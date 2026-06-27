#!/usr/bin/env bash
# Circle Layer — assemble the flashable Magisk module zip (#152).
#
# Pulls the built Circle priv-apps into the systemless overlay and packages the
# module. APKs come from an AOSP product out dir (or any dir of APKs passed with
# --apps). Run after a Circle OS build, or point --apps at a directory of APKs.
#
# Usage:
#   build-module.sh [--apps <dir-of-apks>] [--out circle-layer.zip] [--apk APP=/path/App.apk ...]
#
# The default app set is the userspace-deliverable Circle apps; framework pieces
# (mesh service, privacy manager) are NOT in the module — they need the full OS.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MODULE_DIR="${SCRIPT_DIR}/circle-layer"
OUT="circle-layer.zip"
APPS_DIR=""
declare -a EXTRA_APKS=()

# Userspace-deliverable Circle apps (overlaid into /system/priv-app/<App>/<App>.apk).
DEFAULT_APPS="CircleMe CircleRooms CircleMail CircleMessages CirclePhotos CircleNotes CircleMaps Butler"

die() { echo "ERROR: $*" >&2; exit 1; }

while [ $# -gt 0 ]; do
  case "$1" in
    --apps) APPS_DIR="${2:-}"; shift 2 ;;
    --out)  OUT="${2:-}"; shift 2 ;;
    --apk)  EXTRA_APKS+=("${2:-}"); shift 2 ;;
    -h|--help) sed -n '2,14p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) die "unknown arg: $1" ;;
  esac
done

command -v zip >/dev/null 2>&1 || die "zip is required"

# Resolve the output to an absolute path now, before we cd into the staging dir.
case "${OUT}" in
  /*) OUT_ABS="${OUT}" ;;
  *)  OUT_ABS="${PWD}/${OUT}" ;;
esac

STAGE="$(mktemp -d)"
trap 'rm -rf "${STAGE}"' EXIT
cp -a "${MODULE_DIR}/." "${STAGE}/"
mkdir -p "${STAGE}/system/priv-app"

place_apk() {
  local name="$1" apk="$2"
  [ -f "${apk}" ] || { echo "  skip ${name} (no apk: ${apk})"; return; }
  mkdir -p "${STAGE}/system/priv-app/${name}"
  cp "${apk}" "${STAGE}/system/priv-app/${name}/${name}.apk"
  echo "  + ${name}"
}

echo "==> staging Circle apps"
if [ -n "${APPS_DIR}" ]; then
  [ -d "${APPS_DIR}" ] || die "no such apps dir: ${APPS_DIR}"
  for name in ${DEFAULT_APPS}; do
    apk="$(find "${APPS_DIR}" -name "${name}.apk" 2>/dev/null | head -1)"
    [ -n "${apk}" ] && place_apk "${name}" "${apk}" || echo "  skip ${name} (not found)"
  done
fi
for pair in "${EXTRA_APKS[@]:-}"; do
  [ -z "${pair}" ] && continue
  place_apk "${pair%%=*}" "${pair#*=}"
done

# Keep the dir present even if no apps were staged (scaffold remains flashable).
touch "${STAGE}/system/priv-app/.placeholder"

echo "==> packaging ${OUT_ABS}"
rm -f "${OUT_ABS}"
( cd "${STAGE}" && zip -r9 -X "${OUT_ABS}" . -x '.git*' >/dev/null )
echo "✓ ${OUT_ABS}"
echo "  flash in Magisk Manager, or: magisk --install-module ${OUT_ABS}"
