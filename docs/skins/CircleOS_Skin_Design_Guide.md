# Circle OS — Skin & Icon-Pack Design Guide

> **Intent:** take the Windows Phone / Metro design positives that the *design world* admired — and that the platform's app gap + Microsoft dependency wasted — and do them **right**, on Circle OS's independent, app-rich foundation. Metro is the design *discipline* we inherit; **the soul stays Circle OS** (privacy, ownership, AetherNet, any-handset, uncensorable). **We inherit the discipline, never the trade dress or the names** — the recognizable Windows-Phone signatures are deliberately replaced (§2) and the flagship is renamed **Circle Field**.
> This is the spec you hand a designer + engineer. Numbers are a **starting spec — tune in design**, not gospel.

---

## 0. Why Metro is worth inheriting
Design circles loved Windows Phone for things that were never the reason it failed (the app gap + being chained to Microsoft were). We keep exactly those things:
- **Authentically digital** — flat, content-first, anti-skeuomorphic. (Predated iOS 7's flattening by 3 years.)
- **Typography as the interface** — type does the work icons/chrome do elsewhere.
- **Live Tiles** — a living, glanceable home.
- **Motion with intent** — "fast and fluid," choreographed, not decorative.
- **System rigor** — one consistent design across the whole OS.
- **Restraint** — reductive, generous negative space.

---

## 1. Foundations we inherit (the Metro rules)

### 1.1 The tile grid (the spine of the whole look)
- Phone start screen = a tile grid, **4 "small" columns** wide.
- Tile sizes (in small-cell units): **small 1×1 · medium 2×2 · wide 4×2 · large 4×4**.
- Starting numbers: small cell **≈ 76dp**, gutter **≈ 8dp** → medium ≈ 160dp, wide ≈ 328dp. Outer margin ≈ 12dp.
- Everything snaps to this grid. No free placement.

### 1.2 Tile anatomy
- **Content sits bottom-left** (app name lowercase, small); counts/badges top-right.
- Tile glyph (for non-live tiles): **white, centred** on the accent fill.
- **Live face + back face** that flips; **transparent-tile** option (glyph on the wallpaper).
- Flat fill, **sharp corners**, no shadow, no gradient.

### 1.3 Typography
- Base face: **Selawik** (OFL, Segoe-metric) — the legal Segoe feel.
- Light weights, **large headers**; the panorama/section **wordmark bleeds off the right edge**.
- Lowercase section headers (Metro convention) — keep.
- Starting type ramp (dp): Display 48 / Header 28 / Subhead 20 / Body 15 / Caption 12; headers Light, body Regular.

### 1.4 Colour & theme
- Flat fills; **one accent colour applied across all tiles**.
- **True-black** dark theme (OLED) / clean white light theme.
- No gradients on UI; colour = information, not decoration.

### 1.5 Motion
- **Tile flip / turnstile** on the start screen.
- **Press-to-tilt** feedback (tiles depress toward the touch).
- **Panorama / Pivot** horizontal motion between sections.
- **Parallax** wallpaper behind tiles.
- Page transitions are choreographed (staggered list cascade).

### 1.6 Layout patterns
- **Panorama** (wide canvas that runs off-screen) and **Pivot** (swipe between tabbed sections).
- **Long-list with alphabetical jump** (tap a letter → index grid).
- Content-first: minimal chrome, no title bars where text can lead.

### 1.7 Iconography — two distinct contexts
- **Tile glyph** = white, centred, optical-sized, on the accent square.
- **In-app / list icons** = **monoline** (single stroke weight), monochrome, angular/geometric.
- Flat only — no 3D, gradient, or shadow. SVG masters → exported sizes.

---

## 2. Distinctly Circle OS — the six moves (what makes it *not* Windows Phone)
> **The strongest IP answer is the family (§4):** the **default skin is circle-forward (Circle Halo)** — round faces, a B! hub — so the OS's identity is unmistakably Circle; Metro's tile look ships only as an **opt-in nostalgia skin**. The six moves below are Circle's design *language* — **Halo** expresses them fully, **Field** lightly on a grid, **Metro** is the disciplined-grid tribute.

We inherit Metro's **discipline** (§1); we do **not** ship its trade dress or vocabulary. *(Not legal advice — get an IP pass before ship. But flat design + grids aren't ownable; what reads as Windows Phone is a short list of signatures + names. We deliberately replace those and rename the whole thing.)*

**Deliberately dropped (the WP tell):** the uniform hard-square flip-tile mosaic · the tile-flip · the panorama-with-wordmark-bleeding-right · the names *Metro / Live Tiles / Panorama / Pivot*.

**The six moves that replace them:**
1. **Circle is structural, not decorative.** Faces are **soft-square (squircle)** at one Circle radius; identity elements (avatars, contacts, B! presence, toggles, status) are **full circles**; a radial **B! orb** anchors the home. The silhouette is instantly not WP's hard 0-corner.
2. **Break the uniform grid.** A **hero canvas** (the mesh alive, or B!) + a **face shelf** beneath — asymmetric, content-first — with mixed geometry: a **capsule** for status, **circles** for people, not only square/wide/large.
3. **Living + mesh motion, not the flip.** Live faces **breathe** (a presence pulse); a tap sends a **ripple across neighbouring faces like a mesh signal**; **circular reveal** from the face centre on launch. This motion is literally the product — the ownable fingerprint.
4. **Depth through adaptive translucency, not flat-only.** **Frosted, translucent faces** over a **parallax** wallpaper, alpha auto-tuned to the wallpaper (schema §G) and to mesh/privacy state — material and alive, the opposite of WP's opaque fills, distinct from iOS glass because it's *adaptive*.
5. **The home is a dashboard, not a launcher.** **Data-as-typography** (wallet balance, peers-nearby, blind-comms state as hero numerals), a persistent **privacy/encrypted ribbon**, and **soul faces** — a **live AetherNet mesh face**, the **SDPKT wallet**, **B!**. Same type-led restraint Metro taught, pointed at what Circle *means*.
6. **Own the vocabulary + the accent.** Rename: tiles → **faces**, the grid → **the wall**, the motion → **ripple/pulse**, the flagship → **Circle Field** (not "Circle Metro"). Accent **locked to Circle Blue `#2196F3`** (+ navy `#2c3e50` base) across all faces — the one signature colour, **never orange**, never WP's user-chosen free-for-all. Dual dark base kept: true-black **and** deep-navy.

Guardrails from the brand rules: **no orange · no scrollbars · fully responsive · accessibility-first** (frosting must never drop text-on-face below AA).

---

## 3. Building a skin on Circle OS (technical shape)
> **The concrete token contract + engine lives in [`CircleOS_Skin_Token_Schema.md`](CircleOS_Skin_Token_Schema.md)** — the exact ~50 colour roles, the *derive-don't-author* rule, the square↔circle shape split (§2.2 made mechanical), Selawik on the type ramp, wallpaper-adaptive translucency, and the RRO registry / loader / hot-swap. Adapted from the proven `Appearance` model in end-4's *illogical-impulse* (schema only — M3 is open, we ship none of the GPL code).

A **skin** = a bundle the theme engine applies, with a **first-boot picker**:
- **RRO overlay** — overrides `colors.xml` (accent/theme), `dimens` (grid/tile/corner), shape tokens, and font tokens.
- **Icon pack** — Lawnicons/appfilter-style mapping; SVG masters → density buckets.
- **Wallpaper set** — true-black + navy variants with the Circle mark.
- **Font** — Selawik.
- **Accent** — from the brand palette.
- Engine = **AOSP RRO + Material You** (native); packaging structure can follow the Apache `substratum/template`.

---

## 4. The skin family — one engine, choose your look
Circle OS ships a **family**; the **first-boot picker** + `SkinRegistry` let a user land on the default and switch anytime. All three are the same token schema at three `shape.container` values.
- **Circle Halo** — *default, flagship.* Circle-forward: round faces, a B! hub, everything leaning into the Circle. [`CircleOS_Skin_CircleHalo.md`](CircleOS_Skin_CircleHalo.md) · `shape.container = full`.
- **Circle Field** — *alternate.* The Metro tile grid, kept, with light circle touches (soft-square, circular identity, ripple-not-flip). [`CircleOS_Skin_CircleField.md`](CircleOS_Skin_CircleField.md) · `= 6dp`.
- **Circle Metro** — *alternate · nostalgia.* The disciplined hard-square tile grid — a tribute to Metro's *discipline* (renamed, Circle Blue, ripple-not-flip; not a clone). [`CircleOS_Skin_CircleMetro.md`](CircleOS_Skin_CircleMetro.md) · `= none`.
- **Icon pack v1**: monoline set — core ~150 app + face glyphs, weight aligned to Selawik.

That's the whole IP answer: the **default is unmistakably Circle**; Metro's tile look is **opt-in**.

## 5. Asset shortcuts (optional — only if cleanly licensed)
Per the policy: **if a clean-licensed asset already exists, use it as a base; otherwise make our own.** Realistically the one clear clean grab is **Selawik** (OFL). Everything else — tiles, icons, wallpapers — **make our own**, which also makes it distinct. (See `CircleOS_Theme_Skins_Scout.md` for the verified clean-asset list.)

## Appendix — "don't lose what critics loved" checklist
Typographic hierarchy ✓ · Live Tiles ✓ · choreographed motion ✓ · flat/authentically-digital ✓ · panorama/pivot ✓ · grid discipline ✓ · restraint ✓
