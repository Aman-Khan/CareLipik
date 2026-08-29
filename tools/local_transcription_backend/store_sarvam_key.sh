#!/bin/sh
set -eu

KEYCHAIN_SERVICE="carelipik-sarvam-local-test"

echo "Paste the Sarvam API key when macOS prompts for the password, then press Return."
security add-generic-password \
    -U \
    -a "$USER" \
    -s "$KEYCHAIN_SERVICE" \
    -l "CareLipik local Sarvam test key" \
    -w

echo "Sarvam test key stored in macOS Keychain. It was not written to the repository."
