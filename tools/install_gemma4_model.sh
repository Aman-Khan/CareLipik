#!/bin/sh
set -eu

usage() {
    echo "Usage: $0 [--serial DEVICE_SERIAL] /absolute/path/to/gemma-4-E2B-it-gpu.litertlm" >&2
    exit 2
}

serial=""
if [ "${1:-}" = "--serial" ]; then
    [ "$#" -ge 3 ] || usage
    serial="$2"
    shift 2
fi
[ "$#" -eq 1 ] || usage

model_path="$1"
expected_name="gemma-4-e2b-it-gpu.litertlm"
[ -f "$model_path" ] || { echo "Model file not found: $model_path" >&2; exit 1; }
case "$model_path" in
    *.litertlm) ;;
    *) echo "Expected a verified .litertlm model bundle: $model_path" >&2; exit 1 ;;
esac

if [ -n "$serial" ]; then
    adb_target="-s $serial"
else
    adb_target=""
fi

# shellcheck disable=SC2086
adb $adb_target get-state >/dev/null
# shellcheck disable=SC2086
adb $adb_target push "$model_path" "/data/local/tmp/$expected_name"
# shellcheck disable=SC2086
adb $adb_target shell run-as com.carelipik.app mkdir -p files/models
# shellcheck disable=SC2086
adb $adb_target shell run-as com.carelipik.app cp "/data/local/tmp/$expected_name" "files/models/$expected_name"
# shellcheck disable=SC2086
adb $adb_target shell run-as com.carelipik.app chmod 600 "files/models/$expected_name"
# shellcheck disable=SC2086
adb $adb_target shell rm "/data/local/tmp/$expected_name"

echo "Installed $(basename "$model_path") as $expected_name in CareLipik app-private storage."
