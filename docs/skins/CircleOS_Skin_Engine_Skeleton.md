# Circle OS — Skin Engine: Walking Skeleton

> The minimal end-to-end proof that a skin's tokens can be **emitted → enabled → visibly applied, live, no reflash** — i.e. seam ① (system theming) of the [architecture](CircleOS_Skin_Design_Guide.md). Companion to [`CircleOS_Skin_Token_Schema.md`](CircleOS_Skin_Token_Schema.md) (phases B1 emit + B3 apply).

## What it proves
A skin's **colours** reach the whole system through Android's overlay stack — the same mechanism Material You uses for Monet. Once this works, the rest of the engine (registry, picker, adaptive translucency) is layers on top.

## Where it runs
`OverlayManager` + `FabricatedOverlay` are **platform** APIs, drivable from the shell only where the shell is trusted:
- **Circle OS** (platform build) — the real home.
- **AOSP userdebug / an emulator** — for proving the mechanism (used here: `pixel_5_api_34`).
- **rooted** device.
- **NOT** stock **MIUI / EMUI** — they lock `OverlayManager` from shell and suppress Material You. The P30-class phones can't validate this; that's expected, not a failure.

## Fidelity ladder (this skeleton = rung 1–2)
1. **Accent via Material You seed** — `settings put secure theme_customization_overlay_packages {system_palette:2196F3,…}`. No root; regenerates Monet from Circle Blue. (Fallback.)
2. **Full palette via FabricatedOverlay** — override the framework colour slots directly (`android:color/system_accent1_500 …`) via `cmd overlay fabricate` + `cmd overlay enable`. The engine path. ← walking skeleton
3. **Full skin** — an **RRO APK** carries shape/type (`circle_face_radius` = full / 6dp / none, etc.) and the **launcher** reads the layout tokens for the Halo hub / Field grid / Metro tiles home. The real thing, on Circle OS.

## Run it
```bash
./circle_skin_apply.sh <serial> halo      # apply (seed + fabricated accent overlay)
./circle_skin_apply.sh <serial> revert    # undo
```
(Family colours are identical — `halo|field|metro` set the same Circle Blue; their *shape/layout* difference is rung 3.)

## The real thing — Circle theming service (platform sketch)
On Circle OS this is a system service / SystemUI hook, not a shell script:
```kotlin
val om = context.getSystemService(OverlayManager::class.java)
val skin = SkinRegistry.active()                       // Halo | Field | Metro
val fob = FabricatedOverlay.Builder("com.circleos.theme", "CircleSkin", "android").apply {
    skin.colorRoles.forEach { (res, argb) ->           // all ~50 role slots from the manifest
        setResourceValue(res, TypedValue.TYPE_INT_COLOR_ARGB8, argb)
    }
    // shape/type ship as a static RRO APK; layout tokens go to the launcher
}.build()
om.registerFabricatedOverlay(fob)
om.setEnabled(OverlayIdentifier("com.circleos.theme:CircleSkin"), true, UserHandle.SYSTEM)
```

## Next rungs (engine phases)
- **B2 SkinRegistry** — discover installed skin packages + persist the active one.
- **B4 Picker** — first-boot + Settings, live previews.
- **B5 Adaptive translucency** — wallpaper vibrancy → frosting alpha (Field/Halo).
- **Launcher layout** — pluggable Halo/Field/Metro home reading the skin's layout tokens.
