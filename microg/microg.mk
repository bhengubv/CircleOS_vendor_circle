#
# CircleOS -- microG Integration
#
# microG is an open-source re-implementation of Google Play Services.
# It provides app compatibility (push notifications, maps, auth) without
# sending data to Google.
#
# CircleOS ships microG as the default GMS replacement.
# Users can disable it entirely via CircleSettings > Privacy > microG.
#

# Enable restricted signature spoofing (only microG packages may spoof).
# Patched into frameworks/base -- not available to arbitrary apps.
PRODUCT_SYSTEM_PROPERTIES +=     ro.microg.enabled=true     ro.circle.microg.signature_spoof=restricted

# microG core components shipped today as prebuilts under
# vendor/circle/microg/prebuilt/. GsfProxy / FakeStore / Ichnaea /
# Nominatim are optional follow-ups -- the alpha-1 build ships
# GmsCore and FDroid only.
PRODUCT_PACKAGES +=     GmsCore     FDroid

# microG permissions (allow signature-spoof, allow background data).
PRODUCT_COPY_FILES +=     vendor/circle/microg/permissions/microg_permissions.xml:$(TARGET_COPY_OUT_SYSTEM)/etc/permissions/microg_permissions.xml

# Disable proprietary GMS routing on top of the degoogle.mk overlay.
PRODUCT_SYSTEM_PROPERTIES +=     ro.gsf.enabled=false
