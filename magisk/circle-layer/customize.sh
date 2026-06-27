#!/system/bin/sh
# Circle Layer — install-time customization (#152).
# Magisk sources this with $MODPATH, $API, $ARCH, $IS64BIT, ui_print, abort,
# set_perm, set_perm_recursive already defined.

SKIPUNZIP=0

ui_print "- Circle Layer v0.1.0-alpha"

# Circle apps target modern APIs; require Android 13 (API 33)+.
if [ "$API" -lt 33 ]; then
  abort "! Circle Layer needs Android 13 (API 33) or newer (found API $API)"
fi

# Circle is 64-bit only (ART, AetherNet native libs).
if [ "$IS64BIT" != "true" ]; then
  abort "! Circle Layer requires a 64-bit device"
fi

ui_print "- Device: API $API, ARCH $ARCH"
ui_print "- Installing Circle apps + props systemlessly"

# Standard perms for the systemless overlay.
set_perm_recursive "$MODPATH/system" 0 0 0755 0644

# priv-app entries need to be traversable.
if [ -d "$MODPATH/system/priv-app" ]; then
  set_perm_recursive "$MODPATH/system/priv-app" 0 0 0755 0644
fi

ui_print "- Done. Reboot to activate the Circle Layer."
ui_print "  Framework features (mesh, privacy manager) need the full Circle OS."
