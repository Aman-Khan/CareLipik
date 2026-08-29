#!/bin/sh
set -eu

PORT="${CARELIPIK_BACKEND_PORT:-8787}"
PID_FILE="${TMPDIR:-/tmp}/carelipik-local-backend-$(id -u)-${PORT}.pid"

if [ ! -f "$PID_FILE" ]; then
    echo "No managed CareLipik backend PID file exists for port $PORT."
    echo "Check the listener with: lsof -nP -iTCP:$PORT -sTCP:LISTEN"
    exit 0
fi

PID="$(sed -n '1p' "$PID_FILE")"
case "$PID" in
    *[!0-9]*|'') echo "Invalid backend PID file; remove $PID_FILE manually." >&2; exit 1 ;;
esac

COMMAND="$(ps -p "$PID" -o command= 2>/dev/null || true)"
case "$COMMAND" in
    *local_transcription_backend/server.py*) ;;
    *)
        echo "PID $PID is not the CareLipik backend; it will not be stopped." >&2
        rm -f "$PID_FILE"
        exit 1
        ;;
esac

kill "$PID"
rm -f "$PID_FILE"
echo "Stopped CareLipik backend PID $PID on port $PORT."
