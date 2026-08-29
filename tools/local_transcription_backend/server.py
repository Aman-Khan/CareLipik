#!/usr/bin/env python3
"""Local-only CareLipik proxy for Saaras transcription and clinical term review."""

import cgi
import json
import os
import re
import tempfile
import urllib.error
import urllib.request
from dataclasses import dataclass
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any, Dict, Optional
from urllib.parse import urlparse


SARVAM_API_BASE_URL = "https://api.sarvam.ai"
DEFAULT_HOST = "127.0.0.1"
DEFAULT_PORT = 8787
MAX_UPLOAD_BYTES = 25 * 1024 * 1024
MAX_TRANSCRIPT_BYTES = 128 * 1024
SAFE_JOB_ID = re.compile(r"^[A-Za-z0-9._-]+$")
GEMINI_API_BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
DEFAULT_GEMINI_MODEL = "gemini-2.5-flash"


class RequestError(Exception):
    """The Android client sent an invalid request."""


class ProviderError(Exception):
    """Sarvam rejected a request or returned an invalid response."""


class ClinicalAnalysisError(Exception):
    """The clinical term provider rejected a request or returned invalid output."""


@dataclass(frozen=True)
class TranscriptionConfig:
    model: str
    language_code: str
    mode: str
    with_diarization: bool
    num_speakers: int


class SarvamBatchClient:
    def __init__(self, api_key: str, timeout_seconds: int = 60) -> None:
        if not api_key.strip():
            raise ValueError("SARVAM_API_KEY is required")
        self._api_key = api_key.strip()
        self._timeout_seconds = timeout_seconds

    def submit(self, audio_path: Path, config: TranscriptionConfig) -> str:
        initiated = self._json_request(
            "POST",
            "/speech-to-text/job/v1",
            {
                "job_parameters": {
                    "model": config.model,
                    "language_code": config.language_code,
                    "mode": config.mode,
                    "with_diarization": config.with_diarization,
                    "num_speakers": config.num_speakers,
                }
            },
        )
        job_id = str(initiated.get("job_id", ""))
        if not SAFE_JOB_ID.fullmatch(job_id):
            raise ProviderError("Sarvam returned an invalid job identifier")

        upload_links = self._json_request(
            "POST",
            "/speech-to-text/job/v1/upload-files",
            {"job_id": job_id, "files": [audio_path.name]},
        )
        file_details = upload_links.get("upload_urls", {}).get(audio_path.name, {})
        upload_url = str(file_details.get("file_url", ""))
        if not upload_url.startswith("https://"):
            raise ProviderError("Sarvam did not return a secure upload URL")

        self._upload_audio(upload_url, audio_path)
        self._json_request("POST", f"/speech-to-text/job/v1/{job_id}/start")
        return job_id

    def result_status(self, job_id: str) -> Dict[str, Any]:
        if not SAFE_JOB_ID.fullmatch(job_id):
            raise RequestError("Invalid transcription job identifier")
        status = self._json_request(
            "GET",
            f"/speech-to-text/job/v1/{job_id}/status",
        )
        job_state = str(status.get("job_state", "")).lower()
        if job_state in {"accepted", "pending"}:
            return {"id": job_id, "status": "queued"}
        if job_state == "running":
            return {"id": job_id, "status": "processing"}
        if job_state == "failed":
            return {
                "id": job_id,
                "status": "failed",
                "message": _safe_provider_message(status.get("error_message")),
            }
        if job_state not in {"completed", "partiallycompleted"}:
            raise ProviderError("Sarvam returned an unknown job state")

        output_name = _successful_output_name(status)
        download_links = self._json_request(
            "POST",
            "/speech-to-text/job/v1/download-files",
            {"job_id": job_id, "files": [output_name]},
        )
        output_details = download_links.get("download_urls", {}).get(output_name, {})
        download_url = str(output_details.get("file_url", ""))
        if not download_url.startswith("https://"):
            raise ProviderError("Sarvam did not return a secure result URL")
        provider_result = self._download_json(download_url)
        return normalize_provider_result(job_id, provider_result)

    def _json_request(
        self,
        method: str,
        path: str,
        payload: Optional[Dict[str, Any]] = None,
    ) -> Dict[str, Any]:
        data = None if payload is None else json.dumps(payload).encode("utf-8")
        request = urllib.request.Request(
            f"{SARVAM_API_BASE_URL}{path}",
            data=data,
            method=method,
            headers={
                "Accept": "application/json",
                "api-subscription-key": self._api_key,
                **({"Content-Type": "application/json"} if payload is not None else {}),
            },
        )
        return self._open_json(request)

    def _upload_audio(self, upload_url: str, audio_path: Path) -> None:
        request = urllib.request.Request(
            upload_url,
            data=audio_path.read_bytes(),
            method="PUT",
            headers={
                "Content-Type": "audio/wav",
                "x-ms-blob-type": "BlockBlob",
            },
        )
        try:
            with urllib.request.urlopen(request, timeout=self._timeout_seconds) as response:
                if response.status not in range(200, 300):
                    raise ProviderError("Sarvam audio upload failed")
        except urllib.error.HTTPError as error:
            raise ProviderError("Sarvam audio upload failed") from error
        except urllib.error.URLError as error:
            raise ProviderError("Could not connect to Sarvam for audio upload") from error

    def _download_json(self, url: str) -> Dict[str, Any]:
        request = urllib.request.Request(url, method="GET", headers={"Accept": "application/json"})
        return self._open_json(request, include_api_key=False)

    def _open_json(
        self,
        request: urllib.request.Request,
        include_api_key: bool = True,
    ) -> Dict[str, Any]:
        if include_api_key and not request.has_header("api-subscription-key"):
            request.add_header("api-subscription-key", self._api_key)
        try:
            with urllib.request.urlopen(request, timeout=self._timeout_seconds) as response:
                body = response.read(MAX_UPLOAD_BYTES)
        except urllib.error.HTTPError as error:
            message = _provider_http_error(error)
            raise ProviderError(message) from error
        except urllib.error.URLError as error:
            raise ProviderError("Could not connect to Sarvam") from error
        try:
            parsed = json.loads(body.decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError) as error:
            raise ProviderError("Sarvam returned an unreadable response") from error
        if not isinstance(parsed, dict):
            raise ProviderError("Sarvam returned an invalid response")
        return parsed


class GeminiClinicalEntityClient:
    def __init__(
        self,
        api_key: str,
        model: str = DEFAULT_GEMINI_MODEL,
        timeout_seconds: int = 60,
    ) -> None:
        if not api_key.strip():
            raise ValueError("GEMINI_API_KEY is required")
        if not re.fullmatch(r"[A-Za-z0-9._-]+", model):
            raise ValueError("Invalid Gemini model name")
        self._api_key = api_key.strip()
        self._model = model
        self._timeout_seconds = timeout_seconds

    def analyze(self, transcript: str, language_code: str) -> Dict[str, Any]:
        prompt = (
            "Extract only medical information explicitly written in the consultation transcript. "
            "The text may be English, Hindi, or Hinglish. Preserve source_text exactly as it "
            "appears in the transcript. Do not diagnose, prescribe, invent missing details, or "
            "invent terminology codes. Include symptoms, diseases, medicine brands, generic "
            "salts, allergies, investigations, procedures, anatomy, dosage, strength, route, and "
            "frequency. For each medication mention, separately return the visible brand name, "
            "generic active ingredient or salt, and strength when confidently known. A widely "
            "known brand-to-salt mapping may be returned as an unverified candidate from general "
            "knowledge; do not leave it empty solely because a terminology database is unavailable. "
            "Use an empty string when genuinely uncertain and never fabricate a mapping. Mark "
            "negated, past, family-history, "
            "and uncertain mentions. If a source "
            "word looks like an ASR error, normalized_text may contain a conservative correction "
            "and possible_asr_error may be true; otherwise keep it false. Return each distinct "
            "mention.\n\n"
            f"Language hint: {language_code}\n"
            f"Transcript:\n{transcript}"
        )
        payload = {
            "contents": [{"role": "user", "parts": [{"text": prompt}]}],
            "generationConfig": {
                "temperature": 0,
                "responseMimeType": "application/json",
                "responseSchema": {
                    "type": "OBJECT",
                    "properties": {
                        "entities": {
                            "type": "ARRAY",
                            "items": {
                                "type": "OBJECT",
                                "properties": {
                                    "source_text": {"type": "STRING"},
                                    "normalized_text": {"type": "STRING"},
                                    "brand_name": {"type": "STRING"},
                                    "generic_salt": {"type": "STRING"},
                                    "strength": {"type": "STRING"},
                                    "category": {
                                        "type": "STRING",
                                        "enum": [
                                            "SYMPTOM", "DISEASE", "MEDICATION", "SALT",
                                            "ALLERGY", "INVESTIGATION", "PROCEDURE", "ANATOMY",
                                            "DOSAGE", "STRENGTH", "ROUTE", "FREQUENCY",
                                        ],
                                    },
                                    "assertion": {
                                        "type": "STRING",
                                        "enum": [
                                            "PRESENT", "NEGATED", "PAST", "FAMILY_HISTORY",
                                            "POSSIBLE",
                                        ],
                                    },
                                    "confidence": {"type": "NUMBER"},
                                    "possible_asr_error": {"type": "BOOLEAN"},
                                },
                                "required": [
                                    "source_text", "normalized_text", "brand_name",
                                    "generic_salt", "strength", "category", "assertion",
                                    "confidence", "possible_asr_error",
                                ],
                            },
                        }
                    },
                    "required": ["entities"],
                },
            },
        }
        request = urllib.request.Request(
            f"{GEMINI_API_BASE_URL}/models/{self._model}:generateContent",
            data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
            method="POST",
            headers={
                "Accept": "application/json",
                "Content-Type": "application/json; charset=utf-8",
                "x-goog-api-key": self._api_key,
            },
        )
        try:
            with urllib.request.urlopen(request, timeout=self._timeout_seconds) as response:
                body = response.read(MAX_TRANSCRIPT_BYTES)
        except urllib.error.HTTPError as error:
            raise ClinicalAnalysisError(_clinical_provider_http_error(error)) from error
        except urllib.error.URLError as error:
            raise ClinicalAnalysisError("Could not connect to clinical term analysis") from error
        try:
            provider_response = json.loads(body.decode("utf-8"))
            response_text = provider_response["candidates"][0]["content"]["parts"][0]["text"]
            parsed = json.loads(response_text)
        except (KeyError, IndexError, TypeError, UnicodeDecodeError, json.JSONDecodeError) as error:
            raise ClinicalAnalysisError("Clinical term analysis returned an invalid response") from error
        return normalize_clinical_entities(transcript, parsed)


def normalize_clinical_entities(transcript: str, result: Dict[str, Any]) -> Dict[str, Any]:
    raw_entities = result.get("entities", [])
    if not isinstance(raw_entities, list):
        raise ClinicalAnalysisError("Clinical term analysis returned invalid entities")
    entities = []
    occupied_ranges = []
    allowed_categories = {
        "SYMPTOM", "DISEASE", "MEDICATION", "SALT", "ALLERGY", "INVESTIGATION",
        "PROCEDURE", "ANATOMY", "DOSAGE", "STRENGTH", "ROUTE", "FREQUENCY",
    }
    allowed_assertions = {"PRESENT", "NEGATED", "PAST", "FAMILY_HISTORY", "POSSIBLE"}
    candidate_entities = sorted(
        (entity for entity in raw_entities if isinstance(entity, dict)),
        key=lambda entity: len(str(entity.get("source_text", "")).strip()),
        reverse=True,
    )
    for raw_entity in candidate_entities:
        source_text = str(raw_entity.get("source_text", "")).strip()
        normalized_text = str(raw_entity.get("normalized_text", "")).strip()
        brand_name = str(raw_entity.get("brand_name", "")).strip()
        generic_salt = str(raw_entity.get("generic_salt", "")).strip()
        strength = str(raw_entity.get("strength", "")).strip()
        category = str(raw_entity.get("category", "")).upper()
        assertion = str(raw_entity.get("assertion", "")).upper()
        if not source_text or category not in allowed_categories or assertion not in allowed_assertions:
            continue
        if category not in {"MEDICATION", "SALT"}:
            brand_name = ""
            generic_salt = ""
            strength = ""
        match = _find_unoccupied_source(transcript, source_text, occupied_ranges)
        if match is None:
            continue
        start_index, end_index = match
        occupied_ranges.append(match)
        confidence = _bounded_confidence(raw_entity.get("confidence"))
        possible_asr_error = raw_entity.get("possible_asr_error") is True
        entities.append(
            {
                "id": f"cloud:{category}:{start_index}:{source_text.casefold()}",
                "source_text": transcript[start_index:end_index],
                "normalized_text": normalized_text or source_text,
                "brand_name": brand_name,
                "generic_salt": generic_salt,
                "strength": strength,
                "category": category,
                "assertion": assertion,
                "confidence": confidence,
                "possible_asr_error": possible_asr_error,
                "start_index": start_index,
                "end_index_exclusive": end_index,
                "verification_status": "doctor_confirmation_required",
            }
        )
    entities.sort(key=lambda entity: (entity["start_index"], -entity["end_index_exclusive"]))
    return {"entities": entities, "source": "gemini", "codes_verified": False}


def _find_unoccupied_source(
    transcript: str,
    source_text: str,
    occupied_ranges: Any,
) -> Optional[Any]:
    for match in re.finditer(re.escape(source_text), transcript, re.IGNORECASE):
        candidate = match.span()
        if all(candidate[0] >= end or candidate[1] <= begin for begin, end in occupied_ranges):
            return candidate
    return None


def _bounded_confidence(value: Any) -> float:
    try:
        number = float(value)
    except (TypeError, ValueError):
        return 0.0
    return min(1.0, max(0.0, number))


def _clinical_provider_http_error(error: urllib.error.HTTPError) -> str:
    if error.code in {401, 403}:
        return "Clinical term analysis authentication failed"
    if error.code == 429:
        return "Clinical term analysis rate limit was reached"
    return f"Clinical term analysis failed with HTTP {error.code}"


def normalize_provider_result(job_id: str, result: Dict[str, Any]) -> Dict[str, Any]:
    diarized = result.get("diarized_transcript")
    raw_entries = diarized.get("entries", []) if isinstance(diarized, dict) else []
    segments = []
    if isinstance(raw_entries, list):
        for entry in raw_entries:
            if not isinstance(entry, dict):
                continue
            transcript = str(entry.get("transcript", "")).strip()
            speaker_id = str(entry.get("speaker_id", "")).strip()
            start_time = _optional_non_negative_number(entry.get("start_time_seconds"))
            end_time = _optional_non_negative_number(entry.get("end_time_seconds"))
            has_valid_times = (
                start_time is None or end_time is None or end_time > start_time
            )
            if transcript and speaker_id and has_valid_times:
                segments.append(
                    {
                        "speaker_id": speaker_id,
                        "transcript": transcript,
                        "start_time_seconds": start_time,
                        "end_time_seconds": end_time,
                    }
                )
    segments.sort(
        key=lambda segment: (
            segment["start_time_seconds"] is None,
            segment["start_time_seconds"] or 0.0,
        )
    )
    speaker_ids = {segment["speaker_id"] for segment in segments}
    warning = None
    if len(speaker_ids) != 2:
        warning = (
            "Saaras could not reliably separate exactly two voices. "
            "Review the full transcript and do not assume its speaker order."
        )
        segments = []
    response = {
        "id": job_id,
        "status": "completed",
        "transcript": str(result.get("transcript", "")).strip(),
        "segments": segments,
        "language_code": result.get("language_code"),
    }
    if warning is not None:
        response["speaker_separation_warning"] = warning
    return response


def _optional_non_negative_number(value: Any) -> Optional[float]:
    if value is None:
        return None
    if isinstance(value, bool):
        return None
    try:
        number = float(value)
    except (TypeError, ValueError):
        return None
    return number if number >= 0 else None


def _successful_output_name(status: Dict[str, Any]) -> str:
    details = status.get("job_details", [])
    if isinstance(details, list):
        for detail in details:
            if not isinstance(detail, dict) or str(detail.get("state", "")).lower() != "success":
                continue
            outputs = detail.get("outputs", [])
            if isinstance(outputs, list) and outputs and isinstance(outputs[0], dict):
                file_name = str(outputs[0].get("file_name", ""))
                if file_name:
                    return file_name
    raise ProviderError("Sarvam completed without a transcription output")


def _safe_provider_message(value: Any) -> str:
    message = str(value or "").strip()
    return message if message else "Sarvam could not process this recording"


def _provider_http_error(error: urllib.error.HTTPError) -> str:
    try:
        body = error.read(64 * 1024)
        parsed = json.loads(body.decode("utf-8"))
        if isinstance(parsed, dict):
            detail = parsed.get("message") or parsed.get("detail")
            if detail:
                return f"Sarvam request failed: {detail}"
    except (UnicodeDecodeError, json.JSONDecodeError):
        pass
    return f"Sarvam request failed with HTTP {error.code}"


def validate_config(form: cgi.FieldStorage) -> TranscriptionConfig:
    provider = str(form.getfirst("provider", ""))
    model = str(form.getfirst("model", ""))
    language_code = str(form.getfirst("language_code", ""))
    mode = str(form.getfirst("mode", ""))
    with_diarization = str(form.getfirst("with_diarization", "")).lower() == "true"
    try:
        num_speakers = int(str(form.getfirst("num_speakers", "0")))
    except ValueError as error:
        raise RequestError("num_speakers must be a number") from error

    if provider != "sarvam":
        raise RequestError("Unsupported transcription provider")
    if model != "saaras:v3":
        raise RequestError("Unsupported transcription model")
    if language_code not in {"en-IN", "hi-IN"}:
        raise RequestError("Only en-IN and hi-IN are enabled for this local test")
    if mode not in {"transcribe", "codemix"}:
        raise RequestError("Unsupported transcription mode")
    if not with_diarization or num_speakers != 2:
        raise RequestError("This test requires two-speaker diarization")
    return TranscriptionConfig(
        model=model,
        language_code=language_code,
        mode=mode,
        with_diarization=with_diarization,
        num_speakers=num_speakers,
    )


def handler_for(client: Any, clinical_client: Optional[Any] = None):
    class CareLipikRequestHandler(BaseHTTPRequestHandler):
        server_version = "CareLipikLocalTranscription/1.0"

        def do_GET(self) -> None:
            parsed_path = urlparse(self.path)
            if parsed_path.path == "/health":
                self._send_json(
                    200,
                    {
                        "status": "ok",
                        "clinical_entity_extraction": clinical_client is not None,
                    },
                )
                return
            prefix = "/v1/transcriptions/"
            if not parsed_path.path.startswith(prefix):
                self._send_json(404, {"message": "Not found"})
                return
            job_id = parsed_path.path[len(prefix):]
            try:
                result = client.result_status(job_id)
                self._send_json(200, result)
            except RequestError as error:
                self._send_json(400, {"message": str(error)})
            except ProviderError as error:
                self._send_json(502, {"message": str(error)})
            except Exception:
                self._send_json(500, {"message": "Local transcription backend failed"})

        def do_POST(self) -> None:
            parsed_path = urlparse(self.path).path
            if parsed_path == "/v1/clinical-entities":
                self._handle_clinical_entities()
                return
            if parsed_path != "/v1/transcriptions":
                self._send_json(404, {"message": "Not found"})
                return
            temporary_path: Optional[Path] = None
            try:
                content_length = int(self.headers.get("Content-Length", "0"))
                if content_length <= 0 or content_length > MAX_UPLOAD_BYTES:
                    raise RequestError("Recording is empty or too large for the local test")
                content_type = self.headers.get("Content-Type", "")
                if not content_type.startswith("multipart/form-data"):
                    raise RequestError("Expected multipart form data")
                form = cgi.FieldStorage(
                    fp=self.rfile,
                    headers=self.headers,
                    environ={
                        "REQUEST_METHOD": "POST",
                        "CONTENT_TYPE": content_type,
                        "CONTENT_LENGTH": str(content_length),
                    },
                    keep_blank_values=True,
                )
                config = validate_config(form)
                if "file" not in form:
                    raise RequestError("Recording file is required")
                file_field = form["file"]
                if isinstance(file_field, list) or file_field.file is None:
                    raise RequestError("Exactly one recording file is required")
                with tempfile.NamedTemporaryFile(
                    prefix="carelipik-",
                    suffix=".wav",
                    delete=False,
                ) as temporary_file:
                    temporary_path = Path(temporary_file.name)
                    while True:
                        chunk = file_field.file.read(64 * 1024)
                        if not chunk:
                            break
                        temporary_file.write(chunk)
                if temporary_path.stat().st_size == 0:
                    raise RequestError("Recording file is empty")
                with temporary_path.open("rb") as audio:
                    if audio.read(4) != b"RIFF" or audio.read(4) == b"":
                        raise RequestError("Recording must be a WAV file")
                    audio.seek(8)
                    if audio.read(4) != b"WAVE":
                        raise RequestError("Recording must be a WAV file")
                job_id = client.submit(temporary_path, config)
                self._send_json(202, {"id": job_id, "status": "queued"})
            except RequestError as error:
                self._send_json(400, {"message": str(error)})
            except ProviderError as error:
                self._send_json(502, {"message": str(error)})
            except Exception:
                self._send_json(500, {"message": "Local transcription backend failed"})
            finally:
                if temporary_path is not None:
                    temporary_path.unlink(missing_ok=True)

        def _handle_clinical_entities(self) -> None:
            if clinical_client is None:
                self._send_json(
                    503,
                    {"message": "Online clinical term analysis is not configured"},
                )
                return
            try:
                content_length = int(self.headers.get("Content-Length", "0"))
                if content_length <= 0 or content_length > MAX_TRANSCRIPT_BYTES:
                    raise RequestError("Transcript is empty or too large")
                if not self.headers.get("Content-Type", "").startswith("application/json"):
                    raise RequestError("Expected JSON clinical analysis request")
                payload = json.loads(self.rfile.read(content_length).decode("utf-8"))
                if not isinstance(payload, dict):
                    raise RequestError("Invalid clinical analysis request")
                transcript = str(payload.get("transcript", "")).strip()
                language_code = str(payload.get("language_code", "")).strip()
                if not transcript:
                    raise RequestError("Transcript is required")
                if language_code not in {"en-IN", "hi-IN", "hi-Latn-IN"}:
                    raise RequestError("Unsupported clinical analysis language")
                result = clinical_client.analyze(transcript, language_code)
                self._send_json(200, result)
            except RequestError as error:
                self._send_json(400, {"message": str(error)})
            except (UnicodeDecodeError, json.JSONDecodeError):
                self._send_json(400, {"message": "Invalid clinical analysis JSON"})
            except ClinicalAnalysisError as error:
                self._send_json(502, {"message": str(error)})
            except Exception:
                self._send_json(500, {"message": "Clinical term analysis failed"})

        def _send_json(self, status: int, payload: Dict[str, Any]) -> None:
            body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
            self.send_response(status)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            self.wfile.write(body)

        def log_message(self, format_string: str, *args: Any) -> None:
            print(f"CareLipik local backend: {format_string % args}")

    return CareLipikRequestHandler


def create_server(
    host: str,
    port: int,
    client: Any,
    clinical_client: Optional[Any] = None,
) -> ThreadingHTTPServer:
    return ThreadingHTTPServer((host, port), handler_for(client, clinical_client))


def main() -> None:
    api_key = os.environ.get("SARVAM_API_KEY", "")
    if not api_key:
        raise SystemExit(
            "SARVAM_API_KEY is unavailable. Run store_sarvam_key.sh, then use run.sh."
        )
    gemini_api_key = os.environ.get("GEMINI_API_KEY", "")
    gemini_model = os.environ.get("GEMINI_MODEL", DEFAULT_GEMINI_MODEL)
    clinical_client = (
        GeminiClinicalEntityClient(gemini_api_key, gemini_model)
        if gemini_api_key
        else None
    )
    server = create_server(
        DEFAULT_HOST,
        DEFAULT_PORT,
        SarvamBatchClient(api_key),
        clinical_client,
    )
    print(f"CareLipik local transcription backend listening on http://{DEFAULT_HOST}:{DEFAULT_PORT}")
    print("Only synthetic test recordings should be used.")
    if clinical_client is None:
        print("Gemini clinical term analysis is not configured; offline review remains available.")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
