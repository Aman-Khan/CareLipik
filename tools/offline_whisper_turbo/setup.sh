#!/bin/sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
REPOSITORY_ROOT=$(CDPATH= cd -- "$SCRIPT_DIR/../.." && pwd)
MODEL_DIRECTORY="$REPOSITORY_ROOT/app/src/main/assets/models/sherpa-onnx-whisper-turbo"
BASE_URL="https://huggingface.co/csukuangfj/sherpa-onnx-whisper-turbo/resolve/main"

required_files="turbo-encoder.int8.onnx turbo-decoder.int8.onnx turbo-tokens.txt"

check_models() {
    missing=0
    for file_name in $required_files; do
        file_path="$MODEL_DIRECTORY/$file_name"
        if [ ! -s "$file_path" ]; then
            echo "Missing: $file_path"
            missing=1
        else
            file_size=$(wc -c < "$file_path" | tr -d ' ')
            echo "Ready: $file_name ($file_size bytes)"
        fi
    done
    return "$missing"
}

if [ "${1:-}" = "--check" ]; then
    check_models
    exit $?
fi

mkdir -p "$MODEL_DIRECTORY"
for file_name in $required_files; do
    destination="$MODEL_DIRECTORY/$file_name"
    if [ -s "$destination" ]; then
        echo "Keeping existing $file_name"
        continue
    fi
    pending="$destination.pending"
    echo "Downloading $file_name..."
    curl --fail --location --retry 3 --output "$pending" "$BASE_URL/$file_name"
    mv "$pending" "$destination"
done

check_models
echo "Whisper Turbo assets are installed locally. Rebuild and reinstall the APK."
