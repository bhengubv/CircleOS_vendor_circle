# Circle OS - Kernel hardening configuration
# Applied to all Circle OS device targets.
# Hardware-specific defconfigs should include circle_kernel.mk.

# ---- Kernel security features ----

# Lock down kernel from userspace (disables dangerous interfaces like /dev/mem)
BOARD_KERNEL_CMDLINE += lockdown=confidentiality

# Kernel Address Space Layout Randomisation (already default but enforce)
BOARD_KERNEL_CMDLINE += kaslr

# Randomise heap allocations
BOARD_KERNEL_CMDLINE += slub_debug=FZP

# Disable kernel modules from being loaded at runtime
BOARD_KERNEL_CMDLINE += module.sig_enforce=1

# ---- Kernel config fragments (applied via KERNEL_CONFIG_FRAGMENTS) ----
# These enable/disable specific CONFIG_ options in the kernel defconfig.

KERNEL_CIRCLE_HARDENING_FRAGMENTS := \
    vendor/circle/kernel/configs/hardening.config

KERNEL_CONFIG_FRAGMENTS += $(KERNEL_CIRCLE_HARDENING_FRAGMENTS)

# ---- ptrace restriction ----
# Only parent processes may trace children (prevents debugger attach attacks)
PRODUCT_PROPERTY_OVERRIDES += \
    ro.kernel.yama.ptrace_scope=2

# ---- Seccomp enforcement ----
PRODUCT_PROPERTY_OVERRIDES += \
    ro.seccomp.enable=true
