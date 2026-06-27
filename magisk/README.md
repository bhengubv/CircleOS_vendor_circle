# Circle Layer — Magisk module (#152)

A **systemless** Magisk module that brings the Circle OS *userspace* layer to a
**rooted stock Android** device — extending Circle's reach to people who can't or
won't flash the full OS, without modifying `/system` on disk.

## What it delivers

- The Circle apps (`CircleMe`, `CircleRooms`, `CircleMail`, `CircleMessages`,
  `CirclePhotos`, `CircleNotes`, `CircleMaps`, `Butler`) as systemless
  `priv-app` overlays.
- The `ro.circle.*` feature props (via `system.prop`), so the apps behave as they
  do on the full OS.
- Boot hooks (`post-fs-data.sh`, `service.sh`) for Circle userspace init.

## What it does NOT deliver

Framework-level Circle features are baked into `framework.jar` / `services.jar`
and **cannot** be delivered by a userspace overlay:

- `CircleMeshService` (mesh system service), `CirclePrivacyManagerService`,
  permission/network enforcers, wallet settlement — these need the **full Circle
  OS** image (flash or DSU).

A future **Zygisk companion** can add per-app privacy hooks in userspace; the
`zygisk/` slot and `sepolicy.rule` are reserved for it.

## Reach tiers

| Path | Unlock | Root | Gets |
|------|--------|------|------|
| Circle apps from a store | no | no | the apps only |
| **Circle Layer (this)** | no¹ | yes | apps + props + boot hooks |
| Full Circle OS via DSU | no | no | everything, dual-boot |
| Full Circle OS flashed | yes | optional | everything |

¹ Rooting generally requires an unlocked bootloader; on already-rooted devices no
further unlock is needed.

## Build & install

```sh
# After a Circle OS build (APKs under out/target/product/<device>/system/priv-app):
vendor/circle/magisk/build-module.sh --apps out/target/product/<device>/system/priv-app
# or hand-pick:
vendor/circle/magisk/build-module.sh --apk CircleMe=/path/CircleMe.apk --out circle-layer.zip

# Then flash circle-layer.zip in Magisk Manager (or: magisk --install-module circle-layer.zip)
```

## Layout

```
circle-layer/
  module.prop                 module identity + version
  customize.sh                install-time checks (API 33+, 64-bit) + perms
  system.prop                 ro.circle.* props (resetprop'd at boot)
  post-fs-data.sh             early boot hook
  service.sh                  late_start boot hook
  sepolicy.rule               SELinux rules (reserved for the Zygisk companion)
  system/priv-app/            Circle apps injected here by build-module.sh
  META-INF/.../update-binary  standard Magisk module installer
build-module.sh               assembles the flashable zip
```
