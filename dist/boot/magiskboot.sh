#!/usr/bin/env bash
# Circle OS — magiskboot wrapper for the OTA / boot-signing pipeline (#151).
#
# magiskboot (from our fork, github.com/bhengubv/magisk) unpacks and repacks
# Android boot images. Circle OS uses it to (a) sanity-verify the boot image the
# AOSP build produced before an OTA ships, and (b) produce a Magisk-patched
# boot.img for the unlocked-bootloader install path (see patch-boot.sh).
# magiskboot does NOT sign — boot images are AVB-signed separately with the
# Circle release key.
#
# magiskboot is located in this order:
#   1. $CIRCLE_MAGISKBOOT
#   2. vendor/circle/prebuilt/magiskboot/magiskboot   (run fetch-magiskboot.sh)
#   3. magiskboot on $PATH
# It must be a *host* (Linux) build of magiskboot, not the on-device binary.
#
# Usage:
#   magiskboot.sh path                      # print the resolved magiskboot path
#   magiskboot.sh inspect <boot.img>        # unpack + print header / kernel / ramdisk
#   magiskboot.sh verify  <boot.img>        # exit 0 if a valid boot image, else non-zero
#   magiskboot.sh unpack  <boot.img> <dir>  # unpack components into <dir>
#   magiskboot.sh repack  <boot.img> <out>  # repack from <cwd> pieces into <out>
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# vendor/circle/dist/boot -> vendor/circle
CIRCLE_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
PREBUILT="${CIRCLE_ROOT}/prebuilt/magiskboot/magiskboot"

die() { echo "ERROR: $*" >&2; exit 1; }

resolve_magiskboot() {
  if [ -n "${CIRCLE_MAGISKBOOT:-}" ] && [ -x "${CIRCLE_MAGISKBOOT}" ]; then
    echo "${CIRCLE_MAGISKBOOT}"; return 0
  fi
  if [ -x "${PREBUILT}" ]; then
    echo "${PREBUILT}"; return 0
  fi
  if command -v magiskboot >/dev/null 2>&1; then
    command -v magiskboot; return 0
  fi
  die "magiskboot not found. Set \$CIRCLE_MAGISKBOOT, run fetch-magiskboot.sh, or add it to PATH."
}

usage() {
  sed -n '2,30p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
  exit 1
}

[ $# -ge 1 ] || usage
CMD="$1"; shift

MB="$(resolve_magiskboot)"

case "${CMD}" in
  path)
    echo "${MB}"
    ;;

  unpack)
    [ $# -eq 2 ] || usage
    IMG="$1"; DEST="$2"
    [ -f "${IMG}" ] || die "no such boot image: ${IMG}"
    mkdir -p "${DEST}"
    IMG_ABS="$(cd "$(dirname "${IMG}")" && pwd)/$(basename "${IMG}")"
    ( cd "${DEST}" && "${MB}" unpack "${IMG_ABS}" )
    echo "unpacked ${IMG} -> ${DEST}/"
    ;;

  inspect)
    [ $# -eq 1 ] || usage
    IMG="$1"
    [ -f "${IMG}" ] || die "no such boot image: ${IMG}"
    IMG_ABS="$(cd "$(dirname "${IMG}")" && pwd)/$(basename "${IMG}")"
    TMP="$(mktemp -d)"
    trap 'rm -rf "${TMP}"' EXIT
    echo "==> magiskboot header for $(basename "${IMG}")"
    # magiskboot prints HEADER_VER / KERNEL_SZ / RAMDISK_SZ / OS_VERSION etc. on unpack.
    ( cd "${TMP}" && "${MB}" unpack "${IMG_ABS}" ) || die "unpack failed — not a valid boot image?"
    echo "==> components"
    ( cd "${TMP}" && ls -l kernel ramdisk.cpio dtb 2>/dev/null || ls -l )
    if [ -f "${TMP}/ramdisk.cpio" ]; then
      echo "==> ramdisk entries (top level)"
      ( cd "${TMP}" && "${MB}" cpio ramdisk.cpio "ls" 2>/dev/null | head -30 || true )
    fi
    ;;

  verify)
    [ $# -eq 1 ] || usage
    IMG="$1"
    [ -f "${IMG}" ] || die "no such boot image: ${IMG}"
    IMG_ABS="$(cd "$(dirname "${IMG}")" && pwd)/$(basename "${IMG}")"
    TMP="$(mktemp -d)"
    trap 'rm -rf "${TMP}"' EXIT
    if ( cd "${TMP}" && "${MB}" unpack "${IMG_ABS}" >/dev/null 2>&1 ) && [ -f "${TMP}/kernel" -o -f "${TMP}/ramdisk.cpio" ]; then
      echo "OK: ${IMG} is a valid boot image"
    else
      die "INVALID: ${IMG} did not unpack as a boot image"
    fi
    ;;

  repack)
    [ $# -eq 2 ] || usage
    ORIG="$1"; OUT="$2"
    [ -f "${ORIG}" ] || die "no such original boot image: ${ORIG}"
    "${MB}" repack "${ORIG}" "${OUT}"
    echo "repacked -> ${OUT}"
    ;;

  *)
    usage
    ;;
esac
