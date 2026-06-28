# Circle OS — low-level security hardening

Closes part of the gap to GrapheneOS's exploit-mitigation hardening. Three layers,
all conservative (secure defaults or guarded so they can't break a build):

| File | Layer | What it does |
|------|-------|--------------|
| `hardening.mk` | userspace (product) | hardened_malloc as the heap allocator (when built), hardened `ro.adb.secure`/debug defaults, disables the USAP app-spawn pool (isolation over the perf shortcut). |
| `board-hardening.mk` | kernel (BoardConfig) | `init_on_alloc=1 init_on_free=1` (zero memory on alloc+free), `slab_nomerge`, `page_alloc.shuffle=1`, `randomize_kstack_offset=on`, `pti=on`, `vsyscall=none`. |
| `fetch-hardened-malloc.sh` | allocator build | builds GrapheneOS `hardened_malloc` (our fork) into `prebuilt/hardened_malloc/`. |

## Wire-up

```make
# in the product .mk:
$(call inherit-product, vendor/circle/security/hardening.mk)

# in the device BoardConfig.mk:
-include vendor/circle/security/board-hardening.mk
```

```sh
# build the allocator once (hardening.mk then picks it up automatically):
ANDROID_NDK_HOME=/path/to/ndk vendor/circle/security/fetch-hardened-malloc.sh
```

## Honest scope

This brings the **biggest, lowest-risk** GrapheneOS mitigations: hardened_malloc
+ kernel memory-init + the standard cmdline hardening set. It does **not** yet
match Graphene's full surface — their per-app exploit protections toggle,
hardened libc/bionic patches, and the deeper SELinux tightening are follow-ups
(the SELinux dir is reserved but empty, because a blind `neverallow` fails the
whole build and must be added rule-by-rule with a build to verify). The
allocator swap is the headline and it's real; the rest narrows the gap.
