# Circle OS boot tooling — magiskboot + AVB (#151)

Wiring for [magiskboot](https://github.com/bhengubv/magisk) (our fork) into the
Circle OS OTA and boot-signing flow. Two jobs:

1. **Release sanity gate** — verify the boot image the AOSP build produced is a
   well-formed Android boot image before an OTA ships.
2. **Power-user install path** — produce a Magisk-patched **and Circle-AVB-signed**
   `boot.img` for users with an unlocked bootloader who want root, without
   weakening verified boot (the patched image is re-signed with the Circle key).

This does **not** replace AOSP signing: `make otapackage` already produces the
fully AVB-signed A/B `payload.bin` for the normal locked / DSU install paths.
magiskboot does not sign — signing is always `avbtool` with the Circle key.

## Install-path tiers (reach)

| Path | Unlock needed | Artifact | Tooling |
|------|---------------|----------|---------|
| Apps only | none | per-app APK | normal app install |
| Full OS, dual-boot | none | A/B `payload.bin` | DSU loader |
| Full OS, flashed | bootloader unlock | A/B `payload.bin` | fastboot/recovery |
| Rooted boot | bootloader unlock | `boot-magisk-signed.img` | `patch-boot.sh` + fastboot |

## Scripts

- **`magiskboot.sh`** — wrapper. `path` / `inspect <img>` / `verify <img>` /
  `unpack <img> <dir>` / `repack <img> <out>`. Resolves magiskboot from
  `$CIRCLE_MAGISKBOOT`, the vendored prebuilt, or `PATH`.
- **`fetch-magiskboot.sh`** — install a **host** magiskboot into
  `vendor/circle/prebuilt/magiskboot/`. `--vendor <bin>` copies a prebuilt host
  binary (reliable); `--from-source [--ref <ref>]` clones and builds our fork.
- **`patch-boot.sh`** — `--boot <img>` (or `--payload <payload.bin>`) →
  Magisk-patch → `avbtool add_hash_footer` with the Circle AVB key →
  `boot-magisk-signed.img` + `.sha256`.

## One-time setup

```sh
# Get a host magiskboot (pick one):
vendor/circle/dist/boot/fetch-magiskboot.sh --vendor /path/to/host-magiskboot
vendor/circle/dist/boot/fetch-magiskboot.sh --from-source --ref circle
```

## Where it hooks in

`vendor/circle/release/build_release.sh` runs `magiskboot.sh verify` on the built
`boot.img` after `make otapackage`, as a non-fatal sanity gate (skipped cleanly
if magiskboot is not installed). The patched-boot artifact is produced on demand
with `patch-boot.sh` and distributed alongside the OTA, signed by the same
`sign-release.sh` manifest so downloads stay verifiable.

> Note: magiskboot must be a **host** (Linux) build, not the on-device binary
> shipped inside the Magisk APK.
