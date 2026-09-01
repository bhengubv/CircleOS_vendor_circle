#!/usr/bin/env bash
# Circle OS — skin engine walking skeleton (colour seam).
#
# Emits a Circle skin's accent as a FabricatedOverlay (the real engine
# mechanism) AND sets the Material You seed, then enables it — live, no reflash.
# Proves seam (1) System theming from CircleOS_Skin_Architecture.
#
# Target surfaces (where the shell may drive OverlayManager):
#   Circle OS (platform) · AOSP userdebug · an emulator · rooted device.
#   NOT stock MIUI/EMUI — they lock overlays and suppress Material You.
#
# Usage:  ./circle_skin_apply.sh <serial> halo|field|metro|revert
#   (family colours are identical; shape/type/layout are later rungs — see the spec.)
set -uo pipefail
S="${1:?serial (adb -s)}"; SKIN="${2:?halo|field|metro|revert}"
A(){ adb -s "$S" shell "$@"; }
OV=CircleSkin
ACCENT=2196F3   # Circle Blue — the one accent across the whole family

seed_and_fabricate(){
  # 1) Material You seed — regenerate Monet from Circle Blue (no root)
  A "settings put secure theme_customization_overlay_packages '{\"android.theme.customization.system_palette\":\"$ACCENT\",\"android.theme.customization.color_source\":\"preset\",\"android.theme.customization.theme_style\":\"TONAL_SPOT\"}'"
  # 2) FabricatedOverlay — override framework accent slots precisely (the engine path)
  for r in system_accent1_500 system_accent1_600 system_accent1_400; do
    A "cmd overlay fabricate --target android --name $OV android:color/$r 0xFF$ACCENT" 2>/dev/null || true
  done
  A "cmd overlay enable --user 0 com.android.shell:$OV" 2>/dev/null || A "cmd overlay enable --user 0 $OV" 2>/dev/null || true
}

case "$SKIN" in
  halo|field|metro) seed_and_fabricate; echo "applied $SKIN (Circle Blue #$ACCENT) to $S — seed + fabricated overlay";;
  revert) A "cmd overlay disable --user 0 com.android.shell:$OV" 2>/dev/null || true
          A "settings delete secure theme_customization_overlay_packages" || true; echo "reverted $S";;
  *) echo "usage: $0 <serial> halo|field|metro|revert"; exit 2;;
esac
