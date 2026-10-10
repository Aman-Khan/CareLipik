"""Build the isolated Android Whisper Vulkan library, not an evaluation tool."""
import argparse
import hashlib
import os
from pathlib import Path
import subprocess
import urllib.request
import zipfile

SOURCE_ARCHIVES = [
    ("ggml-org/whisper.cpp", "v1.9.5", "whisper.cpp-1.9.5", "7a7a73071939f16bc028ad2f713f16f26e37afb6cc155d0f19e3d65705d2b1d9"),
    ("KhronosGroup/Vulkan-Headers", "vulkan-sdk-1.4.363.0", "Vulkan-Headers-vulkan-sdk-1.4.363.0", "f4be95220ff0ee0b1c620301ff4fe9ae48592d71f4c30f2d5c571351a153ae81"),
    ("KhronosGroup/SPIRV-Headers", "vulkan-sdk-1.4.363.0", "SPIRV-Headers-vulkan-sdk-1.4.363.0", "9dd4de860f9b19c707aac51143eddfba7a20d4578fca2f064937098a1cc7004f"),
]

def digest(path):
    h = hashlib.sha256()
    with path.open("rb") as stream:
        while chunk := stream.read(1024 * 1024):
            h.update(chunk)
    return h.hexdigest()

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--sdk", required=True)
    parser.add_argument("--cache", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--offline", action="store_true")
    args = parser.parse_args()
    sdk, cache, output = Path(args.sdk).resolve(), Path(args.cache).resolve(), Path(args.output).resolve()
    cache.mkdir(parents=True, exist_ok=True)
    for repo, tag, folder, sha in SOURCE_ARCHIVES:
        archive = cache / ((tag if "whisper" in repo else repo.split("/")[1] + "-" + tag) + ".zip")
        if not archive.is_file() or digest(archive) != sha:
            if args.offline:
                raise RuntimeError(f"Missing pinned {repo} source; run prepareWhisperVulkanRuntime online first")
            pending = archive.with_suffix(".pending")
            try:
                with urllib.request.urlopen(f"https://codeload.github.com/{repo}/zip/refs/tags/{tag}", timeout=120) as src, pending.open("wb") as dst:
                    while chunk := src.read(1024 * 1024): dst.write(chunk)
                if digest(pending) != sha: raise RuntimeError(f"Checksum mismatch: {repo}")
                pending.replace(archive)
            finally: pending.unlink(missing_ok=True)
        if not (cache / folder / "CMakeLists.txt").exists():
            with zipfile.ZipFile(archive) as z: z.extractall(cache)
    ndk = sdk / "ndk/27.2.12479018"
    cmake = sdk / "cmake/3.22.1/bin/cmake.exe"
    ninja = sdk / "cmake/3.22.1/bin/ninja.exe"
    env = os.environ.copy()
    if os.name == "nt":
        finder = Path(os.environ.get("ProgramFiles(x86)", "C:/Program Files (x86)")) / "Microsoft Visual Studio/Installer/vswhere.exe"
        installation = subprocess.check_output([str(finder), "-latest", "-products", "*", "-requires", "Microsoft.VisualStudio.Component.VC.Tools.x86.x64", "-property", "installationPath"], text=True).strip()
        if not installation: raise RuntimeError("Install Visual Studio C++ Build Tools for Vulkan's host shader generator")
        setup = Path(installation) / "VC/Auxiliary/Build/vcvars64.bat"
        bootstrap = cache / "host-env.bat"
        bootstrap.write_text(f'@echo off\ncall "{setup}" >nul\nif errorlevel 1 exit /b 1\nset\n', encoding="utf-8")
        captured = subprocess.check_output(["cmd", "/d", "/c", str(bootstrap)], text=True)
        for line in captured.splitlines():
            if "=" in line and not line.startswith("="):
                key, value = line.split("=", 1); env[key] = value
    source = cache / "whisper.cpp-1.9.5"
    vulkan = cache / "Vulkan-Headers-vulkan-sdk-1.4.363.0"
    spirv = cache / "SPIRV-Headers-vulkan-sdk-1.4.363.0"
    # Header-only package installs are local and never modify the system SDK.
    for name, headers in [("vk", vulkan), ("spv", spirv)]:
        b = cache / (name + "-headers-build")
        subprocess.run([str(cmake), "-S", str(headers), "-B", str(b), "-G", "Ninja", f"-DCMAKE_MAKE_PROGRAM={ninja}", f"-DCMAKE_INSTALL_PREFIX={cache / 'headers'}"], check=True, env=env)
        subprocess.run([str(cmake), "--install", str(b)], check=True, env=env, stdout=subprocess.DEVNULL)
    builder = Path(__file__).resolve().parent
    build = cache / "b"
    output.mkdir(parents=True, exist_ok=True)
    subprocess.run([str(cmake), "-S", str(builder), "-B", str(build), "-G", "Ninja",
        f"-DCMAKE_MAKE_PROGRAM={ninja}", f"-DCMAKE_TOOLCHAIN_FILE={ndk / 'build/cmake/android.toolchain.cmake'}",
        "-DANDROID_ABI=arm64-v8a", "-DANDROID_PLATFORM=android-29", "-DANDROID_STL=c++_shared",
        "-DCMAKE_BUILD_TYPE=Release", f"-DWHISPER_SOURCE={source}", f"-DRUNTIME_OUTPUT={output / 'arm64-v8a'}",
        f"-DVulkan_INCLUDE_DIR={cache / 'headers/include'}",
        f"-DSPIRV-Headers_DIR={cache / 'headers/share/cmake/SPIRV-Headers'}",
        f"-DVulkan_GLSLC_EXECUTABLE={ndk / 'shader-tools/windows-x86_64/glslc.exe'}"], check=True, env=env)
    subprocess.run([str(cmake), "--build", str(build), "--target", "carelipik_whisper", "--parallel", "4"], check=True, env=env)

if __name__ == "__main__": main()
