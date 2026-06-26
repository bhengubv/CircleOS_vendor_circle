#!/usr/bin/env bash
# Circle OS - sign a release for verifiable, uncensorable distribution (#23).
#
# Produces SHA256SUMS + a detached signature so anyone can verify a download is
# the authentic, untampered Circle OS image regardless of which mirror or peer
# it came from. The signature is over the *hash manifest*, so a malicious mirror
# cannot substitute a backdoored image without failing verification.
#
# Usage:
#   ./sign-release.sh [-k release_key.pem] FILE [FILE...]
#
# The Circle OS release key ships as an Android signing key (PKCS#8 DER, .pk8).
# Convert it to PEM once:
#   openssl pkcs8 -inform DER -nocrypt -in releasekey.pk8 -out release_key.pem
# Publish the matching public key so users can verify:
#   openssl rsa -in release_key.pem -pubout -out circleos-release.pub      # RSA
#   openssl ec  -in release_key.pem -pubout -out circleos-release.pub      # EC
set -euo pipefail

KEY=""
usage() { echo "usage: $0 [-k release_key.pem] FILE [FILE...]"; exit 1; }
while getopts "k:h" o; do
  case "$o" in
    k) KEY="$OPTARG" ;;
    *) usage ;;
  esac
done
shift $((OPTIND - 1))
[ $# -ge 1 ] || usage

for f in "$@"; do
  [ -f "$f" ] || { echo "ERROR: not a file: $f" >&2; exit 1; }
done

echo "==> hashing $# file(s)"
: > SHA256SUMS
for f in "$@"; do
  sha256sum "$f" | tee -a SHA256SUMS
done

if [ -n "$KEY" ]; then
  [ -f "$KEY" ] || { echo "ERROR: key not found: $KEY" >&2; exit 1; }
  echo "==> signing SHA256SUMS with $KEY"
  openssl dgst -sha256 -sign "$KEY" -out SHA256SUMS.sig SHA256SUMS
  echo "    wrote SHA256SUMS.sig"
  echo
  echo "    Publish alongside the image: SHA256SUMS, SHA256SUMS.sig, circleos-release.pub"
  echo "    Downloaders verify with:"
  echo "      sha256sum -c SHA256SUMS"
  echo "      openssl dgst -sha256 -verify circleos-release.pub -signature SHA256SUMS.sig SHA256SUMS"
elif command -v gpg >/dev/null 2>&1; then
  echo "==> no -k key given; signing SHA256SUMS with gpg"
  gpg --armor --detach-sign SHA256SUMS
  echo "    wrote SHA256SUMS.asc"
  echo "    Downloaders verify with:"
  echo "      sha256sum -c SHA256SUMS && gpg --verify SHA256SUMS.asc"
else
  echo "WARN: no -k key and no gpg available - wrote SHA256SUMS only (UNSIGNED)." >&2
  echo "      An unsigned manifest proves integrity but not authenticity. Sign before publishing." >&2
fi
