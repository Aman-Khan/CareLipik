#!/bin/sh
set -eu

usage() {
    echo "Usage: $0 [--serial DEVICE_SERIAL] /absolute/path/to/apollo-medical-ner-bundle" >&2
    exit 2
}

serial=""
if [ "${1:-}" = "--serial" ]; then
    [ "$#" -ge 3 ] || usage
    serial="$2"
    shift 2
fi
[ "$#" -eq 1 ] || usage

bundle="$1"
[ -d "$bundle" ] || { echo "Bundle directory not found: $bundle" >&2; exit 1; }
for file in model.int8.onnx tokenizer.json config.json; do
    [ -f "$bundle/$file" ] || { echo "Required bundle file missing: $bundle/$file" >&2; exit 1; }
done

if [ -n "$serial" ]; then
    adb_target="-s $serial"
else
    adb_target=""
fi

# shellcheck disable=SC2086
adb $adb_target get-state >/dev/null
# shellcheck disable=SC2086
adb $adb_target shell run-as com.carelipik.app mkdir -p files/models/apollo-medical-ner
for file in model.int8.onnx tokenizer.json config.json; do
    # shellcheck disable=SC2086
    adb $adb_target push "$bundle/$file" "/data/local/tmp/carelipik-apollo-$file"
    # shellcheck disable=SC2086
    adb $adb_target shell run-as com.carelipik.app cp "/data/local/tmp/carelipik-apollo-$file" "files/models/apollo-medical-ner/$file"
    # shellcheck disable=SC2086
    adb $adb_target shell run-as com.carelipik.app chmod 600 "files/models/apollo-medical-ner/$file"
    # shellcheck disable=SC2086
    adb $adb_target shell rm "/data/local/tmp/carelipik-apollo-$file"
done

echo "Installed Apollo Medical-NER in CareLipik app-private storage."
