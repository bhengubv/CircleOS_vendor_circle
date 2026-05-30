#!/bin/bash
# Circle OS — vendor-side lunch combo registration shim.
#
# AOSP's build/envsetup.sh only auto-sources vendorsetup.sh files found
# under device/, vendor/, or product/ (see envsetup.sh
# `source_vendorsetup` and its 4-deep find of those three roots).
# build/circle/vendorsetup.sh is therefore not picked up automatically —
# we relay through here so the lunch combos defined there land in the
# menu without anyone having to source build/circle/vendorsetup.sh by
# hand.
#
# The real list of add_lunch_combo invocations lives in
# build/circle/vendorsetup.sh, alongside the circle_arm64 /
# circle_redmi_note12 / circle_pixel6 / circle_emulator product mks.
# Keep this file as a one-line shim; do not duplicate the lunch
# definitions here.

BUILD_TOP="${ANDROID_BUILD_TOP:-$(pwd)}"
if [ -f "${BUILD_TOP}/build/circle/vendorsetup.sh" ]; then
    source "${BUILD_TOP}/build/circle/vendorsetup.sh"
fi
