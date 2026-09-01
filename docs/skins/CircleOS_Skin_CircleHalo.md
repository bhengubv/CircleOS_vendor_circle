# Circle OS — Skin: "Circle Halo" (flagship · default)

> The **default** skin ([`CircleOS_Skin_Design_Guide.md`](CircleOS_Skin_Design_Guide.md) §4), authored against [`CircleOS_Skin_Token_Schema.md`](CircleOS_Skin_Token_Schema.md). **Circle-forward — the circle is the organizing principle.** Circle OS boots into this; the first-boot picker lets a user switch to **Circle Field** (grid) or **Circle Metro** (nostalgia). Ships **light + true-black dark**. Starting spec — tune on the P30; every value is ours.

## 0. Vocabulary
`hub` = the central B! orb · `halo` = the rings around it · `face` = a **round** surface (soul/app) · `people` = circular avatars.

## 1. The idea
Everything is round. A breathing **B! hub** ringed by **halos** anchors the home; **round faces** (soul, then apps) sit in tidy rows; **people are circles**. Still disciplined — flat, structured, glanceable (the Field lesson: no busy dashboard, no frosting). It's the opposite of Windows Phone's rectilinear grid, so it reads as unmistakably Circle — and it turns Metro's tile look into a *pure opt-in nostalgia choice*, which is the whole IP answer.

## 2. Colour roles — LIGHT
| Role | Hex | | Role | Hex |
|---|---|---|---|---|
| primary | `#0061A4` | | surface | `#FFFFFF` |
| onPrimary | `#FFFFFF` | | onSurface | `#1B1B1D` |
| **primaryContainer** | **`#2196F3`** | | surfaceContainerLowest | `#FFFFFF` |
| **onPrimaryContainer** | **`#FFFFFF`** | | surfaceContainerLow | `#F7F7F8` |
| secondary | `#40526A` | | surfaceContainer | `#F1F1F3` |
| onSecondary | `#FFFFFF` | | surfaceContainerHigh | `#EBEBEE` |
| secondaryContainer | `#2C3E50` | | surfaceContainerHighest | `#E5E5E9` |
| onSecondaryContainer | `#FFFFFF` | | surfaceVariant | `#E1E2EC` |
| tertiary | `#006A67` | | onSurfaceVariant | `#45464A` |
| tertiaryContainer | `#A8F0EB` | | outline | `#757680` |
| onTertiaryContainer | `#00201F` | | outlineVariant | `#C5C6D0` |
| error | `#BA1A1A` | | inverseSurface | `#303032` |
| errorContainer | `#FFDAD6` | | inversePrimary | `#9BCBFF` |
| onErrorContainer | `#410002` | | background | `#FFFFFF` |
| success | `#1B6B3A` | | onBackground | `#1B1B1D` |
| successContainer | `#A2F2B6` | | surfaceTint | `#0061A4` |
| onSuccessContainer | `#00210E` | | shadow / scrim | `#000000` |

## 3. Colour roles — TRUE-BLACK DARK
| Role | Hex | | Role | Hex |
|---|---|---|---|---|
| primary | `#9BCBFF` | | surface | `#000000` |
| onPrimary | `#003354` | | onSurface | `#E4E2E3` |
| **primaryContainer** | **`#2196F3`** | | surfaceContainerLowest | `#000000` |
| **onPrimaryContainer** | **`#FFFFFF`** | | surfaceContainerLow | `#0D0D0E` |
| secondary | `#B9CDE8` | | surfaceContainer | `#151517` |
| onSecondary | `#1B2A38` | | surfaceContainerHigh | `#1F1F21` |
| secondaryContainer | `#2C3E50` | | surfaceContainerHighest | `#2A2A2C` |
| onSecondaryContainer | `#FFFFFF` | | surfaceVariant | `#44464F` |
| tertiary | `#4FDAD3` | | onSurfaceVariant | `#C5C6D0` |
| tertiaryContainer | `#004F4C` | | outline | `#8E9099` |
| onTertiaryContainer | `#71F7EF` | | outlineVariant | `#44464F` |
| error | `#FFB4AB` | | inverseSurface | `#E4E2E3` |
| errorContainer | `#93000A` | | inversePrimary | `#0061A4` |
| onErrorContainer | `#FFDAD6` | | background | `#000000` |
| success | `#86D89B` | | onBackground | `#E4E2E3` |
| successContainer | `#0C5230` | | surfaceTint | `#9BCBFF` |
| onSuccessContainer | `#A2F2B6` | | shadow / scrim | `#000000` |

Accent handling per schema: **round faces fill `primaryContainer #2196F3` (constant both themes) + white glyph**; **`primary` adapts** for accent text (`#0061A4` light / `#9BCBFF` dark). Fixed roles per schema; `term*` omitted.

## 4. Shape — the circle (the move)
- `shape.face = full` (round soul/app faces) · `shape.hub = full` · `shape.identity = full` (people, toggles, presence) · `shape.sheet / window = md` (rounded panels).
- This is the schema's **`shape.container` set to `full`** — the *same token* Field sets to `6dp` and Metro sets to `none`. Three skins, one token, three values.

## 5. Depth — flat, round
Flat opaque round faces over a **parallax** wallpaper; optional 1dp `outlineVariant` hairline. **No frosting** (Field lesson). Depth reads from the halos + parallax, not tonal tint.

## 6. Motion
`breathe` (hub + live faces) · **`halo ripple`** (the hub ring pulses outward — a mesh signal) · `circleReveal` on launch · `ripple` across faces on tap. **No flip.**

## 7. Type — Selawik
`main = Selawik`; labels **under** faces, lowercase; the hub renders **B!**. Ramp per schema (`display48 Light … caption12`). **No wordmark-bleed.**

## 8. Size — hub + rings
`hub ø108 · soulFace ø66 · appFace ø52 · person ø40`; hero ≈ top **35%**; row gutter 14–18; touch target 48. No scrollbars, fully responsive.

## 9. Layout
**hub hero** → **soul-face row** (aethernet · sdpkt · txtme) → **app-face rows** → **people row**. Structured and glanceable — the circle organizes, it doesn't scatter.

## 10. Assets
`icons = "circle-monoline"` (white glyphs, centred) · `wallpaper = { dark: "true-black-mark", light: "white-mark" }`, parallax-ready.

## 11. Buildable emission — AOSP RRO
`res/values/colors.xml` + `values-night/colors.xml` = the §2/§3 role values (shared Circle palette, `circle_*` names). Shape/size:
```xml
<dimen name="circle_face_radius">9999dp</dimen>     <!-- round faces -->
<dimen name="circle_identity_radius">9999dp</dimen>
<dimen name="circle_hub_diameter">108dp</dimen>
<dimen name="circle_soulface_diameter">66dp</dimen>
<dimen name="circle_appface_diameter">52dp</dimen>
<dimen name="circle_divider">1dp</dimen>
```
Package as an RRO; the `SkinRegistry`/loader mounts it as **default**; Material You stays on for parallax + adaptive tuning.

---

### The family proves the schema
**Halo** (`shape.container = full`) · **Field** (`= 6dp`) · **Metro** (`= none`) — one engine, three looks, chosen in the picker. Default is unmistakably Circle; Metro is opt-in. Contrast/AA per schema (white-on-`#2196F3` ✓ large, `primary` for accent text).
