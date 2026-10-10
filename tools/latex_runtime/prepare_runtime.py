"""Package pinned BusyTeX/TeX Live assets for offline Android XeLaTeX compilation."""
import argparse
import hashlib
import shutil
import tarfile
import urllib.request
from pathlib import Path

ASSETS = "https://github.com/TeXlyre/texlyre-busytex/releases/download/assets-v1.4.0/busytex-assets.tar.gz"
PACKAGE = "https://registry.npmjs.org/texlyre-busytex/-/texlyre-busytex-1.4.0.tgz"
FONTS = "https://raw.githubusercontent.com/notofonts/noto-fonts/main/"
DOWNLOADS = {
    "busytex-assets.tar.gz": (ASSETS, "1caa434fb5aab5bdd59dc303bca2ac7b9b9af02ef1627bf8652caabfa1b7cd2b"),
    "busytex-package.tgz": (PACKAGE, "e221f621ac3be0962c6e0608bc746eccd261ae2583afd57c33ef23415c50b190"),
    "NotoSans-Regular.ttf": (FONTS + "hinted/ttf/NotoSans/NotoSans-Regular.ttf", "b85c38ecea8a7cfb39c24e395a4007474fa5a4fc864f6ee33309eb4948d232d5"),
    "NotoSans-Bold.ttf": (FONTS + "hinted/ttf/NotoSans/NotoSans-Bold.ttf", "c976e4b1b99edc88775377fcc21692ca4bfa46b6d6ca6522bfda505b28ff9d6a"),
    "NotoSansDevanagari-Regular.ttf": (FONTS + "hinted/ttf/NotoSansDevanagari/NotoSansDevanagari-Regular.ttf", "385e78e6359a9d88a0f243d53b1209d7548361ba2194e2b9ec779bcaa7e8949d"),
}
RUNTIME_FILES = ["busytex.js", "busytex.wasm", "busytex_worker.js", "busytex_pipeline.js",
    "busytex_biber.js", "texlive-basic.js", "texlive-basic.data", "texlive-recommended.js", "texlive-recommended.data", "versions.txt"]

def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--cache", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--offline", action="store_true")
    args = parser.parse_args()
    args.cache.mkdir(parents=True, exist_ok=True)
    output = args.output / "latex" / "vendor"
    stamp = output / ".ready"
    identity = digest(Path(__file__))
    expected = [output / "busytex" / name for name in RUNTIME_FILES] + [output / "index.js"] + [output / "fonts" / name for name in DOWNLOADS if name.endswith(".ttf")]
    if stamp.exists() and stamp.read_text() == identity and all(p.is_file() for p in expected):
        print("Offline LaTeX runtime ready", flush=True)
        return
    for name, (url, checksum) in DOWNLOADS.items():
        target = args.cache / name
        if not target.is_file() or digest(target) != checksum:
            if args.offline:
                raise RuntimeError(f"Missing verified LaTeX asset {name}; run prepareLatexRuntime once without --offline.")
            pending = target.with_suffix(target.suffix + ".pending")
            print(f"Downloading LaTeX build asset: {name}", flush=True)
            with urllib.request.urlopen(url, timeout=120) as response, pending.open("wb") as stream:
                shutil.copyfileobj(response, stream)
            if digest(pending) != checksum:
                pending.unlink()
                raise RuntimeError(f"Checksum mismatch: {name}")
            pending.replace(target)
    (output / "busytex").mkdir(parents=True, exist_ok=True)
    with tarfile.open(args.cache / "busytex-assets.tar.gz", "r:gz") as archive:
        for name in RUNTIME_FILES:
            with archive.extractfile("busytex/" + name) as stream, (output / "busytex" / name).open("wb") as target:
                shutil.copyfileobj(stream, target)
    with tarfile.open(args.cache / "busytex-package.tgz", "r:gz") as archive:
        for source, name in [("package/dist/index.js", "index.js"), ("package/LICENSE", "BUSYTEX-LICENSE.txt")]:
            (output / name).write_bytes(archive.extractfile(source).read())
    (output / "fonts").mkdir(exist_ok=True)
    for name in DOWNLOADS:
        if name.endswith(".ttf"):
            shutil.copyfile(args.cache / name, output / "fonts" / name)
    license_file = Path(__file__).parent / "NOTO-OFL.txt"
    shutil.copyfile(license_file, output / "fonts" / "OFL.txt")
    stamp.write_text(identity)
    print("Packaged BusyTeX 1.4.0 and TeX Live 2026 for offline XeLaTeX", flush=True)

if __name__ == "__main__":
    main()
