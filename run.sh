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

if ! command -v javac >/dev/null 2>&1 || ! javac -version >/dev/null 2>&1; then
  echo "No JDK found. Install one first:" >&2
  echo "  brew install openjdk@21" >&2
  echo "  sudo ln -sfn /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk \\" >&2
  echo "               /Library/Java/JavaVirtualMachines/openjdk-21.jdk" >&2
  exit 1
fi

out="build/${dir//\//_}"
rm -rf "$out" && mkdir -p "$out"
javac -d "$out" "$dir"/*.java
java -cp "$out" Demo
