"""Download LiteRT-LM model bundles from Hugging Face and install them to the connected Android device.
"""
import argparse
import json
import os
import shutil
from pathlib import Path
import subprocess
import sys
import time
import traceback
import urllib.request

MODELS = {
    "gemma4": {
        "url": "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it-gpu.litertlm",
        "local_subpath": ".local-models/gemma4/gemma-4-E2B-it-gpu.litertlm",
        "device_name": "gemma-4-e2b-it-gpu.litertlm",
        "expected_size": 2008432640,
        "gated": False,
    },
    "medgemma": {
        "url": "https://huggingface.co/litert-community/MedGemma-1.5-4B-IT/resolve/main/medgemma-1.5-4b-it_q4_block32_ekv2048.litertlm",
        "local_subpath": ".local-models/medgemma/medgemma-1.5-4b-it_q4_block32_ekv2048.litertlm",
        "device_name": "medgemma-1.5-4b-it-int4.litertlm",
        "expected_size": 2583871056,
        "gated": True,
    }
}

PACKAGE_NAME = "com.carelipik.app"


def resolve_apk(metadata_path):
    """Use AGP's selected variant output, including Android Studio's IDE location."""
    metadata_path = Path(metadata_path)
    metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
    if metadata.get("applicationId") != PACKAGE_NAME or metadata.get("variantName") != "debug":
        raise RuntimeError("APK metadata must describe the CareLipik debug app.")
    elements = metadata.get("elements", [])
    if len(elements) != 1:
        raise RuntimeError("Model setup requires one universal debug APK.")
    apk = metadata_path.parent / elements[0]["outputFile"]
    if not apk.is_file():
        raise RuntimeError(f"Built APK is missing: {apk}. Rebuild the debug app.")
    return apk


def get_adb_device(serial=None):
    sdk = os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME")
    executable = shutil.which("adb")
    if not executable and sdk:
        candidate = Path(sdk) / "platform-tools" / ("adb.exe" if os.name == "nt" else "adb")
        if candidate.is_file():
            executable = str(candidate)
    if not executable:
        raise RuntimeError("ADB unavailable. Configure sdk.dir in local.properties or ANDROID_SDK_ROOT.")
    cmd = [executable]
    if serial:
        cmd.extend(["-s", serial])
    return cmd


def install_debug_apk(apk, serial=None):
    # Android Studio sets android:testOnly; -t is required for its Run APKs.
    subprocess.run(get_adb_device(serial) + ["install", "-r", "-t", str(apk)], check=True)


def download_file(url, local_path, expected_size, hf_token=None, gated=False):
    local_path = Path(local_path)
    local_path.parent.mkdir(parents=True, exist_ok=True)
    if local_path.is_file() and local_path.stat().st_size == expected_size:
        print(f"Model bundle already present at {local_path} ({expected_size} bytes)", flush=True)
        return True

    temp_path = local_path.with_suffix(local_path.suffix + ".part")
    downloaded = temp_path.stat().st_size if temp_path.is_file() else 0

    token = hf_token or os.environ.get("HF_TOKEN") or os.environ.get("HUGGING_FACE_HUB_TOKEN")
    if gated and not token:
        raise RuntimeError("MedGemma requires an HF_TOKEN with model access, or a complete local bundle.")

    print(f"Downloading from {url} to {local_path}...", flush=True)
    req = urllib.request.Request(url)
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    if downloaded > 0:
        req.add_header("Range", f"bytes={downloaded}-")

    start_time = time.time()
    last_print = start_time
    try:
        with urllib.request.urlopen(req, timeout=60) as response:
            resumed = downloaded > 0 and response.status == 206
            if resumed and not response.headers.get("Content-Range", "").startswith(f"bytes {downloaded}-"):
                raise RuntimeError("Server returned an invalid download resume offset.")
            if not resumed:
                downloaded = 0
            mode = "ab" if resumed else "wb"
            total_size = expected_size
            with temp_path.open(mode) as out_file:
                while True:
                    chunk = response.read(1024 * 1024)
                    if not chunk:
                        break
                    out_file.write(chunk)
                    downloaded += len(chunk)
                    now = time.time()
                    if now - last_print >= 3.0:
                        elapsed = now - start_time
                        speed = (downloaded / (1024 * 1024)) / elapsed if elapsed > 0 else 0
                        percent = (downloaded / total_size) * 100 if total_size else 0
                        print(f"Downloaded {downloaded / (1024*1024):.1f} MB / {total_size / (1024*1024):.1f} MB ({percent:.1f}%) at {speed:.2f} MB/s", flush=True)
                        last_print = now
        if temp_path.stat().st_size != expected_size:
            raise RuntimeError("Downloaded model size does not match the expected bundle size.")
        temp_path.replace(local_path)
        print(f"Successfully downloaded {local_path}", flush=True)
        return True
    except Exception as e:
        print(f"Download note for {url}: {e}", flush=True)
        return False


def install_to_device(local_path, device_name, serial=None):
    local_path = Path(local_path)
    if not local_path.is_file():
        raise RuntimeError("Prepare the local model bundle before device installation.")

    adb = get_adb_device(serial)

    # Check if device is available
    res = subprocess.run(adb + ["get-state"], capture_output=True, text=True)
    if res.returncode != 0:
        print("No ADB device found or device not ready.", flush=True)
        return False

    # Check if model is already on device with correct size
    check_cmd = adb + ["shell", "run-as", PACKAGE_NAME, "stat", "-c", "%s", f"files/models/{device_name}"]
    res = subprocess.run(check_cmd, capture_output=True, text=True)
    if res.returncode == 0 and res.stdout.strip() == str(local_path.stat().st_size):
        print(f"Model {device_name} is already verified in {PACKAGE_NAME} app storage on device.", flush=True)
        return True

    print(f"Pushing {local_path.name} to /data/local/tmp/{device_name}...", flush=True)
    res = subprocess.run(adb + ["push", str(local_path), f"/data/local/tmp/{device_name}"])
    if res.returncode != 0:
        print(f"Failed to push {local_path.name} to /data/local/tmp", flush=True)
        return False

    print(f"Installing {device_name} into {PACKAGE_NAME} app-private storage...", flush=True)
    commands = [
        ["shell", "run-as", PACKAGE_NAME, "mkdir", "-p", "files/models"],
        ["shell", "run-as", PACKAGE_NAME, "cp", f"/data/local/tmp/{device_name}", f"files/models/{device_name}.pending"],
        ["shell", "run-as", PACKAGE_NAME, "chmod", "600", f"files/models/{device_name}.pending"],
        ["shell", "run-as", PACKAGE_NAME, "mv", f"files/models/{device_name}.pending", f"files/models/{device_name}"],
        ["shell", "rm", f"/data/local/tmp/{device_name}"]
    ]
    for cmd in commands:
        subprocess.run(adb + cmd, check=True)

    print(f"Successfully installed {device_name} on device!", flush=True)
    return True


def main():
    try:
        parser = argparse.ArgumentParser()
        parser.add_argument("--serial", help="ADB device serial")
        parser.add_argument("--model", choices=["medgemma", "gemma4", "all"], default="gemma4")
        parser.add_argument("--apk", help="Install this debug APK before copying models")
        parser.add_argument("--apk-metadata", help="Resolve the built APK from Android Gradle Plugin metadata")
        parser.add_argument("--hf-token", help="Hugging Face API token for gated models")
        parser.add_argument("--download-only", action="store_true")
        parser.add_argument("--install-only", action="store_true")
        args = parser.parse_args()
        if args.download_only and args.install_only:
            parser.error("Choose either download-only or install-only.")
        apk = resolve_apk(args.apk_metadata) if args.apk_metadata else args.apk
        if apk:
            install_debug_apk(apk, args.serial)

        workspace = Path(__file__).resolve().parent.parent
        targets = ["gemma4", "medgemma"] if args.model == "all" else [args.model]

        for key in targets:
            model_info = MODELS[key]
            local_path = workspace / model_info["local_subpath"]
            if not args.install_only:
                prepared = download_file(
                    model_info["url"],
                    local_path,
                    model_info["expected_size"],
                    hf_token=args.hf_token,
                    gated=model_info.get("gated", False)
                )
                if not prepared:
                    raise RuntimeError(f"Could not prepare {key}; model setup stopped.")
            if not args.download_only:
                if local_path.stat().st_size != model_info["expected_size"]:
                    raise RuntimeError(f"Invalid local bundle for {key}.")
                if not install_to_device(local_path, model_info["device_name"], serial=args.serial):
                    raise RuntimeError(f"Could not install {key}; app launch must wait.")
    except Exception as e:
        print(f"Exception in script: {e}", file=sys.stderr)
        traceback.print_exc()
        sys.exit(1)


if __name__ == "__main__":
    main()
