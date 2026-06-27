#!/usr/bin/env bash
# Circle OS — signed BitTorrent distribution for release images (#23 / SOS-B6).
#
# Uncensorable distribution: a Circle OS image is shared peer-to-peer over
# BitTorrent. The torrent's infohash already pins the exact bytes, and we pair it
# with the detached release signature over SHA256SUMS (see ../sign-release.sh), so
# a downloader can prove the image is the authentic, untampered Circle OS build no
# matter which peer or mirror it came from — no central, blockable download host.
#
# Produces: <image>.torrent + a magnet link + a verify.txt cheat-sheet. Adds the
# CDN URL as a BitTorrent web seed so the swarm bootstraps even with zero peers.
#
# Usage:
#   make-torrent.sh -i circleos-0.1.0.img \
#                   [-u https://cdn.thegeek.co.za/circleos/0.1.0/circleos-0.1.0.img] \
#                   [-s SHA256SUMS.sig] [-k circleos-release.pub] [-o outdir]
#
# Requires: mktorrent (or transmission-create). Trackers default to a public set;
# override with -t. The signature/pubkey are produced by sign-release.sh.
set -euo pipefail

IMAGE=""
WEBSEED=""
SIG=""
PUB=""
OUT="."
TRACKERS_DEFAULT=(
  "udp://tracker.opentrackr.org:1337/announce"
  "udp://open.tracker.cl:1337/announce"
  "udp://tracker.openbittorrent.com:6969/announce"
)
declare -a TRACKERS=()

die() { echo "ERROR: $*" >&2; exit 1; }
usage() { sed -n '2,20p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 1; }

while getopts "i:u:s:k:o:t:h" o; do
  case "$o" in
    i) IMAGE="$OPTARG" ;;
    u) WEBSEED="$OPTARG" ;;
    s) SIG="$OPTARG" ;;
    k) PUB="$OPTARG" ;;
    o) OUT="$OPTARG" ;;
    t) TRACKERS+=("$OPTARG") ;;
    *) usage ;;
  esac
done

[ -n "${IMAGE}" ] || usage
[ -f "${IMAGE}" ] || die "no such image: ${IMAGE}"
mkdir -p "${OUT}"
[ ${#TRACKERS[@]} -gt 0 ] || TRACKERS=("${TRACKERS_DEFAULT[@]}")

base="$(basename "${IMAGE}")"
torrent="${OUT}/${base}.torrent"

echo "==> hashing image"
img_sha="$(sha256sum "${IMAGE}" | awk '{print $1}')"

echo "==> creating ${torrent}"
if command -v mktorrent >/dev/null 2>&1; then
  args=(-l 20 -o "${torrent}")          # 1 MiB pieces
  for t in "${TRACKERS[@]}"; do args+=(-a "${t}"); done
  [ -n "${WEBSEED}" ] && args+=(-w "${WEBSEED}")
  args+=(-c "Circle OS release ${base} — sha256:${img_sha}")
  rm -f "${torrent}"
  mktorrent "${args[@]}" "${IMAGE}"
elif command -v transmission-create >/dev/null 2>&1; then
  args=(-o "${torrent}")
  for t in "${TRACKERS[@]}"; do args+=(-t "${t}"); done
  [ -n "${WEBSEED}" ] && args+=(-w "${WEBSEED}")
  args+=(-c "Circle OS release ${base} — sha256:${img_sha}")
  transmission-create "${args[@]}" "${IMAGE}"
else
  die "need mktorrent or transmission-create to build the .torrent"
fi

# Best-effort magnet link (infohash via transmission-show if available).
magnet=""
if command -v transmission-show >/dev/null 2>&1; then
  ih="$(transmission-show "${torrent}" 2>/dev/null | awk -F': ' '/Hash:/{print $2; exit}')"
  if [ -n "${ih}" ]; then
    magnet="magnet:?xt=urn:btih:${ih}&dn=$(printf '%s' "${base}" | sed 's/ /%20/g')"
    for t in "${TRACKERS[@]}"; do magnet="${magnet}&tr=${t}"; done
    [ -n "${WEBSEED}" ] && magnet="${magnet}&ws=${WEBSEED}"
  fi
fi

cat > "${OUT}/verify-${base}.txt" <<TXT
Circle OS — verify this download is authentic
==============================================

1. The .torrent / magnet infohash guarantees you received the EXACT bytes.
2. Confirm the image hash:
     sha256sum ${base}
     # expect: ${img_sha}
3. Confirm authenticity against the Circle release key:
     sha256sum -c SHA256SUMS
     openssl dgst -sha256 -verify ${PUB:-circleos-release.pub} -signature ${SIG:-SHA256SUMS.sig} SHA256SUMS

If step 3 fails, a mirror or peer tampered with the image — discard it.
${magnet:+
Magnet:
  ${magnet}}
TXT

echo ""
echo "✓ torrent : ${torrent}"
[ -n "${magnet}" ] && echo "✓ magnet  : ${magnet}"
echo "✓ verify  : ${OUT}/verify-${base}.txt"
echo "  image sha256: ${img_sha}"
echo ""
echo "Seed it:  transmission-cli ${torrent}   (or any client; keep a seedbox + the CDN web seed up)"
