#
# CircleOS — microG Integration
#
# microG is an open-source re-implementation of Google Play Services.
# It provides app compatibility (push notifications, maps, auth) without
# sending data to Google.
#
# CircleOS ships microG as the default GMS replacement.
# Users can disable it entirely via CircleSettings > Privacy > microG.
#

# Enable restricted signature spoofing (only microG packages may spoof)
# Patched into frameworks/base — not available to arbitrary apps
PRODUCT_PROPERTY_OVERRIDES += \
    ro.microg.enabled=true \
    ro.circle.microg.signature_spoof=restricted

# microG core components (must be built or prebuilt separately)
PRODUCT_PACKAGES += \
    GmsCore \
    GsfProxy \
    FakeStore \
    IchnaeaNlpBackend \
    NominatimGeocoderBackend

# microG permissions
PRODUCT_COPY_FILES += \
    vendor/circle/microg/permissions/microg_permissions.xml:$(TARGET_COPY_OUT_SYSTEM)/etc/permissions/microg_permissions.xml

# Disable proprietary GMS (also done in degoogle.mk, reinforced here)
PRODUCT_PROPERTY_OVERRIDES += \
    ro.gsf.enabled=false

# F-Droid as default open-source app store
PRODUCT_PACKAGES += \
    FDroid
