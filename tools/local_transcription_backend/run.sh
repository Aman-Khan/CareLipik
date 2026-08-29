#!/bin/sh
set -eu

KEYCHAIN_SERVICE="carelipik-sarvam-local-test"
GEMINI_KEYCHAIN_SERVICE="carelipik-gemini-local-test"
SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
PORT="${CARELIPIK_BACKEND_PORT:-8787}"
PID_FILE="${TMPDIR:-/tmp}/carelipik-local-backend-$(id -u)-${PORT}.pid"
HEALTH_URL="http://127.0.0.1:${PORT}/health"

case "$PORT" in
    *[!0-9]*|'') echo "CARELIPIK_BACKEND_PORT must be a number." >&2; exit 1 ;;
esac
if [ "$PORT" -lt 1 ] || [ "$PORT" -gt 65535 ]; then
    echo "CARELIPIK_BACKEND_PORT must be between 1 and 65535." >&2
    exit 1
fi

EXISTING_HEALTH="$(curl -fsS --max-time 2 "$HEALTH_URL" 2>/dev/null || true)"
if [ -n "$EXISTING_HEALTH" ]; then
    echo "CareLipik backend is already healthy on port $PORT. Do not start a second copy."
    echo "$EXISTING_HEALTH"
    echo "Run ./tools/local_transcription_backend/stop.sh before restarting it with new keys."
    exit 0
fi

if lsof -nP -iTCP:"$PORT" -sTCP:LISTEN >/dev/null 2>&1; then
    echo "Port $PORT is occupied by a process that is not a healthy CareLipik backend." >&2
    lsof -nP -iTCP:"$PORT" -sTCP:LISTEN >&2
    echo "Stop that exact process or choose CARELIPIK_BACKEND_PORT before retrying." >&2
    exit 1
fi

SARVAM_TEST_KEY="$(
    security find-generic-password -a "$USER" -s "$KEYCHAIN_SERVICE" -w 2>/dev/null || true
)"
GEMINI_TEST_KEY="$(
    security find-generic-password -a "$USER" -s "$GEMINI_KEYCHAIN_SERVICE" -w 2>/dev/null || true
)"

if [ -z "$SARVAM_TEST_KEY" ]; then
    echo "Sarvam key is missing from macOS Keychain." >&2
    echo "Run ./tools/local_transcription_backend/store_sarvam_key.sh first." >&2
    exit 1
fi
if [ -z "$GEMINI_TEST_KEY" ]; then
    echo "Warning: Gemini key is missing. Transcription can work, but medical-term and note generation will be disabled." >&2
    echo "Run ./tools/local_transcription_backend/store_gemini_key.sh, then restart this backend." >&2
fi

cleanup() {
    rm -f "$PID_FILE"
}

stop_server() {
    kill "$SERVER_PID" 2>/dev/null || true
    wait "$SERVER_PID" 2>/dev/null || true
    cleanup
    exit 130
}

if [ -n "$GEMINI_TEST_KEY" ]; then
    GEMINI_API_KEY="$GEMINI_TEST_KEY" SARVAM_API_KEY="$SARVAM_TEST_KEY" \
        /usr/bin/python3 -u "$SCRIPT_DIR/server.py" &
else
    SARVAM_API_KEY="$SARVAM_TEST_KEY" /usr/bin/python3 -u "$SCRIPT_DIR/server.py" &
fi
SERVER_PID=$!
printf '%s\n' "$SERVER_PID" > "$PID_FILE"
trap stop_server INT TERM HUP

set +e
wait "$SERVER_PID"
STATUS=$?
set -e
cleanup
exit "$STATUS"
