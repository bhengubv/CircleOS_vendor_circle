#!/bin/bash
#
# CircleOS Release Publisher
#
# Signs the build manifest and publishes a CircleOS OTA release to the SleptOn API.
#
# Usage:
#   ./vendor/circle/release/publish_release.sh \
#       --device   pixel6 \
#       --version  0.1.0 \
#       --channel  alpha \
#       --payload  release/0.1.0/pixel6-20260220/payload.bin \
#       --key      /path/to/circleos_ota_private.pem
#
# Required env:
#   SLEPTON_CIRCLEOS_API_KEY — API key with os:publish scope
#                              (seeded in migration 041: slp_cos_CircleOS2026OtaPublish)
#
# Required tools: curl, openssl
#

set -euo pipefail

# ── Argument parsing ──────────────────────────────────────────────────────────
DEVICE=""
VERSION=""
CHANNEL="alpha"
PAYLOAD=""
PRIVATE_KEY=""
ROLLOUT_PERCENT=5          # Start alpha at 5% — staged rollout ladder
MIN_VERSION="0.0.0"
RELEASE_NOTES=""

API_BASE="${CIRCLEOS_OTA_API:-https://ota.circleos.co.za}"

usage() {
    echo "Usage: $0 --device <device> --version <version> --payload <payload.bin> --key <private.pem>"
    echo "  --device    pixel6 | redmi_note12"
    echo "  --version   semver, e.g. 0.1.0"
    echo "  --channel   alpha | beta | nightly | stable  (default: alpha)"
    echo "  --payload   path to payload.bin"
    echo "  --key       path to RSA private key (PEM)"
    echo "  --rollout   rollout percent 1-100 (default: 5)"
    echo "  --notes     release notes text (optional)"
    exit 1
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --device)   DEVICE="$2";           shift 2 ;;
        --version)  VERSION="$2";          shift 2 ;;
        --channel)  CHANNEL="$2";          shift 2 ;;
        --payload)  PAYLOAD="$2";          shift 2 ;;
        --key)      PRIVATE_KEY="$2";      shift 2 ;;
        --rollout)  ROLLOUT_PERCENT="$2";  shift 2 ;;
        --notes)    RELEASE_NOTES="$2";    shift 2 ;;
        *)          usage ;;
    esac
done

# ── Validate inputs ───────────────────────────────────────────────────────────
[[ -z "$DEVICE"      ]] && { echo "ERROR: --device required";  usage; }
[[ -z "$VERSION"     ]] && { echo "ERROR: --version required"; usage; }
[[ -z "$PAYLOAD"     ]] && { echo "ERROR: --payload required"; usage; }
[[ -z "$PRIVATE_KEY" ]] && { echo "ERROR: --key required";     usage; }

[[ -f "$PAYLOAD"     ]] || { echo "ERROR: payload not found: $PAYLOAD";     exit 1; }
[[ -f "$PRIVATE_KEY" ]] || { echo "ERROR: private key not found: $PRIVATE_KEY"; exit 1; }

if [[ -z "${SLEPTON_CIRCLEOS_API_KEY:-}" ]]; then
    echo "ERROR: SLEPTON_CIRCLEOS_API_KEY environment variable is not set."
    echo "  Export it with: export SLEPTON_CIRCLEOS_API_KEY=slp_cos_CircleOS2026OtaPublish"
    exit 1
fi

# Validate semver
if ! echo "$VERSION" | grep -qE '^\d+\.\d+\.\d+$'; then
    echo "ERROR: version must be semver (e.g. 0.1.0), got: $VERSION"
    exit 1
fi

PAYLOAD_SIZE=$(wc -c < "$PAYLOAD")

echo "╔══════════════════════════════════════════════════╗"
echo "║   CircleOS Release Publisher                     ║"
echo "║   Device  : ${DEVICE}                            ║"
echo "║   Version : ${VERSION} (${CHANNEL})              ║"
echo "║   Payload : $(basename "$PAYLOAD") ($(numfmt --to=iec "$PAYLOAD_SIZE"))   ║"
echo "║   Rollout : ${ROLLOUT_PERCENT}%                  ║"
echo "╚══════════════════════════════════════════════════╝"

# ── Build canonical manifest JSON ─────────────────────────────────────────────
# Must match the format validated by OsReleaseService.PublishReleaseAsync()
CANONICAL_JSON=$(printf '{"version":"%s","channel":"%s","rollout_percent":%s,"min_version":"%s"}' \
    "$VERSION" "$CHANNEL" "$ROLLOUT_PERCENT" "$MIN_VERSION")

echo "Canonical manifest: $CANONICAL_JSON"

# ── RSA-SHA256 sign ───────────────────────────────────────────────────────────
echo "Signing manifest..."
SIGNATURE_B64=$(echo -n "$CANONICAL_JSON" | \
    openssl dgst -sha256 -sign "$PRIVATE_KEY" | \
    openssl base64 -A)

echo "Signature (first 32 chars): ${SIGNATURE_B64:0:32}..."

# ── POST multipart/form-data to API ──────────────────────────────────────────
echo "Publishing to ${API_BASE}/api/os/releases ..."

RESPONSE=$(curl -sS -w "\n%{http_code}" \
    -X POST "${API_BASE}/api/os/releases" \
    -H "X-Api-Key: ${SLEPTON_CIRCLEOS_API_KEY}" \
    -F "version=${VERSION}" \
    -F "channel=${CHANNEL}" \
    -F "rollout_percent=${ROLLOUT_PERCENT}" \
    -F "rollout_strategy=device_hash" \
    -F "min_version=${MIN_VERSION}" \
    -F "signature_b64=${SIGNATURE_B64}" \
    -F "release_notes=${RELEASE_NOTES}" \
    -F "file=@${PAYLOAD};type=application/octet-stream")

HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | head -n -1)

if [[ "$HTTP_CODE" == "201" ]]; then
    RELEASE_ID=$(echo "$BODY" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
    echo ""
    echo "✓ Release published successfully!"
    echo "  Release ID : ${RELEASE_ID}"
    echo "  Version    : ${VERSION} (${CHANNEL})"
    echo "  Rollout    : ${ROLLOUT_PERCENT}% → auto-advances via staged rollout ladder"
    echo ""
    echo "Next steps:"
    echo "  Monitor rollout: GET ${API_BASE}/api/os/rollout/${RELEASE_ID}"
    echo "  Advance stage:  POST ${API_BASE}/api/os/rollout/${RELEASE_ID}/advance"
else
    echo ""
    echo "ERROR: API returned HTTP ${HTTP_CODE}"
    echo "Response: ${BODY}"
    exit 1
fi
