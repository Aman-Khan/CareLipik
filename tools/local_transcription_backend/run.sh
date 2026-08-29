#!/bin/sh
set -eu

KEYCHAIN_SERVICE="carelipik-sarvam-local-test"
GEMINI_KEYCHAIN_SERVICE="carelipik-gemini-local-test"
SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
SARVAM_TEST_KEY="$(security find-generic-password -a "$USER" -s "$KEYCHAIN_SERVICE" -w)"
GEMINI_TEST_KEY="$(
    security find-generic-password -a "$USER" -s "$GEMINI_KEYCHAIN_SERVICE" -w 2>/dev/null || true
)"

if [ -z "$SARVAM_TEST_KEY" ]; then
    echo "Sarvam test key is missing. Run store_sarvam_key.sh first." >&2
    exit 1
fi

if [ -n "$GEMINI_TEST_KEY" ]; then
    GEMINI_API_KEY="$GEMINI_TEST_KEY" \
        SARVAM_API_KEY="$SARVAM_TEST_KEY" \
        exec /usr/bin/python3 "$SCRIPT_DIR/server.py"
fi

SARVAM_API_KEY="$SARVAM_TEST_KEY" exec /usr/bin/python3 "$SCRIPT_DIR/server.py"
