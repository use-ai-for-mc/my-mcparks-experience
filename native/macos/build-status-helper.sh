#!/bin/bash
# Build the macOS status-bar helper binary (universal: arm64 + x86_64).
# Requires Xcode command line tools.
#
# Output: native/macos/status-helper
#
# The CI workflow at .github/workflows/build.yml builds this the same way and
# drops the result into src/main/resources/native/macos/status-helper before
# gradle runs, so it ships inside the released jar. This script is provided
# for local dev / build-and-deploy.sh so dev jars get the same feature.

set -euo pipefail
cd "$(dirname "$0")"

echo "Building status-helper for macOS..."

swiftc -O -target arm64-apple-macosx11.0 -o status-helper-arm64 \
  StatusHelper.swift -framework AppKit
swiftc -O -target x86_64-apple-macosx11.0 -o status-helper-x86_64 \
  StatusHelper.swift -framework AppKit

lipo -create -output status-helper status-helper-arm64 status-helper-x86_64
rm status-helper-arm64 status-helper-x86_64

echo "Built: $(pwd)/status-helper"
file status-helper
