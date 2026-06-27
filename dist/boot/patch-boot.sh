#!/usr/bin/env bash
# Circle OS — Magisk-patched, Circle-AVB-signed boot.img for the unlocked path (#151).
#
# AOSP's `make otapackage` already produces a fully AVB-signed A/B payload for the
# normal (locked / DSU) install paths. This script serves the *power-user* path:
# a user with an unlocked bootloader who wants root. It patches the stock Circle
# boot image with Magisk and re-signs it with the Circle AVB key, so the patched
# image still passes verified boot on a device whose bootloader trusts our key.
#
# Pipeline:
#   1. (optional) extract boot.img from an A/B payload.bin            [payload tools]
#   2. patch it with Magisk                                          [Magisk boot_patch.sh + magiskboot]
#   3. re-sign with the Circle AVB key                               [avbtool add_hash_footer]
#   4. emit <out>, <out>.sha256, and a manifest line
#
# Usage:
#   patch-boot.sh --boot <boot.img> [--out boot-magisk-signed.img] \
#                 --avb-key <key.pem> --partition-size <bytes> \
#                 [--magisk-src <dir>] [--avbtool <path>]
#   patch-boot.sh --payload <payload.bin> --partition-size <bytes> --avb-key <key.pem>
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CIRCLE_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
AOSP_ROOT="$(cd "${CIRCLE_ROOT}/../.." && pwd)"   # vendor/circle -> AOSP root

die() { echo "ERROR: $*" >&2; exit 1; }

BOOT=""
PAYLOAD=""
OUT="boot-magisk-signed.img"
AVB_KEY="${CIRCLE_ROOT}/release/security/avb_key.pem"
PART_SIZE=""
MAGISK_SRC="${MAGISK_SRC:-}"
AVBTOOL=""

while [ $# -gt 0 ]; do
  case "$1" in
    --boot)           BOOT="${2:-}"; shift 2 ;;
    --payload)        PAYLOAD="${2:-}"; shift 2 ;;
    --out)            OUT="${2:-}"; shift 2 ;;
    --avb-key)        AVB_KEY="${2:-}"; shift 2 ;;
    --partition-size) PART_SIZE="${2:-}"; shift 2 ;;
    --magisk-src)     MAGISK_SRC="${2:-}"; shift 2 ;;
    --avbtool)        AVBTOOL="${2:-}"; shift 2 ;;
    -h|--help)        sed -n '2,20p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *)                die "unknown arg: $1" ;;
  esac
done

WORK="$(mktemp -d)"
trap 'rm -rf "${WORK}"' EXIT

# ── 1. Obtain the stock boot.img ──────────────────────────────────────────────
if [ -z "${BOOT}" ]; then
  [ -n "${PAYLOAD}" ] || die "give --boot <img> or --payload <payload.bin>"
  [ -f "${PAYLOAD}" ] || die "no such payload: ${PAYLOAD}"
  echo "==> extracting boot from $(basename "${PAYLOAD}")"
  EXTRACTOR=""
  for c in "${AOSP_ROOT}/out/host/linux-x86/bin/ota_extractor" ota_extractor payload-dumper-go; do
    command -v "$c" >/dev/null 2>&1 && { EXTRACTOR="$c"; break; }
    [ -x "$c" ] && { EXTRACTOR="$c"; break; }
  done
  [ -n "${EXTRACTOR}" ] || die "no payload extractor found (build ota_extractor, or install payload-dumper-go)"
  case "${EXTRACTOR}" in
    *ota_extractor*) "${EXTRACTOR}" -payload "${PAYLOAD}" -output_dir "${WORK}" -partitions boot ;;
    *payload-dumper-go*) "${EXTRACTOR}" -p boot -o "${WORK}" "${PAYLOAD}" ;;
  esac
  BOOT="$(find "${WORK}" -name 'boot.img' | head -1)"
  [ -n "${BOOT}" ] || die "extractor did not yield boot.img"
fi
[ -f "${BOOT}" ] || die "no such boot image: ${BOOT}"

# ── 2. Patch with Magisk ──────────────────────────────────────────────────────
# Use Magisk's own boot_patch.sh (canonical patcher) with our host magiskboot and
# the matching device binaries from prebuilt/magiskboot/.
MB="$("${SCRIPT_DIR}/magiskboot.sh" path)"
PREBUILT_DIR="$(dirname "${MB}")"
BOOT_PATCH=""
for c in "${MAGISK_SRC}/scripts/boot_patch.sh" "${PREBUILT_DIR}/boot_patch.sh"; do
  [ -n "$c" ] && [ -f "$c" ] && { BOOT_PATCH="$c"; break; }
done
[ -n "${BOOT_PATCH}" ] || die "boot_patch.sh not found — pass --magisk-src <fork> or vendor it next to magiskboot"

echo "==> patching boot image with Magisk"
cp "${BOOT}" "${WORK}/boot.img"
# boot_patch.sh expects magiskboot + magisk binaries on PATH/cwd; provide them.
( cd "${WORK}" && PATH="${PREBUILT_DIR}:${PATH}" MAGISKBOOT="${MB}" \
    sh "${BOOT_PATCH}" "${WORK}/boot.img" ) || die "Magisk boot_patch.sh failed"
PATCHED="${WORK}/new-boot.img"
[ -f "${PATCHED}" ] || die "boot_patch.sh did not produce new-boot.img"

# ── 3. Re-sign with the Circle AVB key ────────────────────────────────────────
[ -f "${AVB_KEY}" ] || die "AVB key not found: ${AVB_KEY} (pass --avb-key)"
[ -n "${PART_SIZE}" ] || die "--partition-size <bytes> is required to add the AVB hash footer"
if [ -z "${AVBTOOL}" ]; then
  for c in "${AOSP_ROOT}/external/avb/avbtool.py" "${AOSP_ROOT}/external/avb/avbtool" avbtool; do
    [ -f "$c" ] && { AVBTOOL="$c"; break; }
    command -v "$c" >/dev/null 2>&1 && { AVBTOOL="$c"; break; }
  done
fi
[ -n "${AVBTOOL}" ] || die "avbtool not found (build it or pass --avbtool)"

echo "==> AVB-signing the patched image with the Circle key"
case "${AVBTOOL}" in
  *.py) AVB_CMD=(python3 "${AVBTOOL}") ;;
  *)    AVB_CMD=("${AVBTOOL}") ;;
esac
"${AVB_CMD[@]}" add_hash_footer \
  --image "${PATCHED}" \
  --partition_name boot \
  --partition_size "${PART_SIZE}" \
  --key "${AVB_KEY}" \
  --algorithm SHA256_RSA4096

install -m 0644 "${PATCHED}" "${OUT}"
sha256sum "${OUT}" | tee "${OUT}.sha256"

echo ""
echo "✓ patched + Circle-signed boot image:"
echo "    ${OUT}"
echo "    flash with: fastboot flash boot ${OUT}   (unlocked bootloader)"
echo "    verify    : sha256sum -c ${OUT}.sha256"
