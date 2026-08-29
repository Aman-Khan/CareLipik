#!/usr/bin/env python3
"""Generate synthetic two-speaker CareLipik test WAVs with Sarvam Bulbul v3."""

from __future__ import annotations

import argparse
import base64
import io
import json
import os
from pathlib import Path
import urllib.error
import urllib.request
import wave


API_URL = "https://api.sarvam.ai/text-to-speech"
MODEL = "bulbul:v3"
SAMPLE_RATE = 16_000
SILENCE_MILLIS = 450


def synthesize_turn(api_key: str, text: str, language_code: str, speaker: str) -> bytes:
    payload = json.dumps(
        {
            "text": text,
            "language_code": language_code,
            "speaker": speaker,
            "model": MODEL,
            "pace": 0.95,
            "speech_sample_rate": SAMPLE_RATE,
            "output_audio_codec": "wav",
            "temperature": 0.4,
        }
    ).encode("utf-8")
    request = urllib.request.Request(
        API_URL,
        data=payload,
        method="POST",
        headers={
            "api-subscription-key": api_key,
            "Content-Type": "application/json",
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            result = json.load(response)
    except urllib.error.HTTPError as error:
        body = error.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"Sarvam TTS failed with HTTP {error.code}: {body}") from error
    except urllib.error.URLError as error:
        raise RuntimeError(f"Sarvam TTS could not be reached: {error.reason}") from error

    audios = result.get("audios")
    if not isinstance(audios, list) or not audios:
        raise RuntimeError("Sarvam TTS returned no audio.")
    return base64.b64decode(audios[0], validate=True)


def read_compatible_frames(wav_bytes: bytes) -> bytes:
    with wave.open(io.BytesIO(wav_bytes), "rb") as source:
        if source.getnchannels() != 1:
            raise ValueError("Generated audio must be mono.")
        if source.getsampwidth() != 2:
            raise ValueError("Generated audio must be 16-bit PCM.")
        if source.getframerate() != SAMPLE_RATE:
            raise ValueError(f"Generated audio must be {SAMPLE_RATE} Hz.")
        if source.getcomptype() != "NONE":
            raise ValueError("Generated audio must be uncompressed PCM.")
        return source.readframes(source.getnframes())


def combine_turns(turn_audio: list[bytes], output_file: Path) -> None:
    silence_frames = b"\x00\x00" * (SAMPLE_RATE * SILENCE_MILLIS // 1_000)
    output_file.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(output_file), "wb") as output:
        output.setnchannels(1)
        output.setsampwidth(2)
        output.setframerate(SAMPLE_RATE)
        for index, wav_bytes in enumerate(turn_audio):
            if index > 0:
                output.writeframes(silence_frames)
            output.writeframes(read_compatible_frames(wav_bytes))


def generate_case(api_key: str, case: dict, output_directory: Path) -> dict:
    case_id = case["id"]
    print(f"Generating {case_id} ({len(case['turns'])} turns)...")
    audio = [
        synthesize_turn(
            api_key=api_key,
            text=turn["text"],
            language_code=case["language_code"],
            speaker=turn["speaker"],
        )
        for turn in case["turns"]
    ]
    wav_name = f"{case_id}.wav"
    transcript_name = f"{case_id}.json"
    combine_turns(audio, output_directory / wav_name)
    transcript = {
        "id": case_id,
        "title": case["title"],
        "synthetic": True,
        "contains_real_patient_data": False,
        "language_code": case["language_code"],
        "audio_file": wav_name,
        "sample_rate": SAMPLE_RATE,
        "turns": case["turns"],
        "reference_transcript": "\n\n".join(
            f"{turn['role'].title()}: {turn['text']}" for turn in case["turns"]
        ),
    }
    (output_directory / transcript_name).write_text(
        json.dumps(transcript, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    return {
        "id": case_id,
        "audio_file": wav_name,
        "transcript_file": transcript_name,
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--case",
        action="append",
        dest="case_ids",
        help="Generate only this case id. Repeat to select multiple cases.",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=Path(__file__).resolve().parent / "output",
        help="Output directory. Defaults to tools/synthetic_test_audio/output.",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    api_key = os.environ.get("SARVAM_API_KEY", "").strip()
    if not api_key:
        raise SystemExit("SARVAM_API_KEY is missing. Run ./generate.sh instead.")
    cases_file = Path(__file__).resolve().parent / "cases.json"
    cases = json.loads(cases_file.read_text(encoding="utf-8"))
    if args.case_ids:
        selected_ids = set(args.case_ids)
        cases = [case for case in cases if case["id"] in selected_ids]
        missing = selected_ids - {case["id"] for case in cases}
        if missing:
            raise SystemExit(f"Unknown case id(s): {', '.join(sorted(missing))}")
    args.output.mkdir(parents=True, exist_ok=True)
    manifest = [generate_case(api_key, case, args.output) for case in cases]
    (args.output / "manifest.json").write_text(
        json.dumps(manifest, indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"Generated {len(manifest)} synthetic consultation(s) in {args.output}")


if __name__ == "__main__":
    main()
