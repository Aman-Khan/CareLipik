#!/bin/sh
set -eu

KEYCHAIN_SERVICE="carelipik-sarvam-local-test"
SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
SARVAM_TEST_KEY="$(security find-generic-password -a "$USER" -s "$KEYCHAIN_SERVICE" -w)"

if [ -z "$SARVAM_TEST_KEY" ]; then
    echo "Sarvam test key is missing. Run ../local_transcription_backend/store_sarvam_key.sh first." >&2
    exit 1
fi

SARVAM_API_KEY="$SARVAM_TEST_KEY" exec /usr/bin/python3 "$SCRIPT_DIR/generate.py" "$@"
