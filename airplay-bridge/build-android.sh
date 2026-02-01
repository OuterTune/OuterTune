#!/bin/bash
# Build script for AirPlay Bridge native library
# Compiles Rust library for all Android architectures

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUTPUT_DIR="$SCRIPT_DIR/../app/src/main/jniLibs"

# Android target architectures
TARGETS=(
    "aarch64-linux-android:arm64-v8a"
    "armv7-linux-androideabi:armeabi-v7a"
    "x86_64-linux-android:x86_64"
    "i686-linux-android:x86"
)

echo "Building AirPlay Bridge for Android..."

# Check if cargo-ndk is installed
if ! command -v cargo-ndk &> /dev/null; then
    echo "Installing cargo-ndk..."
    cargo install cargo-ndk
fi

# Create output directories
for target_pair in "${TARGETS[@]}"; do
    target="${target_pair%%:*}"
    abi="${target_pair##*:}"
    mkdir -p "$OUTPUT_DIR/$abi"
done

cd "$SCRIPT_DIR"

# Build for each target
for target_pair in "${TARGETS[@]}"; do
    target="${target_pair%%:*}"
    abi="${target_pair##*:}"

    echo "Building for $abi ($target)..."

    # Use cargo-ndk for easier Android builds
    cargo ndk -t $abi --platform 24 build --release

    # Copy the library to jniLibs
    cp "target/$target/release/libairplay_bridge.so" "$OUTPUT_DIR/$abi/"

    echo "Built $abi successfully"
done

echo ""
echo "Build complete! Libraries copied to:"
for target_pair in "${TARGETS[@]}"; do
    abi="${target_pair##*:}"
    echo "  $OUTPUT_DIR/$abi/libairplay_bridge.so"
done
