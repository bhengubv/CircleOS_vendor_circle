#!/bin/bash
#
# CircleOS Alpha Release Build Script
#
# Usage: ./vendor/circle/release/build_release.sh [device] [variant]
# Example: ./vendor/circle/release/build_release.sh redmi_note12 userdebug
#
# Supported devices:
#   redmi_note12  — Xiaomi Redmi Note 12 (minimum supported, SM6225)
#   pixel6        — Google Pixel 6 (reference device, GS101)
#   emulator      — x86_64 AVD for CI testing
#

set -euo pipefail

DEVICE="${1:-redmi_note12}"
VARIANT="${2:-userdebug}"
VERSION="0.1.0-alpha"
BUILD_DATE=$(date +%Y%m%d)
OUT_DIR="out/target/product/${DEVICE}"
RELEASE_DIR="release/${VERSION}"
LOG_FILE="build_${DEVICE}_${BUILD_DATE}.log"

echo "╔══════════════════════════════════════════════════╗"
echo "║   CircleOS ${VERSION} Build                      ║"
echo "║   Device : ${DEVICE}                             ║"
echo "║   Variant: ${VARIANT}                            ║"
echo "║   Date   : ${BUILD_DATE}                         ║"
echo "╚══════════════════════════════════════════════════╝"

# Verify we are in the AOSP root
if [ ! -f build/envsetup.sh ]; then
    echo "ERROR: Run this script from the AOSP root directory"
    exit 1
fi

# Initialize build environment
source build/envsetup.sh
lunch "circle_${DEVICE}-${VARIANT}"

# Build — otapackage produces the A/B payload
echo "Building with $(nproc) cores (log: ${LOG_FILE})..."
make -j$(nproc) otapackage 2>&1 | tee "${LOG_FILE}"

# Package — A/B OTA format: payload.bin + payload_properties.txt
mkdir -p "${RELEASE_DIR}"
RELEASE_DIR_DEVICE="${RELEASE_DIR}/${DEVICE}-${BUILD_DATE}"
mkdir -p "${RELEASE_DIR_DEVICE}"

echo "Packaging A/B OTA..."
if [ -f "${OUT_DIR}/payload.bin" ]; then
    cp "${OUT_DIR}/payload.bin"            "${RELEASE_DIR_DEVICE}/payload.bin"
    cp "${OUT_DIR}/payload_properties.txt" "${RELEASE_DIR_DEVICE}/payload_properties.txt"
elif [ -f "${OUT_DIR}/ota_update_package.zip" ]; then
    # Extract A/B artifacts from the OTA zip
    unzip -o "${OUT_DIR}/ota_update_package.zip" \
        payload.bin payload_properties.txt \
        -d "${RELEASE_DIR_DEVICE}"
else
    echo "ERROR: No OTA package found in ${OUT_DIR}. Did 'make otapackage' succeed?"
    exit 1
fi

# Manifest JSON consumed by ota.circleos.co.za and SystemUpdateService
cat > "${RELEASE_DIR_DEVICE}/manifest.json" <<MANIFEST
{
  "version": "${VERSION}",
  "buildDate": "${BUILD_DATE}",
  "device": "${DEVICE}",
  "channel": "${VARIANT}",
  "payloadUrl": "https://cdn.thegeek.co.za/circleos/${VERSION}/${DEVICE}-${BUILD_DATE}/payload.bin",
  "payloadSize": $(wc -c < "${RELEASE_DIR_DEVICE}/payload.bin"),
  "payloadHash": "$(sha256sum "${RELEASE_DIR_DEVICE}/payload.bin" | awk '{print $1}')"
}
MANIFEST

# Checksums
sha256sum "${RELEASE_DIR_DEVICE}/payload.bin" > "${RELEASE_DIR_DEVICE}/payload.bin.sha256"

echo ""
echo "Build complete:"
echo "  Directory  : ${RELEASE_DIR_DEVICE}/"
echo "  payload.bin: $(du -sh "${RELEASE_DIR_DEVICE}/payload.bin" | cut -f1)"
echo "  manifest   : ${RELEASE_DIR_DEVICE}/manifest.json"
echo ""
echo "Next steps:"
echo "  1. Upload ${RELEASE_DIR_DEVICE}/ to cdn.thegeek.co.za/circleos/${VERSION}/${DEVICE}-${BUILD_DATE}/"
echo "  2. Register release via: POST sleptonapi.thegeeknetwork.co.za/api/os/releases"
echo "  3. Check all items in alpha_checklist.md"
