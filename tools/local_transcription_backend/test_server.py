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


if __name__ == "__main__":
    unittest.main()
