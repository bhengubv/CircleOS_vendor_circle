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

# Build
echo "Building with $(nproc) cores (log: ${LOG_FILE})..."
make -j$(nproc) 2>&1 | tee "${LOG_FILE}"

# Package
mkdir -p "${RELEASE_DIR}"
RELEASE_ZIP="${RELEASE_DIR}/circle-${VERSION}-${DEVICE}-${BUILD_DATE}.zip"

echo "Packaging..."
if [ -f "${OUT_DIR}/ota_update_package.zip" ]; then
    cp "${OUT_DIR}/ota_update_package.zip" "${RELEASE_ZIP}"
else
    # Fallback: manual zip for sideload
    zip -j "${RELEASE_ZIP}" \
        "${OUT_DIR}/system.img" \
        "${OUT_DIR}/vendor.img" \
        "${OUT_DIR}/boot.img" \
        "${OUT_DIR}/recovery.img" 2>/dev/null || true
fi

# Checksum
sha256sum "${RELEASE_ZIP}" > "${RELEASE_ZIP}.sha256"
echo ""
echo "Build complete:"
echo "  Image   : ${RELEASE_ZIP}"
echo "  SHA-256 : ${RELEASE_ZIP}.sha256"
echo ""
echo "Next steps:"
echo "  1. Flash on device and run verify_build.sh"
echo "  2. Check all items in alpha_checklist.md"
echo "  3. Upload to GitHub Releases"
