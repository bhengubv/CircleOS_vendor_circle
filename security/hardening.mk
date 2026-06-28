# Circle OS — userspace security hardening (closes part of the GrapheneOS gap).
# Inherit from the product makefile:
#   $(call inherit-product, vendor/circle/security/hardening.mk)
#
# Pairs with board-hardening.mk (kernel cmdline + SELinux dirs, included from
# BoardConfig.mk). Kept conservative: everything here is either a secure default
# stated explicitly, or guarded so it can't break a build.

# ── Hardened defaults ─────────────────────────────────────────────────────────
# Stated explicitly so a mis-set device can't silently weaken them.
PRODUCT_PRODUCT_PROPERTIES += \
    ro.circle.hardened=1 \
    ro.adb.secure=1

# ── hardened_malloc (GrapheneOS) ──────────────────────────────────────────────
# A hardened heap allocator with slab isolation, guard pages, canaries, and
# zero-on-free — markedly stronger than the stock allocator against heap
# corruption. Built by fetch-hardened-malloc.sh into prebuilt/hardened_malloc.
# Guarded: if the prebuilt isn't present the product still builds (stock Scudo).
ifneq ($(wildcard vendor/circle/prebuilt/hardened_malloc/libhardened_malloc.so),)
PRODUCT_PACKAGES += libhardened_malloc
PRODUCT_PRODUCT_PROPERTIES += ro.circle.hardened_malloc=1
# Preload it ahead of the stock allocator for system + app processes.
PRODUCT_PRODUCT_PROPERTIES += ro.circle.malloc_preload=libhardened_malloc.so
endif

# ── Exploit-surface reduction ─────────────────────────────────────────────────
# Disable the unprivileged-app pool spawning (a known perf-vs-isolation tradeoff;
# Circle chooses isolation), and keep native debugging off in production.
PRODUCT_PRODUCT_PROPERTIES += \
    persist.device_config.runtime_native.usap_pool_enabled=false \
    dalvik.vm.dex2oat-flags=--no-generate-debug-info
