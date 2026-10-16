#!/bin/bash
# tools/build-core.sh
# Cross-compile shopot-core (Zig) for Android arm64-v8a.
#
# Requires: Zig 0.17.0 (https://ziglang.org/download/)
# Output: app/src/main/jniLibs/arm64-v8a/shopot
#
set -euo pipefail

ZIG_VERSION="0.17.0"
REPO_DIR="$(cd "$(dirname "$0")/../core-src" && pwd)"
PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"

echo "=== Building shopot-core for Android arm64-v8a ==="

# 1. Ensure shopot-core source is available as the declared submodule.
if [ ! -f "$PROJECT_DIR/.gitmodules" ]; then
    echo "ERROR: .gitmodules is missing; initialize the repository submodules first." >&2
    exit 1
fi
if [ ! -f "$REPO_DIR/zig/build.zig" ]; then
    echo "ERROR: core-src is not initialized. Run: git submodule update --init --recursive" >&2
    exit 1
fi

# 2. Check for Zig
if ! command -v zig &> /dev/null; then
    echo "Installing Zig ${ZIG_VERSION}..."
    case "$(uname -m)" in
        x86_64) ZIG_ARCH="x86_64" ;;
        aarch64) ZIG_ARCH="aarch64" ;;
        *) echo "Unsupported host arch"; exit 1 ;;
    esac
    ZIG_TAR="zig-linux-${ZIG_ARCH}-${ZIG_VERSION}.tar.xz"
    curl -LO "https://ziglang.org/download/${ZIG_VERSION}/${ZIG_TAR}"
    tar -xf "$ZIG_TAR"
    export PATH="$(pwd)/zig-linux-${ZIG_ARCH}-${ZIG_VERSION}/:$PATH"
    rm -rf "$ZIG_TAR"
fi

echo "Zig: $(zig version)"

# 3. Cross-compile for Android arm64-v8a
cd "$REPO_DIR/zig"

echo "Building with ReleaseSmall optimize..."
zig build -Doptimize=ReleaseSmall \
    -Dtarget=aarch64-linux-musl

# 4. Output binary
OUTPUT_DIR="$PROJECT_DIR/app/src/main/jniLibs/arm64-v8a"
ASSETS_DIR="$PROJECT_DIR/app/src/main/assets"
mkdir -p "$OUTPUT_DIR" "$ASSETS_DIR"

BINARY="$REPO_DIR/zig/zig-out/bin/shopot-zig"
if [ ! -f "$BINARY" ]; then
    echo "ERROR: build output not found at $BINARY"
    ls -la "$REPO_DIR/zig/zig-out/bin/" 2>/dev/null || true
    exit 1
fi

cp "$BINARY" "$OUTPUT_DIR/shopot"
cp "$BINARY" "$ASSETS_DIR/shopot"
chmod 755 "$OUTPUT_DIR/shopot" "$ASSETS_DIR/shopot"

echo "✓ shopot-core binary: $ASSETS_DIR/shopot ($(stat -c%s "$ASSETS_DIR/shopot") bytes)"
echo "Done."
