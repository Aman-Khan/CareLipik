"""Build the pinned Sherpa Android runtime with Whisper confidence metadata.

Build tooling only: no model downloads, inference, tests, or evaluation.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import urllib.request
import zipfile

COMMIT = "1cb484af5e69d3c7803c1eb0b3b5ab8041e0e911"
SOURCE_HASH = "eb447178f0b9520493ee879c967cb4f57d77d98d283a410e8d9554b14d1fb2d5"
NDK_VERSION = "27.2.12479018"
CMAKE_VERSION = "3.22.1"
ABIS = ("arm64-v8a", "armeabi-v7a", "x86", "x86_64")


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def download(url, target, expected, offline):
    if target.is_file() and sha256(target) == expected:
        return
    if offline:
        raise RuntimeError(f"Offline native build input missing or invalid: {target}")
    print(f"Downloading native build input: {target.name}", flush=True)
    target.parent.mkdir(parents=True, exist_ok=True)
    pending = target.with_suffix(target.suffix + ".pending")
    with urllib.request.urlopen(url, timeout=120) as response, pending.open("wb") as output:
        while chunk := response.read(1024 * 1024):
            output.write(chunk)
    if sha256(pending) != expected:
        raise RuntimeError(f"Hash mismatch for {target.name}")
    pending.replace(target)


def run_logged(command, log, **kwargs):
    with log.open("w", encoding="utf-8") as output:
        result = subprocess.run(command, stdout=output, stderr=subprocess.STDOUT, **kwargs)
    if result.returncode:
        print("\n".join(log.read_text(encoding="utf-8", errors="replace").splitlines()[-70:]))
        raise RuntimeError(f"Native build failed; see {log}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--original-aar", required=True, type=Path)
    parser.add_argument("--sdk", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--cache", required=True, type=Path)
    parser.add_argument("--offline", action="store_true")
    args = parser.parse_args()
    tooling = Path(__file__).resolve().parent
    workspace = tooling.parent.parent
    cache = args.cache.resolve()
    cache.mkdir(parents=True, exist_ok=True)
    patch = tooling / "whisper-confidence.patch"
    manifest = tooling / "onnx-headers.json"
    identity = hashlib.sha256((sha256(args.original_aar) + sha256(patch) + sha256(manifest) +
        sha256(Path(__file__)) + NDK_VERSION + CMAKE_VERSION).encode()).hexdigest()
    stamp = args.output.with_suffix(".sha256")
    if args.output.is_file() and stamp.is_file() and stamp.read_text().strip() == identity:
        print("Whisper confidence runtime already built.", flush=True)
        return
    ndk = args.sdk / "ndk" / NDK_VERSION
    suffix = ".exe" if os.name == "nt" else ""
    cmake = args.sdk / "cmake" / CMAKE_VERSION / "bin" / ("cmake" + suffix)
    ninja = cmake.parent / ("ninja" + suffix)
    if not cmake.is_file() or not (ndk / "build/cmake/android.toolchain.cmake").is_file():
        raise RuntimeError(f"Install Android SDK packages ndk;{NDK_VERSION} and cmake;{CMAKE_VERSION} first.")
    archive = cache / "source.zip"
    download(f"https://codeload.github.com/k2-fsa/sherpa-onnx/zip/{COMMIT}", archive, SOURCE_HASH, args.offline)
    source_parent = cache / "src"
    source = source_parent / "s"
    staging = source_parent / "p"
    patched = source_parent / "patched"
    if not patched.is_file() or patched.read_text().strip() != sha256(patch):
        source_parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(archive) as bundle:
            for entry in bundle.infolist():
                relative_name = Path(*Path(entry.filename).parts[1:])
                if not relative_name.parts or relative_name.parts[0] not in ("CMakeLists.txt", "cmake", "sherpa-onnx", "LICENSE"):
                    continue
                target = (staging / relative_name).resolve()
                if not target.is_relative_to(staging.resolve()):
                    raise RuntimeError("Source archive contains an invalid path")
                if entry.is_dir():
                    target.mkdir(parents=True, exist_ok=True)
                else:
                    target.parent.mkdir(parents=True, exist_ok=True)
                    data = bundle.read(entry)
                    if not target.is_file() or target.read_bytes() != data:
                        target.write_bytes(data)
        relative = staging.relative_to(workspace).as_posix()
        subprocess.run(["git", "-c", "safe.directory=" + workspace.as_posix(), "apply",
            "--directory=" + relative, str(patch)], cwd=workspace, check=True)
        # Compare final patched bytes before touching compiler inputs. A patch
        # change in one header should not invalidate every native object file.
        for prepared in staging.rglob("*"):
            if prepared.is_file():
                target = source / prepared.relative_to(staging)
                target.parent.mkdir(parents=True, exist_ok=True)
                data = prepared.read_bytes()
                if not target.is_file() or target.read_bytes() != data:
                    target.write_bytes(data)
        patched.write_text(sha256(patch))
    headers = cache / "headers"
    for name, entry in json.loads(manifest.read_text()).items():
        download(entry["url"], headers / name, entry["sha256"], args.offline)
    original = cache / "original-native"
    with zipfile.ZipFile(args.original_aar) as bundle:
        for abi in ABIS:
            name = f"jni/{abi}/libonnxruntime.so"
            target = original / name
            target.parent.mkdir(parents=True, exist_ok=True)
            data = bundle.read(name)
            if not target.is_file() or target.read_bytes() != data:
                target.write_bytes(data)
    libraries = {}
    for abi in ABIS:
        build = cache / "b" / abi
        build.mkdir(parents=True, exist_ok=True)
        environment = os.environ.copy()
        environment["SHERPA_ONNXRUNTIME_INCLUDE_DIR"] = headers.as_posix()
        environment["SHERPA_ONNXRUNTIME_LIB_DIR"] = (original / "jni" / abi).as_posix()
        flags = {
            "CMAKE_TOOLCHAIN_FILE": (ndk / "build/cmake/android.toolchain.cmake").as_posix(),
            "CMAKE_MAKE_PROGRAM": ninja.as_posix(),
            "CMAKE_BUILD_TYPE": "Release", "ANDROID_ABI": abi, "ANDROID_PLATFORM": "android-29",
            "CMAKE_OBJECT_PATH_MAX": "128",
            "BUILD_SHARED_LIBS": "OFF", "SHERPA_ONNX_ENABLE_JNI": "ON",
            "SHERPA_ONNX_ENABLE_SPEAKER_DIARIZATION": "ON", "SHERPA_ONNX_ENABLE_TTS": "OFF",
            "SHERPA_ONNX_ENABLE_BINARY": "OFF", "SHERPA_ONNX_ENABLE_C_API": "OFF",
            "SHERPA_ONNX_ENABLE_PYTHON": "OFF", "SHERPA_ONNX_ENABLE_TESTS": "OFF",
            "SHERPA_ONNX_ENABLE_CHECK": "OFF", "SHERPA_ONNX_ENABLE_PORTAUDIO": "OFF",
            "SHERPA_ONNX_ENABLE_WEBSOCKET": "OFF", "SHERPA_ONNX_BUILD_C_API_EXAMPLES": "OFF",
            "SHERPA_ONNX_LINK_LIBSTDCPP_STATICALLY": "OFF",
        }
        flags["FETCHCONTENT_FULLY_DISCONNECTED"] = "ON" if args.offline else "OFF"
        print(f"Configuring Sherpa confidence runtime: {abi}", flush=True)
        run_logged([str(cmake), "-S", str(source), "-B", str(build), "-G", "Ninja"] +
            [f"-D{key}={value}" for key, value in flags.items()], build / "configure.log", env=environment)
        print(f"Compiling Sherpa confidence runtime: {abi}", flush=True)
        run_logged([str(cmake), "--build", str(build), "--target", "sherpa-onnx-jni", "--parallel", "4"],
            build / "compile.log", env=environment)
        library = build / "lib/libsherpa-onnx-jni.so"
        host = "windows-x86_64" if os.name == "nt" else "darwin-x86_64" if os.uname().sysname == "Darwin" else "linux-x86_64"
        strip = ndk / "toolchains/llvm/prebuilt" / host / "bin" / ("llvm-strip" + suffix)
        subprocess.run([str(strip), "--strip-unneeded", str(library)], check=True)
        libraries[f"jni/{abi}/libsherpa-onnx-jni.so"] = library
    args.output.parent.mkdir(parents=True, exist_ok=True)
    pending = args.output.with_suffix(".pending.aar")
    with zipfile.ZipFile(args.original_aar) as bundle, zipfile.ZipFile(pending, "w", zipfile.ZIP_DEFLATED) as output:
        for entry in bundle.infolist():
            output.writestr(entry, libraries[entry.filename].read_bytes() if entry.filename in libraries else bundle.read(entry))
    pending.replace(args.output)
    stamp.write_text(identity + "\n")
    print(f"Built confidence runtime for all four original Android ABIs: {args.output}", flush=True)


if __name__ == "__main__":
    main()
