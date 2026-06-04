# CircleOS OTA Update Service — build properties
# Included from circle.mk or device product config.
#
# ro.vendor.circleos.update.url — base URL for the SleptOnAPI update endpoint
# ro.vendor.circleos.channel    — default update channel; user can override
#                                 via setChannel()
#
# ro.vendor.* prefix is required by AOSP's vendor_property_contexts namespace
# check (VTS-enforced).

PRODUCT_PROPERTY_OVERRIDES += \
    ro.vendor.circleos.update.url=https://ota.circleos.co.za \
    ro.vendor.circleos.channel=stable
