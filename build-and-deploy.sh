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

MAX_RETRIES=3
RETRY_COUNT=0

while [ $RETRY_COUNT -lt $MAX_RETRIES ]; do
    echo "Copying jar to ${TARGET_DIR}..."
    cp -f "${SOURCE_JAR}" "${TARGET_JAR}"

    echo "Verifying copied jar integrity..."
    if verify_jar "${TARGET_JAR}"; then
        echo "Jar verification successful!"
        break
    else
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
