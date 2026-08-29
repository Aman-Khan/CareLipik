#!/bin/sh
set -eu

MODEL_BASE_URL="https://huggingface.co/csukuangfj/sherpa-onnx-medasr-ctc-en-int8-2025-12-25/resolve/main"
MODEL_TEMP_DIR="$(mktemp -d)"
MODEL_DESTINATION="app/src/main/assets/models/sherpa-onnx-medasr-ctc-en-int8"
trap 'rm -rf "$MODEL_TEMP_DIR"' EXIT

download_and_verify() {
    filename="$1"
    expected_sha256="$2"
    destination="$MODEL_TEMP_DIR/$filename"

    curl -fL "$MODEL_BASE_URL/$filename" -o "$destination"
    actual_sha256="$(shasum -a 256 "$destination" | cut -d ' ' -f 1)"
    if [ "$actual_sha256" != "$expected_sha256" ]; then
        echo "Checksum verification failed for $filename." >&2
        exit 1
    fi
}

download_and_verify \
    "model.int8.onnx" \
    "2c20f03265ee6144c566fd18b0f7bbb4f0d005d11ce9440dd641920210f4c33a"
download_and_verify \
    "tokens.txt" \
    "b43987c0f8f660068a166d155f02b1e439d1f03dda36d50759b4e282e98814f2"

mkdir -p "$MODEL_DESTINATION"
install -m 0644 \
    "$MODEL_TEMP_DIR/model.int8.onnx" \
    "$MODEL_TEMP_DIR/tokens.txt" \
    "$MODEL_DESTINATION/"

echo "Offline MedASR English model installed in $MODEL_DESTINATION"
