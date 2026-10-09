#!/bin/sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
/usr/bin/python3 "$SCRIPT_DIR/generate_stress.py"
