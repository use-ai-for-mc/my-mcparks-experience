#!/bin/bash

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="${SCRIPT_DIR}"
TARGET_DIR="/Users/cusgadmin/Library/Application Support/ModrinthApp/profiles/Fabric 1.19/mods/"

JAR_NAME="my-mcparks-experience-1.0.0.jar"
SOURCE_JAR="${PROJECT_DIR}/build/libs/${JAR_NAME}"
TARGET_JAR="${TARGET_DIR}/${JAR_NAME}"

echo "Building My MCParks Experience mod..."
cd "${PROJECT_DIR}"

# Build the macOS menu-bar status helper and stage it into resources so the
# jar produced by gradle includes it (matching what the GitHub Action does).
if [ "$(uname)" = "Darwin" ]; then
    echo "Building macOS status-helper..."
    bash "${PROJECT_DIR}/native/macos/build-status-helper.sh"
    mkdir -p "${PROJECT_DIR}/src/main/resources/native/macos"
    cp "${PROJECT_DIR}/native/macos/status-helper" \
       "${PROJECT_DIR}/src/main/resources/native/macos/status-helper"
    chmod +x "${PROJECT_DIR}/src/main/resources/native/macos/status-helper"
fi

./gradlew build

if [ ! -f "${SOURCE_JAR}" ]; then
    echo "Error: Build artifact not found at ${SOURCE_JAR}"
    exit 1
fi

echo "Creating target directory if it doesn't exist..."
mkdir -p "${TARGET_DIR}"

verify_jar() {
    local jar_file="$1"
    unzip -tq "${jar_file}" 2>/dev/null
}

# Atomic deploy: write to a temp file on the same volume, then rename.
# POSIX rename(2) is atomic — the running JVM keeps its open handle to the
# old inode, while new launches pick up the new file.  This avoids the
# "invalid zip signature" crash caused by truncate-and-rewrite via cp.
TEMP_JAR="${TARGET_JAR}.new"

MAX_RETRIES=3
RETRY_COUNT=0

while [ $RETRY_COUNT -lt $MAX_RETRIES ]; do
    echo "Copying jar to staging file..."
    cp "${SOURCE_JAR}" "${TEMP_JAR}"

    echo "Verifying staged jar integrity..."
    if verify_jar "${TEMP_JAR}"; then
        echo "Jar verification successful!"
        echo "Atomically swapping into ${TARGET_DIR}..."
        mv -f "${TEMP_JAR}" "${TARGET_JAR}"
        break
    else
        rm -f "${TEMP_JAR}"
        RETRY_COUNT=$((RETRY_COUNT + 1))
        if [ $RETRY_COUNT -lt $MAX_RETRIES ]; then
            echo "Warning: Jar verification failed (attempt $RETRY_COUNT/$MAX_RETRIES). Retrying..."
            sleep 1
        else
            echo "Error: Failed to copy a valid jar after $MAX_RETRIES attempts"
            echo "Source: ${SOURCE_JAR}"
            echo "Target: ${TARGET_JAR}"
            exit 1
        fi
    fi
done

echo "Build and deployment complete!"
echo "Jar copied to: ${TARGET_JAR}"
