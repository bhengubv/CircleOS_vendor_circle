# Circle OS - Common product configuration
# Verifiable: adb shell getprop ro.circle.version → 0.1.0-alpha

PRODUCT_PROPERTY_OVERRIDES += \
    ro.circle.version=0.1.0-alpha \
    ro.circle.build.type=userdebug

# Circle OS applications
PRODUCT_PACKAGES += \
    CircleSettings \
    TrafficLobby \
    Butler \
    InferenceBridge \
    PersonalityEditor \
    PersonalityTile \
    SdpktTitanium

# SELinux policy extensions
BOARD_SEPOLICY_DIRS += vendor/circle/sepolicy
