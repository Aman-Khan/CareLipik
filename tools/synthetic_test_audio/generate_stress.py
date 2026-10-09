#!/usr/bin/env python3
"""Generate offline, privacy-safe stress WAVs for audio quality and diarization tests."""

from __future__ import annotations

from array import array
import json
from pathlib import Path
import random
import subprocess
import tempfile
import wave


SAMPLE_RATE = 16_000
OUTPUT = Path(__file__).resolve().parent / "output" / "stress"
VOICES = {
    "doctor": "Aman",
    "patient": "Rishi",
    "caregiver": "Tara",
    "nurse": "Daniel",
}
TEXT = {
    "doctor_question": "Good morning. Please describe the symptoms and when they started.",
    "patient_answer": "I have had cough, sore throat, tiredness, and fever for four days.",
    "doctor_followup": "Do you have wheezing, shortness of breath, dizziness, or chest pain?",
    "patient_followup": "I have mild wheezing and shortness of breath, but no chest pain.",
    "caregiver_interrupt": "Doctor, the fever was one hundred and one degrees last night.",
    "nurse_interrupt": "The oxygen reading recorded today was ninety seven percent.",
}


def silence(seconds: float) -> array:
    return array("h", [0]) * round(seconds * SAMPLE_RATE)


def load_wave(path: Path) -> array:
    with wave.open(str(path), "rb") as source:
        if (source.getnchannels(), source.getsampwidth(), source.getframerate()) != (
            1,
            2,
            SAMPLE_RATE,
        ):
            raise RuntimeError(f"Unexpected WAV format: {path}")
        return array("h", source.readframes(source.getnframes()))


def synthesize(text: str, voice: str, directory: Path, name: str) -> array:
    aiff = directory / f"{name}.aiff"
    wav = directory / f"{name}.wav"
    subprocess.run(
        ["/usr/bin/say", "-v", voice, "-r", "155", "-o", str(aiff), text],
        check=True,
    )
    subprocess.run(
        [
            "/usr/bin/afconvert",
            "-f",
            "WAVE",
            "-d",
            f"LEI16@{SAMPLE_RATE}",
            "-c",
            "1",
            str(aiff),
            str(wav),
        ],
        check=True,
    )
    frames = load_wave(wav)
    if len(frames) < SAMPLE_RATE // 2:
        raise RuntimeError(
            f"macOS generated no usable speech for voice {voice}. "
            "Run outside a restricted sandbox and verify that the voice is installed."
        )
    return frames


def place(output: array, clip: array, start_seconds: float, gain: float = 1.0) -> None:
    start = round(start_seconds * SAMPLE_RATE)
    required = start + len(clip)
    if len(output) < required:
        output.extend([0] * (required - len(output)))
    for offset, value in enumerate(clip):
        mixed = output[start + offset] + round(value * gain)
        output[start + offset] = max(-32_768, min(32_767, mixed))


def add_noise(audio: array, amplitude: int, seed: int = 7) -> array:
    rng = random.Random(seed)
    noisy = array("h")
    previous = 0.0
    for value in audio:
        # Low-pass-filtered deterministic noise approximates a fan/room background.
        previous = 0.93 * previous + 0.07 * rng.uniform(-amplitude, amplitude)
        noisy.append(max(-32_768, min(32_767, round(value + previous))))
    return noisy


def apply_gain(audio: array, gain: float) -> array:
    return array("h", (max(-32_768, min(32_767, round(value * gain))) for value in audio))


def write_case(case_id: str, audio: array, events: list[dict], expectations: list[str]) -> dict:
    OUTPUT.mkdir(parents=True, exist_ok=True)
    wav_name = f"{case_id}.wav"
    with wave.open(str(OUTPUT / wav_name), "wb") as target:
        target.setnchannels(1)
        target.setsampwidth(2)
        target.setframerate(SAMPLE_RATE)
        target.writeframes(audio.tobytes())
    metadata = {
        "id": case_id,
        "synthetic": True,
        "contains_real_patient_data": False,
        "audio_file": wav_name,
        "sample_rate": SAMPLE_RATE,
        "duration_seconds": round(len(audio) / SAMPLE_RATE, 3),
        "events": events,
        "expectations": expectations,
    }
    (OUTPUT / f"{case_id}.json").write_text(
        json.dumps(metadata, indent=2) + "\n", encoding="utf-8"
    )
    return metadata


def event(role: str, text_key: str, start: float, clip: array) -> dict:
    return {
        "role": role,
        "voice": VOICES[role],
        "text": TEXT[text_key],
        "start_seconds": round(start, 3),
        "end_seconds": round(start + len(clip) / SAMPLE_RATE, 3),
    }


def main() -> None:
    with tempfile.TemporaryDirectory(prefix="carelipik-stress-") as temp:
        directory = Path(temp)
        clips = {
            key: synthesize(text, VOICES[role], directory, key)
            for key, text, role in (
                ("doctor_question", TEXT["doctor_question"], "doctor"),
                ("patient_answer", TEXT["patient_answer"], "patient"),
                ("doctor_followup", TEXT["doctor_followup"], "doctor"),
                ("patient_followup", TEXT["patient_followup"], "patient"),
                ("caregiver_interrupt", TEXT["caregiver_interrupt"], "caregiver"),
                ("nurse_interrupt", TEXT["nurse_interrupt"], "nurse"),
            )
        }

        cases = []

        # Two voices separated by pauses long enough to exercise silence/VAD handling.
        audio = array("h")
        events = []
        cursor = 2.0
        for role, key, pause in (
            ("doctor", "doctor_question", 7.0),
            ("patient", "patient_answer", 10.0),
            ("doctor", "doctor_followup", 6.0),
            ("patient", "patient_followup", 2.0),
        ):
            clip = clips[key]
            place(audio, clip, cursor)
            events.append(event(role, key, cursor, clip))
            cursor += len(clip) / SAMPLE_RATE + pause
        audio.extend(silence(2.0))
        cases.append(write_case("long-pauses-two-speakers", audio, events, [
            "detect 2 voices", "high silence percentage", "preserve 4 speech turns"
        ]))

        # A caregiver interrupts during the patient's answer, producing three voices and overlap.
        audio = array("h")
        dq, pa, ci = clips["doctor_question"], clips["patient_answer"], clips["caregiver_interrupt"]
        place(audio, dq, 0.5)
        patient_start = len(dq) / SAMPLE_RATE + 1.0
        place(audio, pa, patient_start)
        interrupt_start = patient_start + 1.1
        place(audio, ci, interrupt_start)
        events = [
            event("doctor", "doctor_question", 0.5, dq),
            event("patient", "patient_answer", patient_start, pa),
            event("caregiver", "caregiver_interrupt", interrupt_start, ci),
        ]
        cases.append(write_case("three-speakers-overlap", audio, events, [
            "detect approximately 3 voices", "show overlap uncertainty", "do not auto-label overlap confidently"
        ]))

        # Four independent voices with short hand-offs and one brief interruption.
        audio = array("h")
        events = []
        cursor = 0.5
        for role, key in (
            ("doctor", "doctor_question"),
            ("patient", "patient_answer"),
            ("caregiver", "caregiver_interrupt"),
            ("nurse", "nurse_interrupt"),
            ("doctor", "doctor_followup"),
            ("patient", "patient_followup"),
        ):
            clip = clips[key]
            place(audio, clip, cursor)
            events.append(event(role, key, cursor, clip))
            cursor += len(clip) / SAMPLE_RATE + 0.35
        cases.append(write_case("four-speakers-short-turns", audio, events, [
            "detect 3-4 voice clusters", "allow Other roles", "retain short speaker hand-offs"
        ]))

        clean = array("h")
        place(clean, dq, 0.5)
        place(clean, pa, len(dq) / SAMPLE_RATE + 1.0)
        base_events = [event("doctor", "doctor_question", 0.5, dq), event("patient", "patient_answer", len(dq) / SAMPLE_RATE + 1.0, pa)]
        cases.append(write_case("background-fan-noise", add_noise(clean, 11_000), base_events, [
            "warn about background noise", "avoid treating steady noise as a confident person"
        ]))
        cases.append(write_case("clipped-two-speakers", apply_gain(clean, 7.0), base_events, [
            "warn about clipping", "keep manual speaker correction available"
        ]))
        cases.append(write_case("distant-quiet-speakers", add_noise(apply_gain(clean, 0.07), 900), base_events, [
            "warn that speech is quiet or distant", "avoid confident role assignment when embeddings are weak"
        ]))

        mostly_silent = silence(18.0)
        place(mostly_silent, clips["patient_followup"], 9.0, 0.35)
        cases.append(write_case("mostly-silence-short-speech", mostly_silent, [
            event("patient", "patient_followup", 9.0, clips["patient_followup"])
        ], ["warn about mostly silent audio", "warn when active speech is insufficient if applicable"]))

        (OUTPUT / "manifest.json").write_text(
            json.dumps([
                {"id": item["id"], "audio_file": item["audio_file"], "metadata_file": f"{item['id']}.json"}
                for item in cases
            ], indent=2) + "\n",
            encoding="utf-8",
        )
        print(f"Generated {len(cases)} offline stress cases in {OUTPUT}")


if __name__ == "__main__":
    main()
