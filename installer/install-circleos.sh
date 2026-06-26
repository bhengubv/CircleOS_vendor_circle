#!/usr/bin/env bash
# Circle OS — desktop DSU installer (#11, achievable form)
#
# Installs the Circle OS GSI onto a connected Android device as a Dynamic
# System (DSU) — dual-boot, zero-risk, no bootloader unlock, no wipe.
# The next reboot returns to stock Android untouched.
#
# REALITY: Android guards DSU install with INSTALL_DYNAMIC_SYSTEM
# (signature|privileged). A sideloaded *app* can never trigger it on stock.
# The two paths that work are (1) this adb-driven desktop installer, and
# (2) Settings > Developer options > DSU loader. This script is path (1):
# one click on the PC, for any device the user can put in USB debugging.
#
# Usage:  ./install-circleos.sh [circleos_gsi.img.gz] [userdata_GiB]
# Default GSI: ./circleos-gsi.img.gz   Default userdata: 8 GiB
set -euo pipefail

GSI="${1:-circleos-gsi.img.gz}"
USERDATA_GIB="${2:-8}"
DSU_COMPONENT="com.android.dynsystem/com.android.dynsystem.VerificationActivity"

say() { printf '\033[1;34m[circleos]\033[0m %s\n' "$*"; }
err() { printf '\033[1;31m[circleos] ERROR:\033[0m %s\n' "$*" >&2; }

# --- preflight -------------------------------------------------------------
command -v adb >/dev/null 2>&1 || { err "adb not found. Install Android platform-tools and retry."; exit 1; }
[ -f "$GSI" ] || { err "GSI not found: $GSI  (pass the path as arg 1)"; exit 1; }

say "Waiting for a device in USB debugging (authorize the prompt on the phone)..."
adb start-server >/dev/null 2>&1 || true
adb wait-for-device

STATE="$(adb get-state 2>/dev/null || echo unknown)"
[ "$STATE" = "device" ] || { err "Device not ready (state=$STATE). Enable USB debugging + authorize this computer."; exit 1; }

MODEL="$(adb shell getprop ro.product.model 2>/dev/null | tr -d '\r')"
REL="$(adb shell getprop ro.build.version.release 2>/dev/null | tr -d '\r')"
SDK="$(adb shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r')"
say "Target: ${MODEL:-?}  Android ${REL:-?} (API ${SDK:-?})"
if [ -n "$SDK" ] && [ "$SDK" -lt 29 ] 2>/dev/null; then
  err "DSU needs Android 10 (API 29)+. This device is API $SDK — use the hard-mode install path instead."
  exit 1
fi

# system size = uncompressed size of the GSI (what DSU allocates for /system)
if gzip -t "$GSI" >/dev/null 2>&1; then
  SYS_BYTES="$(gzip -l "$GSI" | awk 'NR==2{print $2}')"
else
  SYS_BYTES="$(stat -c%s "$GSI" 2>/dev/null || stat -f%z "$GSI")"
fi
UD_BYTES="$(( USERDATA_GIB * 1024 * 1024 * 1024 ))"
say "GSI uncompressed: $(( SYS_BYTES / 1024 / 1024 )) MiB   userdata: ${USERDATA_GIB} GiB"

# --- push + trigger DSU ----------------------------------------------------
REMOTE="/data/local/tmp/circleos-gsi.img.gz"
say "Pushing GSI to device (this can take a few minutes)..."
adb push "$GSI" "$REMOTE"

say "Requesting Dynamic System install..."
OUT="$(adb shell am start-activity \
  -n "$DSU_COMPONENT" \
  -a android.os.image.action.START_INSTALL \
  -d "file://$REMOTE" \
  --el KEY_SYSTEM_SIZE "$SYS_BYTES" \
  --el KEY_USERDATA_SIZE "$UD_BYTES" 2>&1 | tr -d '\r')"
echo "$OUT"

if echo "$OUT" | grep -qiE 'Permission Denial|SecurityException|requires .*INSTALL_DYNAMIC_SYSTEM'; then
  err "The device blocked the DSU trigger (INSTALL_DYNAMIC_SYSTEM)."
  cat <<'EOF'
  This phone's build won't let adb start a DSU install directly. Use the
  on-device path instead:
    Settings > About phone > tap Build number 7x (enable Developer options)
    Settings > System > Developer options > DSU Loader  (or "Dynamic System Updates")
    Pick the Circle OS image, confirm, and reboot.
  If DSU Loader isn't listed, the OEM disabled it — use the hard-mode path (#12).
EOF
  exit 2
fi

say "DSU install requested. On the phone: confirm the dialog, wait for it to finish,"
say "then tap RESTART (or run:  adb reboot ) to boot Circle OS."
say "To return to stock Android at any time, just reboot normally."
