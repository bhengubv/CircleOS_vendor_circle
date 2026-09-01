# Circle OS — Windows-Phone SKIN Scout (verified)

> **Corrected scope:** this is about **skins** (visual theme packs), **not launchers**.
> A "skin" = the *look* (tiles, icons, colours, wallpaper, font) applied by Circle OS's theme engine — no separate app.
> Scouted + licence-verified 2026-06-25 via `gh api` (authoritative, not snippets).

## Skin vs launcher (the correction)
- **Launcher** = a whole home-screen app. *(An earlier pass wandered here — out of scope, dropped.)*
- **Skin** = a visual theme layer the OS paints over itself via **AOSP RRO overlays + Material You**. Pick a skin → the system re-dresses. **This doc is skins only.**

## What a Circle OS "skin" is made of
`icon pack` + `tile / accent styling (RRO overlay)` + `wallpaper` + `font`, bundled together and picked at first boot.

## Verified-clean skin components (what we can actually ship)
| Component | Source | Licence (verified) | Notes |
|---|---|---|---|
| Android icon pack | **Gerreidae/Windows-Phone-Metro-Pack** | **Apache-2.0** ✅ | Lawnicons-based (clean base), Kotlin, 2025. **Caveat:** confirm glyphs are original WP-*style* art, not traced Microsoft icons, before shipping. |
| Original Metro icon set | **Templarian/WindowsIcons** | **Creative Commons** (979★) — *verify BY vs ND* | Austin Andrews' *original* Metro-style icons. CC-BY = ship with credit; CC-BY-**ND** = can't modify (would block polishing) — confirm variant first. |
| Metro icon set (newer) | Templarian/ModernIcons | Creative Commons — *verify* | Smaller/newer successor. |
| Font | **microsoft/Selawik** | **OFL-1.1** ✅ | Open, Segoe-metric-compatible = the legal Segoe look. |
| Skin *packaging* template | **substratum/template** | **Apache-2.0** ✅ | Official theme template — the structure for packaging a skin; pairs with native AOSP RRO. |
| Theme *engine* | **AOSP RRO + Material You** | Apache ✅ | The in-OS skin switcher (our own — first-boot picker). |
| Web-surface styling | **olton/Metro-UI-CSS** | **MIT** ✅ | For PWA/web surfaces + styling reference. |
| (.NET app icons — bonus) | **MahApps.Metro.IconPacks** | **MIT** ✅ | Metro icons for the MAUI apps (active, 1900★). |

## Reference-only / can't use
- **substratum/substratum** (the manager app) — **GPL-3.0** → don't fold into our Apache OS; use **AOSP RRO** natively instead.
- **Wallpapers** — no clean, openly-licensed *WP/Metro wallpaper pack* found in the wild → source **CC0** (awesome-cc0) or create our own.
- Softonic/APKPure/Uptodown "Metro theme" packs — unknown / Microsoft-derived licence → avoid (IP risk).
- *Launchers* (SquareHome / METROV / metro-launcher) — out of scope for skins; in git history if ever revisited.

## Honest bottom line
There is **no ready-made, fully-clean "WP skin pack" to lift wholesale** — same story as the launchers. **But** the clean *parts* to assemble proper WP-style skins all exist: an Apache icon pack, Austin Andrews' CC Metro icon set (original art), Selawik (OFL) font, the Apache skin-packaging template + native RRO engine, and Metro CSS (MIT).

**So the play is: assemble 2–3 skins from these clean parts + our own original tile/wallpaper art, applied by the theme engine.** Customisable from first boot, zero Microsoft IP, zero no-licence assets.

## Next steps
1. Verify the two open licence caveats: **Gerreidae glyph provenance** (original vs traced) and **Templarian CC variant** (BY vs ND).
2. Stand up the **RRO theme engine + first-boot skin picker** (folds into WP-07 / WP-67 / WP-08).
3. Assemble **skin v1**: Selawik font + clean Metro icon set + original tile styling + CC0 wallpaper.
