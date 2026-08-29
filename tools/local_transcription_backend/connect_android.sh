#!/bin/sh
set -eu

PORT="${CARELIPIK_BACKEND_PORT:-8787}"
ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
if command -v adb >/dev/null 2>&1; then
    ADB="$(command -v adb)"
fi
if [ ! -x "$ADB" ]; then
    echo "ADB is missing. Install Android SDK Platform-Tools or add platform-tools to PATH." >&2
    exit 1
fi

if [ "$#" -gt 1 ]; then
    echo "Usage: $0 [DEVICE_SERIAL]" >&2
    exit 1
fi

if [ "$#" -eq 1 ]; then
    SERIAL="$1"
else
    SERIALS="$("$ADB" devices | awk 'NR > 1 && $2 == "device" { print $1 }')"
    COUNT="$(printf '%s\n' "$SERIALS" | awk 'NF { count++ } END { print count + 0 }')"
    if [ "$COUNT" -eq 0 ]; then
        echo "No authorized Android device is connected. Check USB debugging and accept the phone prompt." >&2
        "$ADB" devices >&2
        exit 1
    fi
    if [ "$COUNT" -gt 1 ]; then
        echo "Multiple Android devices are connected. Pass one serial:" >&2
        "$ADB" devices >&2
        echo "$0 DEVICE_SERIAL" >&2
        exit 1
    fi
    SERIAL="$SERIALS"
fi

"$ADB" -s "$SERIAL" reverse tcp:"$PORT" tcp:"$PORT"
echo "Connected Android device $SERIAL to the CareLipik backend on port $PORT."
"$ADB" -s "$SERIAL" reverse --list
