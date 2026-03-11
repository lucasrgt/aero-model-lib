#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────
# AeroModelLib — convert.sh
# Converts Blockbench .bbmodel files to AeroModelLib .anim.json format
#
# Usage:
#   bash scripts/convert.sh MyMachine.bbmodel
#   bash scripts/convert.sh MyMachine.bbmodel output.anim.json
#
# Requires: Java 8+ (JDK to recompile, JRE to run pre-compiled .class)
#
# by lucasrgt — aerocoding.dev
# ─────────────────────────────────────────────────────────────────────

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

if [ $# -lt 1 ]; then
    echo "AeroModelLib Converter"
    echo ""
    echo "Usage: bash convert.sh <input.bbmodel> [output.anim.json]"
    echo ""
    echo "Converts Blockbench .bbmodel files to .anim.json format"
    echo "for use with AeroModelLib's animation system."
    echo ""
    echo "The OBJ model must be exported manually from Blockbench:"
    echo "  File > Export > Export OBJ Model"
    echo ""
    echo "See README.md for the full workflow."
    exit 1
fi

# Compile if needed
if [ ! -f "$SCRIPT_DIR/Aero_Convert.class" ] || [ "$SCRIPT_DIR/Aero_Convert.java" -nt "$SCRIPT_DIR/Aero_Convert.class" ]; then
    javac "$SCRIPT_DIR/Aero_Convert.java"
fi

java -cp "$SCRIPT_DIR" Aero_Convert "$@"
