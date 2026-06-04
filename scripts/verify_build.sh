#!/usr/bin/env bash
# CircleOS Build Verification Script
# Run after a successful build and boot to verify all Circle OS components.
#
# Usage:
#   ./vendor/circle/scripts/verify_build.sh
#
# Requirements:
#   - Device booted and connected via adb
#   - adb in PATH

set -euo pipefail

PASS=0
FAIL=0
RED='\033[0;31m'
GRN='\033[0;32m'
YEL='\033[1;33m'
NC='\033[0m'

pass() { echo -e "${GRN}[PASS]${NC} $1"; ((PASS++)); }
fail() { echo -e "${RED}[FAIL]${NC} $1"; ((FAIL++)); }
info() { echo -e "${YEL}[INFO]${NC} $1"; }

echo ""
echo "========================================"
echo "  CircleOS Build Verification"
echo "========================================"
echo ""

# ---- 1. System properties ----
info "Checking system properties..."

VERSION=$(adb shell getprop ro.circle.version 2>/dev/null | tr -d '\r')
if [[ "$VERSION" == "0.1.0-alpha" ]]; then
    pass "ro.circle.version = $VERSION"
else
    fail "ro.circle.version: expected '0.1.0-alpha', got '$VERSION'"
fi

BUILD_TYPE=$(adb shell getprop ro.circle.build.type 2>/dev/null | tr -d '\r')
if [[ -n "$BUILD_TYPE" ]]; then
    pass "ro.circle.build.type = $BUILD_TYPE"
else
    fail "ro.circle.build.type not set"
fi

# ---- 2. Binder services ----
info "Checking binder services..."

PRIVACY=$(adb shell service check circle.privacy 2>/dev/null | tr -d '\r')
if echo "$PRIVACY" | grep -q "found"; then
    pass "circle.privacy service: found"
else
    fail "circle.privacy service: NOT found"
fi

PERMISSION=$(adb shell service check circle.permission 2>/dev/null | tr -d '\r')
if echo "$PERMISSION" | grep -q "found"; then
    pass "circle.permission service: found"
else
    fail "circle.permission service: NOT found"
fi

# ---- 3. Network default-deny ----
info "Checking network default-deny..."

TEST_PKG="com.android.chrome"
HAS_NET=$(adb shell cmd network_policy is-uid-restricted $(adb shell pm dump $TEST_PKG | grep userId= | head -1 | sed 's/.*userId=//') 2>/dev/null | tr -d '\r' || echo "unknown")
info "Network restriction check for $TEST_PKG: $HAS_NET"

# ---- 4. CircleSettings app ----
info "Checking CircleSettings..."

CS_INSTALLED=$(adb shell pm list packages 2>/dev/null | grep "com.circleos.settings" | tr -d '\r')
if [[ -n "$CS_INSTALLED" ]]; then
    pass "CircleSettings installed: $CS_INSTALLED"
else
    fail "CircleSettings not installed"
fi

# ---- 5. Permissions declared ----
info "Checking Circle OS permissions..."

PERMS=("com.circleos.permission.NETWORK"
       "com.circleos.permission.ACCELEROMETER"
       "com.circleos.permission.GYROSCOPE"
       "com.circleos.permission.BAROMETER"
       "com.circleos.permission.MAGNETOMETER"
       "com.circleos.permission.MANAGE_PRIVACY")

for PERM in "${PERMS[@]}"; do
    RESULT=$(adb shell pm dump "$PERM" 2>/dev/null | grep "declared" | tr -d '\r')
    if [[ -n "$RESULT" ]]; then
        pass "Permission declared: $PERM"
    else
        # Alternative check via dumpsys
        RESULT2=$(adb shell dumpsys package "$PERM" 2>/dev/null | grep -c "protectionLevel" || echo "0")
        if [[ "$RESULT2" -gt 0 ]]; then
            pass "Permission declared: $PERM"
        else
            fail "Permission NOT declared: $PERM"
        fi
    fi
done

# ---- 6. Data directories ----
info "Checking runtime data directories..."

DIRS=("/data/circle" "/data/circle/privacy")
for DIR in "${DIRS[@]}"; do
    EXISTS=$(adb shell "[ -d $DIR ] && echo yes || echo no" 2>/dev/null | tr -d '\r')
    if [[ "$EXISTS" == "yes" ]]; then
        pass "Directory exists: $DIR"
    else
        fail "Directory missing: $DIR"
    fi
done

# ---- 7. Threat intel DB ----
info "Checking threat intelligence database..."
TI_EXISTS=$(adb shell "[ -f /data/circle/threat_intel.db ] && echo yes || echo no" 2>/dev/null | tr -d '\r')
if [[ "$TI_EXISTS" == "yes" ]]; then
    pass "threat_intel.db present"
else
    fail "threat_intel.db missing"
fi

# ---- Summary ----
echo ""
echo "========================================"
echo "  Results: ${PASS} passed, ${FAIL} failed"
echo "========================================"
echo ""

if [[ $FAIL -gt 0 ]]; then
    exit 1
fi
