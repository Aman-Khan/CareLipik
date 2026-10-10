#!/bin/sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
REPO_ROOT=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
ADB_BIN=${ADB_BIN:-adb}

if [ "$#" -gt 1 ]; then
    echo "Usage: $0 [device-serial]" >&2
    exit 2
fi

DEVICE_SERIAL=${1:-}
if [ -z "$DEVICE_SERIAL" ]; then
    DEVICE_SERIAL=$(
        "$ADB_BIN" devices |
            awk 'NR > 1 && $2 == "device" { print $1 }'
    )
    DEVICE_COUNT=$(printf '%s\n' "$DEVICE_SERIAL" | awk 'NF { count++ } END { print count + 0 }')
    if [ "$DEVICE_COUNT" -ne 1 ]; then
        echo "Connect exactly one authorized device or pass its serial explicitly." >&2
        exit 1
    fi
fi

cd "$REPO_ROOT"
./gradlew assembleDebug

APK_PATH="$REPO_ROOT/app/build/outputs/apk/debug/app-debug.apk"
if [ ! -f "$APK_PATH" ]; then
    echo "Debug APK was not created at $APK_PATH" >&2
    exit 1
fi

echo "Updating CareLipik on $DEVICE_SERIAL without uninstalling or clearing app data..."
"$ADB_BIN" -s "$DEVICE_SERIAL" install -r "$APK_PATH"
echo "Installed successfully. Profile, API keys, and consultation history were preserved."
