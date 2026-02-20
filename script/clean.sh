#!/usr/bin/env bash
# Remove build artifacts older than 7 days
set -euo pipefail
source bin/activate-hermit

# Install cargo-sweep if needed (pinned version for supply-chain security)
if ! command -v cargo-sweep &> /dev/null; then
    echo "Installing cargo-sweep@0.7.0..."
    cargo install cargo-sweep@0.7.0
fi

# Remove Rust artifacts older than 7 days
echo "Removing Rust build artifacts older than 7 days..."
cargo sweep --recursive --time 7

# Clean Gradle/KMP build outputs
echo "Cleaning Gradle build outputs..."
(cd app && gradle clean --quiet) || true

# Clean firmware build outputs
echo "Cleaning firmware build outputs..."
(cd firmware && inv clean) || true

echo "Clean complete!"
