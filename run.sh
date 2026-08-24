#!/usr/bin/env bash
# Compile and run the demo in one pattern folder.
#
#   ./run.sh lld/02-strategy
#   ./run.sh hld/07-aggregation-and-counting
#
# Every folder is self-contained: no packages, no dependencies, no build tool.
# Each one has exactly one class with a main() called Demo.

set -euo pipefail

dir="${1:-}"
if [[ -z "$dir" ]]; then
  echo "usage: ./run.sh <folder>      e.g. ./run.sh lld/06-state" >&2
  echo >&2
  echo "folders with runnable code:" >&2
  find lld hld -name 'Demo.java' -exec dirname {} \; | sort | sed 's/^/  /' >&2
  exit 1
fi

dir="${dir%/}"
if [[ ! -f "$dir/Demo.java" ]]; then
  echo "no Demo.java in $dir — that folder is explanation only" >&2
  exit 1
fi

# On macOS /usr/bin/javac is a stub that reports no runtime, so check it actually
# works, and fall back to a Homebrew JDK (which is keg-only and not on PATH).
if ! javac -version >/dev/null 2>&1; then
  for candidate in /opt/homebrew/opt/openjdk@21/bin /opt/homebrew/opt/openjdk/bin \
                   /usr/local/opt/openjdk@21/bin /usr/local/opt/openjdk/bin; do
    if [[ -x "$candidate/javac" ]]; then
      PATH="$candidate:$PATH"
      break
    fi
  done
fi

if ! javac -version >/dev/null 2>&1; then
  echo "No working JDK found. Install one with:" >&2
  echo "  brew install openjdk@21" >&2
  echo "and this script will pick it up automatically." >&2
  exit 1
fi

out="build/${dir//\//_}"
rm -rf "$out" && mkdir -p "$out"
javac -d "$out" "$dir"/*.java
java -cp "$out" Demo
