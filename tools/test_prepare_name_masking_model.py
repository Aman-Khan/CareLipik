import hashlib
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import prepare_name_masking_model as model


class PrepareNameMaskingModelTest(unittest.TestCase):
    def test_export_retains_normal_piece_scores_only(self):
        import struct
        normal = b'\x0a\x01A\x15' + struct.pack('<f', -2.0) + b'\x18\x01'
        control = b'\x0a\x01B\x15' + struct.pack('<f', -3.0) + b'\x18\x03'
        data = b'\x0a' + bytes([len(normal)]) + normal + b'\x0a' + bytes([len(control)]) + control
        self.assertEqual('A\t-2.0\n', model.export_bpe(data))

    def test_export_blocks_empty_vocabulary(self):
        with self.assertRaises(ValueError):
            model.export_bpe(b'')

    def test_parser_blocks_truncated_model(self):
        with self.assertRaises(ValueError):
            list(model.protobuf_fields(b'\x0a\x05A'))
    def test_cached_content_requires_digest_and_size(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "model"
            path.write_bytes(b"synthetic")
            digest = hashlib.sha256(b"synthetic").hexdigest()
            self.assertTrue(model.verified(path, 9, "sha256", digest))
            self.assertFalse(model.verified(path, 8, "sha256", digest))
            self.assertFalse(model.verified(path, 9, "sha256", "0" * 64))

    def test_git_blob_digest_includes_header(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "tokens"
            path.write_bytes(b"tokens")
            digest = hashlib.sha1(b"blob 6\0tokens").hexdigest()
            self.assertTrue(model.verified(path, 6, "git-sha1", digest))

    def test_download_failure_does_not_replace_cache_and_removes_partial(self):
        import io
        with tempfile.TemporaryDirectory() as directory:
            destination = Path(directory)
            path = destination / "synthetic.onnx"
            path.write_bytes(b"old")
            with patch.object(model, "FILES", {path.name: (3, "sha256", "0" * 64)}), \
                    patch.object(model.urllib.request, "urlopen", return_value=io.BytesIO(b"bad")):
                with self.assertRaises(ValueError):
                    model.prepare(destination, archive_path=destination / "synthetic.tar.bz2")
            self.assertEqual(b"old", path.read_bytes())
            self.assertFalse(path.with_suffix(".onnx.part").exists())


if __name__ == "__main__":
    unittest.main()
