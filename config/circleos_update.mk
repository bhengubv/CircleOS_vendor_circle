# CircleOS OTA Update Service — build properties
# Included from circle.mk or device product config.
#
# ro.circleos.update.url — base URL for the SleptOnAPI update endpoint
# ro.circleos.channel    — default update channel; user can override via setChannel()

PRODUCT_PROPERTY_OVERRIDES += \
    ro.circleos.update.url=https://sleptonapi.thegeeknetwork.co.za \
    ro.circleos.channel=stable
