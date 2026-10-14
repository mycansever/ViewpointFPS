#!/usr/bin/env bash
# Viewpoint FPS Optimization - build script (bash, no Gradle needed)
# Usage: ./build.sh [path-to-javac]
set -euo pipefail

root="$(cd "$(dirname "$0")" && pwd)"
src_main="$root/42/media/java/client/src"
stubs="$root/tools/stubs"
build="$root/42/media/java/client/build"
classes="$build/classes"
jar_dir="$build/libs"

JAVAC="${1:-${JAVAC:-}}"
if [ -z "$JAVAC" ]; then
    if command -v javac >/dev/null 2>&1; then
        JAVAC="$(command -v javac)"
    elif [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/javac" ]; then
        JAVAC="$JAVA_HOME/bin/javac"
    else
        echo "javac not found. Install a JDK 17+ or pass the path: ./build.sh /path/to/javac" >&2
        exit 1
    fi
fi
JAR="$(dirname "$JAVAC")/jar"

mapfile -t sources < <(find "$src_main" "$stubs" -name '*.java' | sort)
echo "compiling ${#sources[@]} source files ($JAVAC)"
mkdir -p "$classes" "$jar_dir"
"$JAVAC" --release 17 -encoding UTF-8 -nowarn -d "$classes" "${sources[@]}"

out="$jar_dir/vpfps.jar"
rm -f "$out"
"$JAR" --create --file "$out" -C "$classes" dev

echo
echo "OK: $out"
"$JAR" --list --file "$out"
