#!/usr/bin/env bash
# Circle OS — install a host build of magiskboot from our Magisk fork (#151).
#
# magiskboot.sh and patch-boot.sh need a *host* (Linux) magiskboot binary. This
# installs one into vendor/circle/prebuilt/magiskboot/magiskboot, two ways:
#
#   fetch-magiskboot.sh --vendor /path/to/host-magiskboot
#       Copy an already-built host binary into the prebuilt dir (reliable path).
#
#   fetch-magiskboot.sh --from-source [--ref <git-ref>] [--src <dir>]
#       Clone our fork (github.com/bhengubv/magisk) and build the magiskboot host
#       binary. Requires the Magisk build prerequisites (Rust toolchain, cmake, a
#       C/C++ toolchain). The fork is OURS; this only builds it, never edits it.
#
# The matching device binaries (magiskinit, magisk) used by patch-boot.sh come
# from the same fork build under prebuilt/magiskboot/ as well.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CIRCLE_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
DEST_DIR="${CIRCLE_ROOT}/prebuilt/magiskboot"
DEST="${DEST_DIR}/magiskboot"

FORK="https://github.com/bhengubv/magisk.git"
DEFAULT_REF="circle"   # our fork's Circle integration branch

die() { echo "ERROR: $*" >&2; exit 1; }

MODE=""
VENDOR_PATH=""
REF="${DEFAULT_REF}"
SRC=""

while [ $# -gt 0 ]; do
  case "$1" in
    --vendor)      MODE="vendor"; VENDOR_PATH="${2:-}"; shift 2 ;;
    --from-source) MODE="source"; shift ;;
    --ref)         REF="${2:-}"; shift 2 ;;
    --src)         SRC="${2:-}"; shift 2 ;;
    -h|--help)     sed -n '2,18p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *)             die "unknown arg: $1" ;;
  esac
done

[ -n "${MODE}" ] || die "choose --vendor <path> or --from-source"
mkdir -p "${DEST_DIR}"

vendor_install() {
  [ -n "${VENDOR_PATH}" ] || die "--vendor needs a path to a host magiskboot binary"
  [ -f "${VENDOR_PATH}" ] || die "no such file: ${VENDOR_PATH}"
  # Sanity: must be a Linux host ELF, not an Android binary.
  if command -v file >/dev/null 2>&1; then
    if ! file "${VENDOR_PATH}" | grep -qiE 'ELF .*(x86-64|aarch64).*(GNU/Linux|SYSV)'; then
      echo "WARN: ${VENDOR_PATH} may not be a Linux host binary — verify it runs on the build host." >&2
    fi
  fi
  install -m 0755 "${VENDOR_PATH}" "${DEST}"
  echo "installed magiskboot -> ${DEST}"
}

source_build() {
  command -v git >/dev/null 2>&1 || die "git required for --from-source"
  local work
  if [ -n "${SRC}" ]; then
    [ -d "${SRC}/.git" ] || die "--src ${SRC} is not a git checkout of the Magisk fork"
    work="${SRC}"
  else
    work="$(mktemp -d)/magisk"
    echo "==> cloning ${FORK} @ ${REF}"
    git clone --depth 1 --branch "${REF}" --recurse-submodules "${FORK}" "${work}"
  fi

  echo "==> building host magiskboot"
  # The fork ships a host build entry point; prefer it, fall back to cargo.
  if [ -x "${work}/build.py" ]; then
    ( cd "${work}" && python3 build.py binary magiskboot --host ) \
      || die "build.py host magiskboot build failed — check Rust/cmake/clang prerequisites"
  else
    command -v cargo >/dev/null 2>&1 || die "cargo (Rust) required to build magiskboot"
    ( cd "${work}/native/src" && cargo build --release --bin magiskboot ) \
      || die "cargo build of magiskboot failed"
  fi

  local built
  built="$(find "${work}" -type f -name magiskboot -perm -u+x 2>/dev/null \
            | grep -E '/(host|release|out)/' | head -1)"
  [ -n "${built}" ] || built="$(find "${work}" -type f -name magiskboot -perm -u+x 2>/dev/null | head -1)"
  [ -n "${built}" ] || die "could not locate the built magiskboot under ${work}"
  install -m 0755 "${built}" "${DEST}"
  echo "installed magiskboot -> ${DEST}"
  echo "    (built from ${FORK} @ ${REF})"
}

case "${MODE}" in
  vendor) vendor_install ;;
  source) source_build ;;
esac

echo "==> verify:"
"${SCRIPT_DIR}/magiskboot.sh" path
