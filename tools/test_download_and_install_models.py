"""Model setup tests use temporary files and mocked network/device calls."""
import io
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import download_and_install_models as installer


class Response(io.BytesIO):
    def __init__(self, data, status=200, headers=None):
        super().__init__(data)
        self.status = status
        self.headers = headers or {}


class ModelSetupTest(unittest.TestCase):
    def test_debug_install_accepts_android_studio_test_only_apk(self):
        with patch.object(installer, "get_adb_device", return_value=["adb", "-s", "synthetic-device"]), patch.object(installer.subprocess, "run") as run:
            installer.install_debug_apk("ide-debug.apk", "synthetic-device")
            run.assert_called_once_with(["adb", "-s", "synthetic-device", "install", "-r", "-t", "ide-debug.apk"], check=True)

    def test_apk_is_resolved_from_ide_variant_metadata(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "intermediates" / "apk" / "debug"
            output.mkdir(parents=True)
            apk = output / "custom-debug.apk"
            apk.touch()
            metadata = output / "output-metadata.json"
            metadata.write_text(json.dumps({
                "applicationId": installer.PACKAGE_NAME, "variantName": "debug",
                "elements": [{"outputFile": apk.name}]
            }), encoding="utf-8")
            self.assertEqual(installer.resolve_apk(metadata), apk)

    def test_missing_apk_is_reported_before_adb_install(self):
        with tempfile.TemporaryDirectory() as directory:
            metadata = Path(directory) / "output-metadata.json"
            metadata.write_text(json.dumps({
                "applicationId": installer.PACKAGE_NAME, "variantName": "debug",
                "elements": [{"outputFile": "missing.apk"}]
            }), encoding="utf-8")
            with self.assertRaisesRegex(RuntimeError, "Built APK is missing"):
                installer.resolve_apk(metadata)

    def test_download_restarts_when_server_ignores_range(self):
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / "model.litertlm"
            target.with_suffix(".litertlm.part").write_bytes(b"old")
            with patch.object(installer.urllib.request, "urlopen", return_value=Response(b"complete")):
                self.assertTrue(installer.download_file("https://example.invalid/model", target, 8))
            self.assertEqual(target.read_bytes(), b"complete")

    def test_incomplete_download_is_not_promoted(self):
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / "model.litertlm"
            with patch.object(installer.urllib.request, "urlopen", return_value=Response(b"short")):
                self.assertFalse(installer.download_file("https://example.invalid/model", target, 8))
            self.assertFalse(target.exists())

    def test_complete_cached_model_does_not_need_network_or_token(self):
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / "model.litertlm"
            target.write_bytes(b"cached")
            with patch.object(installer.urllib.request, "urlopen") as network:
                self.assertTrue(installer.download_file("https://example.invalid/model", target, 6, gated=True))
                network.assert_not_called()

    def test_adb_can_be_resolved_from_sdk_without_path(self):
        with tempfile.TemporaryDirectory() as directory:
            executable = Path(directory) / "platform-tools" / ("adb.exe" if os.name == "nt" else "adb")
            executable.parent.mkdir()
            executable.touch()
            with patch.dict(os.environ, {"ANDROID_SDK_ROOT": directory}, clear=True), patch.object(installer.shutil, "which", return_value=None):
                self.assertEqual(installer.get_adb_device("synthetic-device"), [str(executable), "-s", "synthetic-device"])


if __name__ == "__main__":
    unittest.main()
