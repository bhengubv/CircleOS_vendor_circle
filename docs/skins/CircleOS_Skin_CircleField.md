# Circle OS — Skin: "Circle Field" (alternate · the grid)

> Alternate skin for grid lovers ([`CircleOS_Skin_Design_Guide.md`](CircleOS_Skin_Design_Guide.md) §4). **Reworked to the dialed-back distance** — the Metro tile grid is *kept*; only light-touch Circle differentiators are added. (The earlier over-rotated orb/dashboard/frosted version is retired — that became [`CircleOS_Skin_CircleHalo.md`](CircleOS_Skin_CircleHalo.md) instead.)

## 1. The idea
Metro's **tile grid, kept** — flat, bold Circle Blue tiles, lowercase labels, the glanceable mosaic. Differentiated only by the safe, light touches:
- **6dp soft-square** tiles (not the hard 0-corner)
- a **circular B! presence dot** + a **circular people row** (square-meets-circle)
- **ripple-not-flip** motion (+ a subtle breathe on the presence dot)
- **soul-tile content** (mesh · sdpkt · B!), locked **Circle Blue**, renamed vocabulary, wordmark **not** bled off the edge.

**No** orb, **no** dashboard, **no** frosting — those live in Halo. Field sits deliberately close to Metro.

## 2. Delta from the schema baseline (everything else = baseline)
| Token | Value |
|---|---|
| `shape.container` | **`6dp`** (soft-square) |
| `shape.identity` | `full` (B! dot, people, toggles) |
| depth | **flat** (no frosting), 1dp hairline |
| motion | `ripple` + subtle `breathe` on the presence dot; **no flip** |
| layout | the tile mosaic (B! 2×2 · sdpkt 2×2 · aethernet wide · small apps) + a circular people row |
| colour | the **shared Circle palette** — see [`CircleOS_Skin_CircleHalo.md`](CircleOS_Skin_CircleHalo.md) §2–3 / schema §B |
| type | Selawik, lowercase labels, no wordmark-bleed |

## 3. Buildable emission — AOSP RRO
`colors.xml`/`values-night` = shared palette. Shape/size:
```xml
<dimen name="circle_face_radius">6dp</dimen>        <!-- soft-square tiles -->
<dimen name="circle_identity_radius">9999dp</dimen> <!-- circular B! dot / people / toggles -->
<dimen name="circle_cell">76dp</dimen>
<dimen name="circle_gutter">8dp</dimen>
<dimen name="circle_divider">1dp</dimen>
```
Same RRO/loader path as the family — only the values above differ from Metro (`face_radius 0`) and Halo (`face_radius 9999`).
