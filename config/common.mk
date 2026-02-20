# Circle OS - Common product configuration
# Verifiable: adb shell getprop ro.circle.version → 0.1.0-alpha

PRODUCT_PROPERTY_OVERRIDES += \
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
    InferenceBridge \
    PersonalityEditor \
    PersonalityTile \
    HomeCinema \
    SdpktTitanium

# A/B update engine (common to all Circle targets)
PRODUCT_PACKAGES += \
    update_engine \
    update_verifier \
    update_engine_client

# No separate recovery partition — A/B boots directly from the inactive slot
PRODUCT_BUILD_RECOVERY_IMAGE := false

# SELinux policy extensions
BOARD_SEPOLICY_DIRS += vendor/circle/sepolicy
