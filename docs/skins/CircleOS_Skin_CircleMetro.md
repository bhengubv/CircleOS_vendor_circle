# Circle OS — Skin: "Circle Metro" (alternate · nostalgia)

> The **opt-in nostalgia** skin ([`CircleOS_Skin_Design_Guide.md`](CircleOS_Skin_Design_Guide.md) §4) — the disciplined tile grid people loved on Windows Phone, rebuilt on Circle's foundation. **Not the default** (that's [`CircleOS_Skin_CircleHalo.md`](CircleOS_Skin_CircleHalo.md)); offered for those who miss it.

## 1. The idea
Hard-square, flat, bold **tile grid** — one Circle Blue accent, white glyphs, lowercase labels, the glanceable mosaic. Maximum Metro *discipline*.

## 2. Delta from the schema baseline (everything else = baseline)
| Token | Value |
|---|---|
| `shape.container` | **`none` (0)** — sharp tiles |
| `shape.identity` | `full` (identity layer stays circular) |
| depth | **flat opaque**, 1dp hairline, no shadow |
| motion | **`ripple`, NOT the WP flip** — keep the tile *look*, not the trademarked flip animation |
| layout | the tile mosaic + soul-tile content (mesh · sdpkt · B!) |
| colour / type | shared Circle palette + Selawik; wordmark **not** bled |

## 3. Why this is a tribute, not a clone (IP)
Even the nostalgia skin ships **renamed vocabulary, the Circle Blue lock, soul-tile content, and ripple-not-flip** — so it reads as a *tribute to Metro's discipline*, not a Windows Phone reproduction. And it's **one optional skin in the family**, never the OS's identity — the default (Halo) is unmistakably Circle. *(Not legal advice — IP pass before ship.)*

## 4. Buildable emission — AOSP RRO
`colors.xml`/`values-night` = shared palette. Shape/size:
```xml
<dimen name="circle_face_radius">0dp</dimen>        <!-- sharp tiles -->
<dimen name="circle_identity_radius">9999dp</dimen>
<dimen name="circle_cell">76dp</dimen>
<dimen name="circle_gutter">8dp</dimen>
<dimen name="circle_divider">1dp</dimen>
```
Same RRO/loader path — only `face_radius = 0` (and the flip→ripple motion) distinguish it from Field (`6dp`) and Halo (`9999`).
