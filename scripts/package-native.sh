#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
crate="$root/native/ferrum-native"
out="$root/native/dist"

"$root/scripts/build-native.sh"

os="$(uname -s)"
arch="$(uname -m)"
case "$arch" in
  amd64|x86_64) arch="x86_64" ;;
  aarch64|arm64) arch="aarch64" ;;
esac

case "$os" in
  Linux)
    libc="glibc"
    if compgen -G "/lib/ld-musl-*" > /dev/null; then
      libc="musl"
    fi
    platform="linux-${libc}-${arch}"
    libname="libferrum.so"
    ;;
  Darwin)
    platform="macos-${arch}"
    libname="libferrum.dylib"
    ;;
  MINGW*|MSYS*|CYGWIN*)
    platform="windows-${arch}"
    libname="ferrum.dll"
    ;;
  *)
    echo "unsupported platform: $os" >&2
    exit 1
    ;;
esac

lib="$crate/target/release/$libname"
if [ ! -f "$lib" ]; then
  echo "built library not found: $lib" >&2
  exit 1
fi

version="$(grep -m1 '^version' "$crate/Cargo.toml" | sed -E 's/.*"([^"]+)".*/\1/')"
abi="$(grep -m1 'FERRUM_ABI_VERSION' "$crate/src/abi.rs" | sed -E 's/.*=[[:space:]]*([0-9]+).*/\1/')"
commit="$(git -C "$root" rev-parse HEAD 2>/dev/null || echo '')"
sha="$(sha256sum "$lib" | awk '{print $1}')"

mkdir -p "$out/$platform"
cp "$lib" "$out/$platform/$libname"
printf '%s' "$sha" > "$out/$platform/$libname.sha256"

cat > "$out/manifest.json" <<EOF
{
  "abi": ${abi:-1},
  "rustVersion": "${version}",
  "gitCommit": "${commit}",
  "libraries": [
    {
      "platform": "${platform}",
      "path": "${platform}/${libname}",
      "sha256": "${sha}",
      "abi": ${abi:-1}
    }
  ]
}
EOF

echo "packaged $platform -> $out/$platform/$libname"
echo "manifest -> $out/manifest.json"
