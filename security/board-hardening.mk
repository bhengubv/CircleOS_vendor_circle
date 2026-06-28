# Circle OS — board-level security hardening. Include from the device BoardConfig.mk:
#   -include vendor/circle/security/board-hardening.mk
#
# These are the well-established GrapheneOS-style kernel mitigations. The device
# kernel must support them (mainline/ACK do). Safe to carry as defaults; remove a
# flag only if a specific device's kernel rejects it.

BOARD_KERNEL_CMDLINE += \
    init_on_alloc=1 \
    init_on_free=1 \
    slab_nomerge \
    page_alloc.shuffle=1 \
    randomize_kstack_offset=on \
    pti=on \
    vsyscall=none

# Circle hardening SELinux rules live here (kept empty until each rule is
# build-verified — a blind neverallow fails the whole build).
BOARD_SEPOLICY_DIRS += vendor/circle/security/sepolicy
