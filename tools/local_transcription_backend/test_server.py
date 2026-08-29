#!/usr/bin/env python3

import json
import threading
import unittest
import urllib.request
import uuid
from contextlib import contextmanager

from server import (
    TranscriptionConfig,
    create_server,
    normalize_clinical_entities,
    normalize_clinical_note,
    normalize_provider_result,
)


class FakeBatchClient:
    def __init__(self) -> None:
        self.submitted_config = None
        self.submitted_audio_was_wav = False
        self.next_status = {"id": "job_test", "status": "processing"}

    def submit(self, audio_path, config: TranscriptionConfig) -> str:
        self.submitted_config = config
        self.submitted_audio_was_wav = audio_path.read_bytes().startswith(b"RIFF")
        return "job_test"

    def result_status(self, job_id: str):
        if job_id != "job_test":
            raise AssertionError("Unexpected job id")
        return self.next_status


class FakeClinicalClient:
    def __init__(self) -> None:
        self.request = None
        self.note_request = None

    def analyze(self, transcript, language_code):
        self.request = (transcript, language_code)
        return {
            "source": "gemini",
            "codes_verified": False,
            "entities": [
                {
                    "id": "cloud:MEDICATION:7:dolo 650",
                    "source_text": "Dolo 650",
                    "normalized_text": "paracetamol 650 mg",
                    "brand_name": "Dolo",
                    "generic_salt": "paracetamol",
                    "strength": "650 mg",
                    "category": "MEDICATION",
                    "assertion": "PRESENT",
                    "confidence": 0.94,
                    "possible_asr_error": False,
                    "start_index": 7,
                    "end_index_exclusive": 15,
                    "verification_status": "doctor_confirmation_required",
                }
            ],
        }

    def generate_note(self, **request):
        self.note_request = request
        return {
            "note_format": request["note_format"],
            "output_language": request["output_language"],
            "specialty_name": request["specialty_name"],
            "sections": [
                {
                    "id": "subjective",
                    "title": "Subjective",
                    "content": "Patient reports a synthetic cough.",
                    "source_turn_ids": ["T2"],
                }
            ],
            "prescribed_medications": [],
            "coverage_warnings": [],
            "source": "gemini",
            "doctor_approval_required": True,
        }


@contextmanager
def running_server(client):
    server = create_server("127.0.0.1", 0, client)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    try:
        yield f"http://127.0.0.1:{server.server_port}"
    finally:
        server.shutdown()
        server.server_close()
        thread.join(timeout=2)


def multipart_body(fields, audio_bytes):
    boundary = f"CareLipikTest-{uuid.uuid4()}"
    parts = []
    for name, value in fields.items():
        parts.append(f"--{boundary}\r\n".encode())
        parts.append(f'Content-Disposition: form-data; name="{name}"\r\n\r\n'.encode())
        parts.append(str(value).encode())
        parts.append(b"\r\n")
    parts.append(f"--{boundary}\r\n".encode())
    parts.append(
        b'Content-Disposition: form-data; name="file"; filename="consultation.wav"\r\n'
    )
    parts.append(b"Content-Type: audio/wav\r\n\r\n")
    parts.append(audio_bytes)
    parts.append(b"\r\n")
    parts.append(f"--{boundary}--\r\n".encode())
    return boundary, b"".join(parts)


class LocalBackendTest(unittest.TestCase):
    def test_submit_validates_and_forwards_batch_configuration(self):
        client = FakeBatchClient()
        fields = {
            "provider": "sarvam",
            "model": "saaras:v3",
            "language_code": "hi-IN",
            "mode": "codemix",
            "with_diarization": "true",
            "num_speakers": "2",
        }
        boundary, body = multipart_body(fields, b"RIFF\x04\x00\x00\x00WAVEdata")

        with running_server(client) as base_url:
            request = urllib.request.Request(
                f"{base_url}/v1/transcriptions",
                data=body,
                method="POST",
                headers={"Content-Type": f"multipart/form-data; boundary={boundary}"},
            )
            with urllib.request.urlopen(request) as response:
                result = json.load(response)

        self.assertEqual(202, response.status)
        self.assertEqual({"id": "job_test", "status": "queued"}, result)
        self.assertTrue(client.submitted_audio_was_wav)
        self.assertEqual("codemix", client.submitted_config.mode)
        self.assertTrue(client.submitted_config.with_diarization)
        self.assertEqual(2, client.submitted_config.num_speakers)

    def test_poll_returns_provider_neutral_status(self):
        client = FakeBatchClient()
        with running_server(client) as base_url:
            with urllib.request.urlopen(f"{base_url}/v1/transcriptions/job_test") as response:
                result = json.load(response)

        self.assertEqual({"id": "job_test", "status": "processing"}, result)

    def test_provider_result_keeps_speaker_ids_without_assigning_roles(self):
        normalized = normalize_provider_result(
            "job_test",
            {
                "transcript": "Full transcript",
                "language_code": "hi-IN",
                "diarized_transcript": {
                    "entries": [
                        {"speaker_id": "0", "transcript": "नमस्ते"},
                        {"speaker_id": "1", "transcript": "मुझे खांसी है"},
                    ]
                },
            },
        )

        self.assertEqual("completed", normalized["status"])
        self.assertEqual("0", normalized["segments"][0]["speaker_id"])
        self.assertEqual("1", normalized["segments"][1]["speaker_id"])
        self.assertNotIn("doctor", json.dumps(normalized).lower())
        self.assertNotIn("patient", json.dumps(normalized).lower())

    def test_provider_result_sorts_timed_turns_without_merging_them(self):
        normalized = normalize_provider_result(
            "job_test",
            {
                "transcript": "Doctor then patient then doctor",
                "diarized_transcript": {
                    "entries": [
                        {
                            "speaker_id": "1",
                            "transcript": "I have a fever",
                            "start_time_seconds": 2.8,
                            "end_time_seconds": 4.2,
                        },
                        {
                            "speaker_id": "0",
                            "transcript": "Good morning",
                            "start_time_seconds": 0.01,
                            "end_time_seconds": 2.5,
                        },
                        {
                            "speaker_id": "0",
                            "transcript": "How high was it?",
                            "start_time_seconds": 4.5,
                            "end_time_seconds": 5.8,
                        },
                    ]
                },
            },
        )

        self.assertEqual(["0", "1", "0"], [item["speaker_id"] for item in normalized["segments"]])
        self.assertEqual(0.01, normalized["segments"][0]["start_time_seconds"])
        self.assertNotIn("speaker_separation_warning", normalized)

    def test_provider_result_does_not_present_one_voice_as_diarized(self):
        normalized = normalize_provider_result(
            "job_test",
            {
                "transcript": "One continuous block",
                "diarized_transcript": {
                    "entries": [
                        {"speaker_id": "0", "transcript": "One continuous block"},
                    ]
                },
            },
        )

        self.assertEqual([], normalized["segments"])
        self.assertIn("exactly two voices", normalized["speaker_separation_warning"])

    def test_clinical_normalization_rejects_hallucinated_source_text(self):
        normalized = normalize_clinical_entities(
            "Patient takes Dolo 650 and denies chest pain.",
            {
                "entities": [
                    {
                        "source_text": "Dolo 650",
                        "normalized_text": "paracetamol 650 mg",
                        "brand_name": "Dolo",
                        "generic_salt": "paracetamol",
                        "strength": "650 mg",
                        "category": "MEDICATION",
                        "assertion": "PRESENT",
                        "confidence": 1.4,
                        "possible_asr_error": False,
                    },
                    {
                        "source_text": "azithromycin",
                        "normalized_text": "azithromycin",
                        "brand_name": "",
                        "generic_salt": "azithromycin",
                        "strength": "",
                        "category": "MEDICATION",
                        "assertion": "PRESENT",
                        "confidence": 0.9,
                        "possible_asr_error": False,
                    },
                ]
            },
        )

        self.assertEqual(1, len(normalized["entities"]))
        self.assertEqual("Dolo 650", normalized["entities"][0]["source_text"])
        self.assertEqual("paracetamol", normalized["entities"][0]["generic_salt"])
        self.assertEqual(1.0, normalized["entities"][0]["confidence"])
        self.assertFalse(normalized["codes_verified"])

    def test_clinical_endpoint_forwards_only_transcript_and_language(self):
        batch_client = FakeBatchClient()
        clinical_client = FakeClinicalClient()
        body = json.dumps(
            {"transcript": "I take Dolo 650.", "language_code": "en-IN"}
        ).encode("utf-8")

        with running_server_with_clinical(batch_client, clinical_client) as base_url:
            request = urllib.request.Request(
                f"{base_url}/v1/clinical-entities",
                data=body,
                method="POST",
                headers={"Content-Type": "application/json"},
            )
            with urllib.request.urlopen(request) as response:
                result = json.load(response)

        self.assertEqual(200, response.status)
        self.assertEqual(("I take Dolo 650.", "en-IN"), clinical_client.request)
        self.assertEqual("MEDICATION", result["entities"][0]["category"])

    def test_clinical_note_normalization_requires_evidence_and_covers_patient_turns(self):
        transcript = (
            "Doctor: What brought you in?\n\n"
            "Patient: I have a cough.\n\n"
            "Doctor: Take SyntheticMed 5 mg once daily for three days.\n\n"
            "Patient: I also feel dizzy."
        )

        normalized = normalize_clinical_note(
            transcript=transcript,
            result={
                "sections": [
                    {
                        "id": "subjective",
                        "content": "Patient reports cough.",
                        "source_turn_ids": ["T2"],
                    },
                    {
                        "id": "assessment",
                        "content": "Invented diagnosis",
                        "source_turn_ids": ["BAD"],
                    },
                ],
                "prescribed_medications": [
                    {
                        "name": "SyntheticMed",
                        "generic_name": "",
                        "strength": "5 mg",
                        "dose": "5 mg",
                        "route": "",
                        "frequency": "once daily",
                        "duration": "three days",
                        "instructions": "",
                        "source_turn_ids": ["T3"],
                    },
                    {
                        "name": "PatientCurrentMed",
                        "generic_name": "",
                        "strength": "",
                        "dose": "",
                        "route": "",
                        "frequency": "",
                        "duration": "",
                        "instructions": "",
                        "source_turn_ids": ["T2"],
                    },
                ],
            },
            note_format="Soap",
            output_language="English",
            specialty_name="General medicine",
        )

        self.assertEqual("", normalized["sections"][2]["content"])
        self.assertEqual("SyntheticMed", normalized["prescribed_medications"][0]["name"])
        self.assertEqual(1, len(normalized["prescribed_medications"]))
        self.assertFalse(normalized["prescribed_medications"][0]["doctor_reviewed"])
        self.assertEqual(
            ["Patient turn T4 is not represented in the generated note."],
            normalized["coverage_warnings"],
        )

    def test_clinical_note_endpoint_forwards_reviewed_context(self):
        batch_client = FakeBatchClient()
        clinical_client = FakeClinicalClient()
        body = json.dumps(
            {
                "transcript": "Doctor: Question\n\nPatient: Synthetic cough",
                "language_code": "hi-Latn-IN",
                "note_format": "Soap",
                "output_language": "English",
                "specialty_name": "General medicine",
                "patient_age": "47",
                "visit_reason": "Synthetic cough",
            }
        ).encode("utf-8")

        with running_server_with_clinical(batch_client, clinical_client) as base_url:
            request = urllib.request.Request(
                f"{base_url}/v1/clinical-note-drafts",
                data=body,
                method="POST",
                headers={"Content-Type": "application/json"},
            )
            with urllib.request.urlopen(request) as response:
                result = json.load(response)

        self.assertEqual(200, response.status)
        self.assertEqual("hi-Latn-IN", clinical_client.note_request["language_code"])
        self.assertEqual("English", clinical_client.note_request["output_language"])
        self.assertTrue(result["doctor_approval_required"])


@contextmanager
def running_server_with_clinical(batch_client, clinical_client):
    server = create_server("127.0.0.1", 0, batch_client, clinical_client)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    try:
        yield f"http://127.0.0.1:{server.server_port}"
    finally:
        server.shutdown()
        server.server_close()
        thread.join(timeout=2)


if __name__ == "__main__":
    unittest.main()
