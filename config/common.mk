# Circle OS - Common product configuration
# Verifiable: adb shell getprop ro.circle.version → 0.1.0-alpha
#
# Properties are labelled by vendor/circle/sepolicy/property_contexts
# which is in system_ext (see SYSTEM_EXT_PRIVATE_SEPOLICY_DIRS below)
# rather than vendor — so the OEM brand prefix `ro.circle.*` is fine.

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
