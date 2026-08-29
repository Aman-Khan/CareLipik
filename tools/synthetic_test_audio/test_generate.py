#!/usr/bin/env python3

import io
from pathlib import Path
import tempfile
import unittest
import wave

from generate import SAMPLE_RATE, combine_turns, read_compatible_frames


def test_wav(frame_count: int = 1_600, sample_rate: int = SAMPLE_RATE) -> bytes:
    output = io.BytesIO()
    with wave.open(output, "wb") as wav_file:
        wav_file.setnchannels(1)
        wav_file.setsampwidth(2)
        wav_file.setframerate(sample_rate)
        wav_file.writeframes(b"\x01\x00" * frame_count)
    return output.getvalue()


class SyntheticAudioGeneratorTest(unittest.TestCase):
    def test_read_compatible_frames_accepts_expected_wave_format(self):
        self.assertEqual(3_200, len(read_compatible_frames(test_wav())))

    def test_read_compatible_frames_rejects_wrong_sample_rate(self):
        with self.assertRaisesRegex(ValueError, "16000 Hz"):
            read_compatible_frames(test_wav(sample_rate=24_000))

    def test_combine_turns_writes_compatible_wave(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "combined.wav"
            combine_turns([test_wav(), test_wav()], output)

            with wave.open(str(output), "rb") as combined:
                self.assertEqual(1, combined.getnchannels())
                self.assertEqual(2, combined.getsampwidth())
                self.assertEqual(SAMPLE_RATE, combined.getframerate())
                self.assertGreater(combined.getnframes(), 3_200)


if __name__ == "__main__":
    unittest.main()
