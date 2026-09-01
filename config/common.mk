# Circle OS - Common product configuration.
# Phase 1 (one-codebase/every-device): this file now COMPOSES the neutral config
# + the core app group + the phone app group. The union equals the exact package
# set from before the split, so circle_base / circle_arm64 / circle_emulator are
# byte-identical. Form-factor products (circle_tablet/tv/wear/desktop) instead
# compose core-config + packages-core + their own group.
include vendor/circle/config/core-config.mk
include vendor/circle/config/packages-core.mk
include vendor/circle/config/packages-phone.mk
