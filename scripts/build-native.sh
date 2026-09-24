#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/../native/ferrum-native"
cargo build --locked --release "$@"
