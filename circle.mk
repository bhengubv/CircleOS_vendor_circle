# Circle OS - Top-level vendor makefile
# Includes all Circle-specific makefiles into the build.

# Core configuration (properties, product packages)
include vendor/circle/config/common.mk

# /data/circle dirs are created by this init script on first boot.
PRODUCT_COPY_FILES += \
    vendor/circle/etc/init/circle_init.rc:$(TARGET_COPY_OUT_SYSTEM)/etc/init/circle_init.rc
