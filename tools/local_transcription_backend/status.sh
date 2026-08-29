#!/bin/sh
set -eu

PORT="${CARELIPIK_BACKEND_PORT:-8787}"
HEALTH_URL="http://127.0.0.1:${PORT}/health"
SARVAM_SERVICE="carelipik-sarvam-local-test"
GEMINI_SERVICE="carelipik-gemini-local-test"

key_status() {
    if security find-generic-password -a "$USER" -s "$1" >/dev/null 2>&1; then
        echo "$2 key: configured in macOS Keychain"
    else
        echo "$2 key: MISSING"
    fi
}

key_status "$SARVAM_SERVICE" "Sarvam"
key_status "$GEMINI_SERVICE" "Gemini"

HEALTH="$(curl -fsS --max-time 3 "$HEALTH_URL" 2>/dev/null || true)"
if [ -n "$HEALTH" ]; then
    echo "Backend: healthy at $HEALTH_URL"
    echo "$HEALTH"
else
    echo "Backend: NOT reachable at $HEALTH_URL"
    if lsof -nP -iTCP:"$PORT" -sTCP:LISTEN >/dev/null 2>&1; then
        echo "Port $PORT is occupied by:"
        lsof -nP -iTCP:"$PORT" -sTCP:LISTEN
    fi
fi

ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
if command -v adb >/dev/null 2>&1; then
    ADB="$(command -v adb)"
fi
if [ -x "$ADB" ]; then
    echo "ADB: $ADB"
    "$ADB" devices
    echo "Reverse mappings:"
    "$ADB" reverse --list 2>/dev/null || true
else
    echo "ADB: MISSING. Install Android SDK Platform-Tools or add it to PATH."
fi

echo "Health confirms local configuration only. Provider validity, quota, billing, and internet access are checked when a request is submitted."
