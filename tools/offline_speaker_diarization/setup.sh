#!/bin/sh
set -eu

SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
REPOSITORY_ROOT="$(CDPATH= cd -- "$SCRIPT_DIR/../.." && pwd)"
MODEL_DIRECTORY="$REPOSITORY_ROOT/app/src/main/assets/models/sherpa-onnx-speaker-diarization"
SEGMENTATION_FILE="$MODEL_DIRECTORY/segmentation-model.onnx"
EMBEDDING_FILE="$MODEL_DIRECTORY/nemo_en_titanet_small.onnx"

if [ "${1:-}" = "--check" ]; then
    if [ -s "$SEGMENTATION_FILE" ] && [ -s "$EMBEDDING_FILE" ]; then
        echo "Offline speaker models are installed."
        exit 0
    fi
    echo "Offline speaker models are not installed."
    exit 1
fi

DOWNLOAD_DIRECTORY="$(mktemp -d)"
trap 'rm -rf "$DOWNLOAD_DIRECTORY"' EXIT INT TERM

mkdir -p "$MODEL_DIRECTORY"

curl -fL --retry 3 --retry-delay 2 --connect-timeout 20 --max-time 300 \
    "https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-segmentation-models/sherpa-onnx-pyannote-segmentation-3-0.tar.bz2" \
    -o "$DOWNLOAD_DIRECTORY/segmentation.tar.bz2"
tar -xjf "$DOWNLOAD_DIRECTORY/segmentation.tar.bz2" -C "$DOWNLOAD_DIRECTORY"
cp "$DOWNLOAD_DIRECTORY/sherpa-onnx-pyannote-segmentation-3-0/model.onnx" \
    "$SEGMENTATION_FILE"

curl -fL --retry 3 --retry-delay 2 --connect-timeout 20 --max-time 300 \
    "https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-recongition-models/nemo_en_titanet_small.onnx" \
    -o "$EMBEDDING_FILE"

test -s "$SEGMENTATION_FILE"
test -s "$EMBEDDING_FILE"
echo "Installed offline speaker models in $MODEL_DIRECTORY"
