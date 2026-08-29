#!/bin/sh
set -eu

KEYCHAIN_SERVICE="carelipik-gemini-local-test"

echo "Paste the Gemini API key when macOS prompts for the password, then press Return."
security add-generic-password \
    -U \
    -a "$USER" \
    -s "$KEYCHAIN_SERVICE" \
    -l "CareLipik local Gemini clinical term test key" \
    -w

echo "Gemini test key stored in macOS Keychain. It was not written to the repository."
