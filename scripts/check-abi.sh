#!/usr/bin/env bash
#
# Verifies that the Ferrum ABI version and the exported symbol surface stay in sync across the
# three places they are declared:
#   1. native/ferrum-native/src/abi.rs        (Rust constants)
#   2. native/ferrum-native/include/ferrum_abi.h (human-reviewable C header)
#   3. java-shared/core-runtime/.../ffm/NativeBindings.java (Java FunctionDescriptors)
#
# The test-only `ferrum_selftest_panic` hook (cargo feature `test-hooks`) is intentionally absent
# from the header and is handled as an allowed Java-only extra.
set -euo pipefail

repo_root="$(cd "$(dirname "$0")/.." && pwd)"

rust_abi="$repo_root/native/ferrum-native/src/abi.rs"
rust_src="$repo_root/native/ferrum-native/src"
header="$repo_root/native/ferrum-native/include/ferrum_abi.h"
bindings="$repo_root/java-shared/core-runtime/src/main/java/top/likoslupus/ferrum/runtime/ffm/NativeBindings.java"

test_hook_symbol="ferrum_selftest_panic"

fail() {
    echo "ABI drift check failed: $*" >&2
    exit 1
}

for file in "$rust_abi" "$header" "$bindings"; do
    [[ -f "$file" ]] || fail "missing expected file: ${file#"$repo_root/"}"
done

# --- ABI version consistency ----------------------------------------------------------------

rust_version="$(grep -oE 'FERRUM_ABI_VERSION: u32 = [0-9]+' "$rust_abi" | grep -oE '[0-9]+$' || true)"
header_version="$(grep -oE '^#define FERRUM_ABI_VERSION [0-9]+u' "$header" | grep -oE '[0-9]+' || true)"
java_version="$(grep -oE 'EXPECTED_ABI = [0-9]+' "$bindings" | grep -oE '[0-9]+' || true)"

[[ -n "$rust_version" ]] || fail "could not read FERRUM_ABI_VERSION from src/abi.rs"
[[ -n "$header_version" ]] || fail "could not read FERRUM_ABI_VERSION from include/ferrum_abi.h"
[[ -n "$java_version" ]] || fail "could not read EXPECTED_ABI from NativeBindings.java"

if [[ "$rust_version" != "$header_version" || "$rust_version" != "$java_version" ]]; then
    fail "ABI version mismatch: rust=$rust_version header=$header_version java=$java_version"
fi
echo "ABI version consistent: $rust_version"

# --- Exported symbol surface ----------------------------------------------------------------

header_symbols="$(
    grep -oE '\bferrum_[a-z0-9_]+[[:space:]]*\(' "$header" \
        | grep -oE 'ferrum_[a-z0-9_]+' | sort -u || true
)"

rust_symbols="$(
    grep -rhoE 'fn[[:space:]]+ferrum_[a-z0-9_]+[[:space:]]*\(' "$rust_src" \
        | grep -oE 'ferrum_[a-z0-9_]+' | sort -u || true
)"
rust_default_symbols="$(printf '%s\n' "$rust_symbols" | grep -vx "$test_hook_symbol" | sort -u || true)"

java_symbols="$(
    grep -oE '"ferrum_[a-z0-9_]+"' "$bindings" | tr -d '"' | sort -u || true
)"
java_expected_symbols="$(printf '%s\n%s\n' "$header_symbols" "$test_hook_symbol" | sort -u || true)"

[[ -n "$header_symbols" ]] || fail "no symbols parsed from ${header#"$repo_root/"}"
[[ -n "$rust_symbols" ]] || fail "no symbols parsed from ${rust_src#"$repo_root/"}/**/*.rs"

if ! diff <(printf '%s\n' "$header_symbols") <(printf '%s\n' "$rust_default_symbols") >/dev/null; then
    echo "header symbols:" >&2
    printf '%s\n' "$header_symbols" >&2
    echo "rust (default build) symbols:" >&2
    printf '%s\n' "$rust_default_symbols" >&2
    fail "header prototypes and Rust exports differ"
fi

if ! diff <(printf '%s\n' "$java_expected_symbols") <(printf '%s\n' "$java_symbols") >/dev/null; then
    echo "expected (header + test hook) symbols:" >&2
    printf '%s\n' "$java_expected_symbols" >&2
    echo "Java binding symbols:" >&2
    printf '%s\n' "$java_symbols" >&2
    fail "Java bindings and the header/test-hook surface differ"
fi

symbol_count="$(printf '%s\n' "$header_symbols" | wc -l | tr -d ' ')"
echo "ABI symbol surface consistent: $symbol_count symbols (+ $test_hook_symbol test hook)"
