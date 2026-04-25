#!/bin/bash
# Aero ModelLib test runner.
# Compiles the pure-Java core/ sources together with the JUnit tests,
# then runs the JUnit suite. No Minecraft dependencies needed — these
# tests cover the parsing + animation logic only.
#
# Usage: bash tests/run.sh

set -e
HERE="$(cd "$(dirname "$0")" && pwd)"
LIB="$(cd "$HERE/.." && pwd)"

# Convert MSYS /c/ paths to Windows C:/ for javac on Windows.
win_path() {
    case "$(uname -s)" in
        MINGW*|MSYS*|CYGWIN*) echo "$1" | sed 's|^/\([a-zA-Z]\)/|\1:/|' ;;
        *) echo "$1" ;;
    esac
}

CORE="$LIB/core"
TESTS="$HERE/aero/modellib"
JUNIT="$HERE/libs/junit-4.13.2.jar"
HAMCREST="$HERE/libs/hamcrest-core-1.3.jar"
OUT="$HERE/out"

WCORE=$(win_path "$CORE")
WJUNIT=$(win_path "$JUNIT")
WHAMCREST=$(win_path "$HAMCREST")
WOUT=$(win_path "$OUT")

case "$(uname -s)" in
    MINGW*|MSYS*|CYGWIN*) SEP=";" ;;
    *) SEP=":" ;;
esac

echo "=== Compiling core/ + tests/ ==="
rm -rf "$OUT"
mkdir -p "$OUT"

# Compile only the pure-Java core files that test code uses transitively.
# Aero_AnimationDefinition references modloader-only Aero_AnimationState;
# excluding it keeps the standalone test compile self-contained.
CORE_FILES=()
while IFS= read -r f; do CORE_FILES+=("$(win_path "$f")"); done < <(find "$CORE" -name "*.java" -not -name "Aero_AnimationDefinition.java")

TEST_FILES=()
while IFS= read -r f; do TEST_FILES+=("$(win_path "$f")"); done < <(find "$TESTS" -name "*Test.java")

if [ ${#TEST_FILES[@]} -eq 0 ]; then
    echo "No test files found in $TESTS."
    exit 0
fi

javac -source 1.8 -target 1.8 \
    -cp "${WJUNIT}${SEP}${WHAMCREST}" \
    -d "$WOUT" \
    "${CORE_FILES[@]}" "${TEST_FILES[@]}"

echo "=== Running tests ==="
TEST_CLASSES=()
for f in "${TEST_FILES[@]}"; do
    name=$(basename "$f" .java)
    TEST_CLASSES+=("aero.modellib.$name")
done

java -cp "${WOUT}${SEP}${WJUNIT}${SEP}${WHAMCREST}" \
    org.junit.runner.JUnitCore \
    "${TEST_CLASSES[@]}"
