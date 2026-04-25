#!/bin/bash
# Aero ModelLib release builder.
#
# Produces both runtime targets under dist/ for distribution:
#   1. ModLoader/Forge source zip — core/ + modloader/ trees, consumed by
#      mods via their transpile.sh (no precompiled JAR makes sense here:
#      Beta 1.7.3 mods source-inject the lib into their MCP workspace).
#   2. StationAPI Loom JAR — built via ./gradlew build, ready to drop into
#      a Babric mods/ folder or to publish to a Maven repo.
#
# Usage:
#   bash scripts/release.sh                # Build both, default version
#   bash scripts/release.sh 0.2.0          # Override version
#   bash scripts/release.sh 0.2.0 --gh     # Also create a GitHub release
#                                          # (requires gh CLI authenticated)
#
# JAVA17_HOME env var overrides the autodetected Java 17 for the gradle
# build. Without an override, the script looks at the standard Eclipse
# Adoptium install path on Windows.

set -e
HERE="$(cd "$(dirname "$0")/.." && pwd)"
DIST="$HERE/dist"

# Read default version from stationapi/gradle.properties so script and Loom stay in sync.
DEFAULT_VERSION=$(grep '^mod_version' "$HERE/stationapi/gradle.properties" | sed 's/.*=\s*//' | tr -d ' \r')
VERSION="${1:-$DEFAULT_VERSION}"

JAVA17="${JAVA17_HOME:-/c/Program Files/Eclipse Adoptium/jdk-17.0.18.8-hotspot}"
GH_RELEASE=false
for arg in "$@"; do
    [ "$arg" = "--gh" ] && GH_RELEASE=true
done

mkdir -p "$DIST"
rm -f "$DIST"/*.jar "$DIST"/*.zip 2>/dev/null || true

echo "=== Aero ModelLib release ==="
echo "  Version: $VERSION"
echo "  Output:  $DIST"
echo ""

# -----------------------------------------------------------------------
# 1. ModLoader/Forge source zip
# -----------------------------------------------------------------------
echo "[1/2] Building ModLoader source zip"
cd "$HERE"
ML_ZIP="aero-model-lib-modloader-$VERSION.zip"
# Using `jar` (always present with JDK) instead of `zip` (not on MSYS by default).
# `jar -cMf` = create no-manifest archive. The .zip extension is purely cosmetic
# — the format is identical, and consumers extract it the same way.
jar -cMf "$DIST/$ML_ZIP" core modloader DOC.md README.md
echo "      → $DIST/$ML_ZIP"

# -----------------------------------------------------------------------
# 2. StationAPI Loom JAR
# -----------------------------------------------------------------------
echo "[2/2] Building StationAPI Loom JAR"
cd "$HERE/stationapi"
if [ ! -f "$JAVA17/bin/java" ] && [ ! -f "$JAVA17/bin/java.exe" ]; then
    echo "  ERROR: Java 17 not found at $JAVA17"
    echo "  Set JAVA17_HOME env var to your JDK 17 install."
    exit 1
fi
JAVA_HOME="$JAVA17" PATH="$JAVA17/bin:$PATH" ./gradlew --no-daemon -q build

ST_JAR="aero-model-lib-stationapi-$VERSION.jar"
ST_SRC_JAR="aero-model-lib-stationapi-$VERSION-sources.jar"
cp "build/libs/aero-model-lib-$VERSION.jar"           "$DIST/$ST_JAR"
cp "build/libs/aero-model-lib-$VERSION-sources.jar"   "$DIST/$ST_SRC_JAR"
echo "      → $DIST/$ST_JAR"
echo "      → $DIST/$ST_SRC_JAR"

# -----------------------------------------------------------------------
# Summary
# -----------------------------------------------------------------------
cd "$HERE"
echo ""
echo "=== Artifacts ==="
ls -la "$DIST/"

# -----------------------------------------------------------------------
# Optional GitHub release
# -----------------------------------------------------------------------
if [ "$GH_RELEASE" = true ]; then
    if ! command -v gh >/dev/null 2>&1; then
        echo ""
        echo "  WARN: gh CLI not found, skipping GitHub release."
        exit 0
    fi
    echo ""
    echo "=== Creating GitHub release v$VERSION ==="
    gh release create "v$VERSION" \
        "$DIST/$ML_ZIP" \
        "$DIST/$ST_JAR" \
        "$DIST/$ST_SRC_JAR" \
        --title "v$VERSION" \
        --notes "Aero ModelLib v$VERSION — see git log for changes." \
        2>&1 | tail -3
fi
