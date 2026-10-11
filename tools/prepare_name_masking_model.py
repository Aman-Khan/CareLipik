"""Prepare verified English keyword-spotting assets without additional Python packages."""
import hashlib
from pathlib import Path
import struct
import tarfile
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
DESTINATION = ROOT / 'app/src/main/assets/models/english-name-kws'
BASE = 'https://github.com/k2-fsa/sherpa-onnx/releases/download/kws-models/'
ARCHIVE = 'sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01.tar.bz2'
ARCHIVE_SHA256 = 'f170013b4716e41b62b9bfd809687c207cef798ef9bc6534d524e17af9b6561a'
FILES = {
    'encoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx': (4807159, 'sha256', '1e721676515bcd42a186979733981213c66c80db680e1cc582dfedf3be76e678'),
    'decoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx': (277985, 'sha256', 'e40ff43297abe815e8898494c17e71bba2152d9d40fa3eb803f75d0f7533329a'),
    'joiner-epoch-12-avg-2-chunk-16-left-64.int8.onnx': (163380, 'sha256', 'eae9da0c7e1e6c6a3f4cc42d167899c388f6c6701b94cb96320e4f55df79624c'),
    'tokens.txt': (5006, 'sha256', 'fd2ded4050a55d2b1578870ba8697d02371980217806b7558bd0a5cc60f3ba53'),
    'bpe.model': (244837, 'sha256', 'c8a2a0129c4ab8e463164c142f82d25649661b122c8cd0b7aab5c9e80b90ad24'),
}


def verified(path, size, algorithm, expected):
    if not path.is_file() or path.stat().st_size != size:
        return False
    digest = hashlib.sha1() if algorithm == 'git-sha1' else hashlib.sha256()
    if algorithm == 'git-sha1':
        digest.update(f'blob {size}\0'.encode())
    with path.open('rb') as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b''):
            digest.update(chunk)
    return digest.hexdigest() == expected


def protobuf_fields(data):
    offset = 0

    def integer():
        nonlocal offset
        value, shift = 0, 0
        while True:
            byte = data[offset]
            offset += 1
            value |= (byte & 127) << shift
            if byte < 128:
                return value
            shift += 7
            if shift > 63:
                raise ValueError('Invalid protobuf integer')

    while offset < len(data):
        key = integer()
        wire = key & 7
        if wire == 0:
            value = integer()
        elif wire == 2:
            length = integer()
            value = data[offset:offset + length]
            offset += length
        elif wire in (1, 5):
            length = 8 if wire == 1 else 4
            value = data[offset:offset + length]
            offset += length
        else:
            raise ValueError('Unsupported protobuf wire type')
        if offset > len(data):
            raise ValueError('Truncated model')
        yield key >> 3, value


def export_bpe(model_bytes):
    lines = []
    for number, value in protobuf_fields(model_bytes):
        if number == 1:
            fields = dict(protobuf_fields(value))
            if fields.get(3, 1) == 1:
                lines.append(f"{fields[1].decode('utf-8')}\t{struct.unpack('<f', fields[2])[0]}\n")
    if not lines:
        raise ValueError('Empty BPE vocabulary')
    return ''.join(lines)


def prepare(destination=DESTINATION, archive_path=None):
    destination.mkdir(parents=True, exist_ok=True)
    if not all(verified(destination / name, *spec) for name, spec in FILES.items()):
        archive = Path(archive_path) if archive_path else ROOT / '.local-models' / ARCHIVE
        archive.parent.mkdir(parents=True, exist_ok=True)
        if not archive.is_file() or hashlib.sha256(archive.read_bytes()).hexdigest() != ARCHIVE_SHA256:
            temporary = archive.with_suffix('.part')
            try:
                print('Downloading English patient-name keyword model', flush=True)
                with urllib.request.urlopen(BASE + ARCHIVE, timeout=120) as source, temporary.open('wb') as output:
                    while chunk := source.read(1024 * 1024):
                        output.write(chunk)
                if hashlib.sha256(temporary.read_bytes()).hexdigest() != ARCHIVE_SHA256:
                    raise ValueError('Model archive integrity check failed')
                temporary.replace(archive)
            finally:
                temporary.unlink(missing_ok=True)
        with tarfile.open(archive) as source:
            for name, spec in FILES.items():
                member = source.getmember(ARCHIVE.removesuffix('.tar.bz2') + '/' + name)
                temporary = (destination / name).with_suffix('.part')
                try:
                    temporary.write_bytes(source.extractfile(member).read())
                    if not verified(temporary, *spec):
                        raise ValueError(f'Model integrity check failed: {name}')
                    temporary.replace(destination / name)
                finally:
                    temporary.unlink(missing_ok=True)
    (destination / 'bpe-scores.tsv').write_text(export_bpe((destination / 'bpe.model').read_bytes()), encoding='utf-8')
    (destination / 'empty-keywords.txt').write_text('', encoding='utf-8')
    print('English patient-name keyword model verified (about 5.5 MB).', flush=True)


if __name__ == '__main__':
    prepare()
