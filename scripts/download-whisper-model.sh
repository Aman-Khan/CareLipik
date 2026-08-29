#!/bin/sh
set -eu

MODEL_BASE_URL="https://huggingface.co/csukuangfj/sherpa-onnx-whisper-small/resolve/8f3c18b358db4d1f2fc1eae49d75cd20989e4309"
MODEL_TEMP_DIR="$(mktemp -d)"
MODEL_DESTINATION="app/src/main/assets/models/sherpa-onnx-whisper-small"
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
    "small-encoder.int8.onnx" \
    "4cbe7b22fa9026b843b60a68640c747de05bafb1a11b57edc0e66c232d9f33a9"
download_and_verify \
    "small-decoder.int8.onnx" \
    "acad50b5c782696e91b55914cc5ab4f756f1532f76e22aa6fc615f39fb69a8ee"
download_and_verify \
    "small-tokens.txt" \
    "b34b360dbb493e781e479794586d661700670d65564001f23024971d1f2fa126"

mkdir -p "$MODEL_DESTINATION"
install -m 0644 \
    "$MODEL_TEMP_DIR/small-encoder.int8.onnx" \
    "$MODEL_TEMP_DIR/small-decoder.int8.onnx" \
    "$MODEL_TEMP_DIR/small-tokens.txt" \
    "$MODEL_DESTINATION/"

echo "Offline Whisper Small model installed in $MODEL_DESTINATION"
