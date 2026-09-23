#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
lib="$root/native/ferrum-native/target/release/libferrum.so"
out="$root/native/dist"

"$root/scripts/build-native.sh"

mkdir -p "$out"
cp "$lib" "$out/"
sha256sum "$lib" | awk '{print $1}' > "$out/libferrum.so.sha256"
