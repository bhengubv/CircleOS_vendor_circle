# Circle OS - Common product configuration
# Verifiable: adb shell getprop ro.circle.version → 0.1.0-alpha
#
# Properties are labelled by vendor/circle/sepolicy/property_contexts
# which is in system_ext (see SYSTEM_EXT_PRIVATE_SEPOLICY_DIRS below)
# rather than vendor — so the OEM brand prefix `ro.circle.*` is fine.

# Round 21: PRODUCT_SYSTEM_PROPERTIES instead of legacy
# PRODUCT_PROPERTY_OVERRIDES. Round 20 (442644e) labelled
# ro.circle.* / ro.circleos.* as system_property_type via
# system_public_prop(circle_prop), so writes target /system/build.prop
# via the partition-specific PRODUCT_SYSTEM_PROPERTIES variable.
PRODUCT_SYSTEM_PROPERTIES += \
    ro.circle.version=0.1.0-alpha \
    ro.circle.build.type=userdebug \
    ro.circleos.update.url=https://ota.circleos.co.za \
    ro.circleos.channel=stable

# Circle OS applications
PRODUCT_PACKAGES += \
    CircleOsSettings \
    TrafficLobby \
    Butler \
    CircleMessages \
    CircleNotes \
    CirclePeople \
    CirclePodcasts \
    CircleReminders \
    CircleReader \
    CircleFindMy \
    CircleKids \
    CircleMe \
    CircleRooms \
    InferenceBridge \
    PersonalityEditor \
    PersonalityTile \
    SdpktTitanium \
    CircleSetupWizard \
    CircleSystemUIOverlay \
    CircleLockscreenOverlay \
    HomeCinema \
    AetherHandler \
    CircleWallpaperOverlay \
    Panik \
    TheJobCenter \
    TrustSeal \
    BidBaas \
    SleptOn \
    Bruh \
    WhatWeWant \
    Takemehome \
    TagMe \
    CircleMaps

# A/B update engine (common to all Circle targets)
PRODUCT_PACKAGES += \
    update_engine \
    update_verifier \
    update_engine_client

# No separate recovery partition — A/B boots directly from the inactive slot
PRODUCT_BUILD_RECOVERY_IMAGE := false

# SELinux policy extensions
#
# Lives in system_ext (not vendor) because the policy grants
# system_server (a system platform domain) access to circle_*_data_file
# types under /data/system/ and registers circle.* binder services that
# system_server owns. vendor_property_contexts / vendor_service_contexts
# / vendor_file_contexts all enforce a `vendor_`/`odm_` context-prefix
# namespace check that our `circle_*` and `circle.*` names don't fit;
# system_ext is the correct architectural home for OEM-branded
# system-side sepolicy extensions.
SYSTEM_EXT_PRIVATE_SEPOLICY_DIRS += vendor/circle/sepolicy

# Round 22: DoH + Quad9 as Circle defaults.
# net.dns1/2 set the default resolver IPs; ro.circleos.private_dns_*
# is consumed by CircleSettings BootReceiver which writes them into
# Settings.Global.PRIVATE_DNS_MODE + PRIVATE_DNS_SPECIFIER on first
# boot. Note that PRIVATE_DNS_MODE itself cannot be set via
# build.prop -- it lives in the SecureSettings db and needs a write
# from a privileged caller.
PRODUCT_SYSTEM_PROPERTIES += \
    net.dns1=9.9.9.9 \
    net.dns2=149.112.112.112 \
    ro.circleos.private_dns_mode=hostname \
    ro.circleos.private_dns_specifier=dns.quad9.net

# Circle OS boot animation
PRODUCT_COPY_FILES += \
    vendor/circle/bootanimation/bootanimation.zip:$(TARGET_COPY_OUT_PRODUCT)/media/bootanimation.zip

# Circle OS wallpapers
PRODUCT_COPY_FILES += \
    vendor/circle/wallpapers/default_dark.png:$(TARGET_COPY_OUT_PRODUCT)/media/wallpaper/default_dark.png \
    vendor/circle/wallpapers/default_light.png:$(TARGET_COPY_OUT_PRODUCT)/media/wallpaper/default_light.png \
    vendor/circle/wallpapers/mesh_dark.png:$(TARGET_COPY_OUT_PRODUCT)/media/wallpaper/mesh_dark.png


# Circle OS vendor VINTF manifest (via DEVICE_MANIFEST_FILE, assemble_vintf sets target-level=legacy)
DEVICE_MANIFEST_FILE += vendor/circle/vintf/manifest.xml
