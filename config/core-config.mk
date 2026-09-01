# Circle OS - form-factor-NEUTRAL config (props, sepolicy, network, media, VINTF,
# update engine). NO application packages here -- those live in packages-*.mk so
# each device class pulls only the apps it needs. Split out of common.mk (Phase 1
# of one-codebase/every-device). common.mk still yields the identical phone set by
# including this + packages-core.mk + packages-phone.mk.

PRODUCT_SYSTEM_PROPERTIES += \
    ro.circle.version=0.1.0-alpha \
    ro.circle.build.type=userdebug \
    ro.circleos.update.url=https://ota.circleos.co.za \
    ro.circleos.channel=stable

# A/B update engine (every Circle target)
PRODUCT_PACKAGES += \
    update_engine \
    update_verifier \
    update_engine_client

PRODUCT_BUILD_RECOVERY_IMAGE := false

SYSTEM_EXT_PRIVATE_SEPOLICY_DIRS += vendor/circle/sepolicy

# DoH + Quad9 defaults
PRODUCT_SYSTEM_PROPERTIES += \
    net.dns1=9.9.9.9 \
    net.dns2=149.112.112.112 \
    ro.circleos.private_dns_mode=hostname \
    ro.circleos.private_dns_specifier=dns.quad9.net

PRODUCT_COPY_FILES += \
    vendor/circle/bootanimation/bootanimation.zip:$(TARGET_COPY_OUT_PRODUCT)/media/bootanimation.zip

PRODUCT_COPY_FILES += \
    vendor/circle/wallpapers/default_dark.png:$(TARGET_COPY_OUT_PRODUCT)/media/wallpaper/default_dark.png \
    vendor/circle/wallpapers/default_light.png:$(TARGET_COPY_OUT_PRODUCT)/media/wallpaper/default_light.png \
    vendor/circle/wallpapers/mesh_dark.png:$(TARGET_COPY_OUT_PRODUCT)/media/wallpaper/mesh_dark.png

PRODUCT_COPY_FILES += \
    vendor/circle/config/endpoints.json:$(TARGET_COPY_OUT_SYSTEM)/etc/circle/endpoints.json

DEVICE_MANIFEST_FILE += vendor/circle/vintf/manifest.xml
