# Circle OS — Skin Token Schema & Engine

> **What this is:** the concrete token contract + skin engine that [`CircleOS_Skin_Design_Guide.md`](CircleOS_Skin_Design_Guide.md) §3 gestures at but never specifies. The *design language* (Metro discipline + Circle signatures) is decided in the guide; this doc decides **the machine** — the exact tokens a skin supplies, what derives automatically, and how skins load/swap.
>
> **Source of the pattern:** adapted from the proven `Appearance` model in end-4's *illogical-impulse* / `end4-pC` (Quickshell/QML). We borrow the **structure**, not the code or values — the structure is just Material 3, which Android/Monet already speaks natively, so it drops onto AOSP RRO with **zero translation**. (Clean: M3 is open; every value below is our own.)
>
> **The one idea that matters:** the schema is the *container*; **a look is one set of values inside it**. The family is the proof: **Halo** = round faces, **Field** = soft-square grid, **Metro** = sharp grid — one schema, three `shape.container` values. Authors fill a small manifest; the engine derives the rest.

---

## A. The skin contract (what a skin package ships)
A **skin** = a token manifest in five groups + assets. Mirrors the guide's §3 bundle.

| Group | What it defines | Guide ref |
|---|---|---|
| `color` | the ~50 Material-3 **role** values (the only colors an author picks) | §1.4, §2.1 |
| `shape` | the rounding ramp **and** the square↔circle split | §1.2, §2.2 |
| `type` | Selawik family + the type ramp + variable axes | §1.3 |
| `size` | grid/tile/gutter/margins + spacing scale | §1.1 |
| `motion` | durations + easing curves for the named motions | §1.5, §2.4 |
| *assets* | icon pack, wallpaper set (true-black + navy) | §3, §5 |

**Golden rule (borrowed from the source):** authors set **base roles only**; every hover / active / border / disabled / elevation-layer state is **computed**. A skin file stays ~40 lines, states never drift, and every component that references a role by name re-skins for free.

---

## B. Color — the role contract (this is `colors.xml` / the RRO)
Android already exposes these as Material theme attrs, so **the skin's color layer *is* this role set**; the RRO overlay overrides them. Author picks values; the UI kit only ever references **names**, never hex (this is §3's missing "semantic indirection").

**Base + surface (the canvas):** `background`, `onBackground`, `surface`, `surfaceDim`, `surfaceBright`, `surfaceContainerLowest → Low → (base) → High → Highest`, `onSurface`, `surfaceVariant`, `onSurfaceVariant`, `inverseSurface`, `inverseOnSurface`, `surfaceTint`.

**Accent roles** (each has `on-` + `Container` + `onContainer`): `primary`, `secondary`, `tertiary`, `error`, **`success`** (M3 ships only `error` — we keep end4's added positive role), plus the `*Fixed` / `*FixedDim` variants.

**Lines & effects:** `outline`, `outlineVariant`, `shadow`, `scrim`.

**Optional:** `term0–15` — a 16-slot palette, only if a skin themes a terminal/console/dev surface. Skip for consumer skins.

**Circle values baked in (from the guide):**
- `primary = #2196F3` (Circle Blue — the one accent across all tiles). **Never orange.**
- **Circle Metro / true-black:** `background = #000000`, `surface*` = the near-black ramp (OLED).
- **Navy variant:** `background = #2c3e50` (the guide's deep-navy base), surfaces tinted up from it — a per-skin option, not a separate skin.
- Tiles fill from `primaryContainer` (or a curated brand-accent set); tile glyph = `onPrimary` (white, centred).

**What derives (engine, not author) — the elevation-layer system:**
`layer0 → layer4`, each auto-producing `…Hover`, `…Active`, `…Border`, `…Disabled`, `on…` by mixing/overlaying the base roles against `onSurface`. Components say *"I'm on layer1"*; the engine paints the right stateful colors. Authors never write a hover color.

> Note: Circle Field keeps low tonal tint but adds **frosted adaptive translucency** (§G) — depth comes from the wallpaper showing through, not from tonal elevation. The layer machinery still runs and drives the per-layer frosting alpha.

---

## C. Shape — the rounding ramp + the Circle square↔circle split
end4's ramp → our ramp (dp). Keep the named steps so components reference `shape.normal`, not a number:

`none 0 · hairline 2 · xs 6 · sm 8 · md 12 · lg 17 · xl 23 · xxl 30 · full 9999`
plus semantic aliases: `screenRounding`, `windowRounding`, `sheetRounding`.

**This is where §2.2 "square-meets-circle" becomes mechanical** — the skin declares **two shape families**, and components inherit by role:
- **`shape.container` (faces, panels, cards, sheets)** — the family's headline knob: **Halo = `full`** (round) · **Field = `6dp`** (soft-square) · **Metro = `none`** (sharp). One token, three shipped skins.
- **`shape.identity` (avatars, B! presence, toggles)** → **`full`**. Always circular, in every skin.

Switching skins repoints `shape.container` (+ motion/layout) — no component changes.

---

## D. Type — Selawik on end4's ramp
- **Families:** `main = Selawik` (OFL — the guide's one clean grab), `numbers`, `title`, `monospace`, `reading`, `expressive`, `icon` (monoline Circle set). end4 proves the multi-family split is worth having.
- **Variable axes** (if Selawik variable, else static weights): `main {wght 450}`, `title {wght 550}` — the "slightly bold title" trick.
- **Ramp** — reconcile end4's pixel ladder with the guide's §1.3 dp ramp:
  `caption 12 · body 15 · subhead 20 · header 28 · display 48` (+ end4's fine steps `10/13/16/17/19` for dense UI). Headers **Light**, body **Regular**, lowercase section headers (Metro), wordmark bleeds off the right edge.

---

## E. Size — the grid is a token, not a constant
Promote the guide's §1.1 grid into the manifest so a skin can retune density:
`smallCell 76 · gutter 8 · outerMargin 12` → medium 160 / wide 328 / large 4×4 derive. Plus a spacing scale `space.{xs4, sm8, md12, lg16, xl24, xxl32}`. Components lay out against tokens; a "compact" skin drops `smallCell` and everything reflows. (**No scrollbars, fully responsive** — guardrails carry.)

---

## F. Motion — the named motions as duration+curve tokens
end4 splits `animation` (durations) from `animationCurves` (easing) — do the same so a skin can dial "fast & fluid" without touching component code:
- **Durations:** `instant 0 · quick 120 · standard 200 · emphasized 350 · panorama 500`.
- **Curves:** `standard`, `emphasized`, `decel`, `accel` (Material easing), plus **`turnstile`** and **`circleReveal`** for the two Circle signatures (§1.5 tile flip/turnstile; §2.4 circular reveal/ripple from tile centre on launch). Press-to-tilt + staggered list cascade reference these tokens.

---

## G. Adaptive behavior — the "smart skin" signature worth stealing
end4's standout trick, and it's cheap on Android: **the skin auto-tunes surface translucency to the wallpaper.** Quantize the wallpaper → dominant color → `vibrancy = (saturation + lightness) / 2` → run the fitted curve:

```
backgroundTranslucency = clamp( 0.5768·v² − 0.759·v + 0.2896 , 0 , 0.22 ) − (light ? 0.12 : 0)
```

Busy/vivid wallpaper → surfaces firm up for legibility; calm wallpaper → they open up. Maps directly onto the guide's **transparent-tile option + parallax wallpaper** (§1.2/§1.5) and gives Circle OS a live, wallpaper-aware feel WP never had. Toggle: `auto | manual | off`.

---

## H. The engine (fills §3 "technical shape" + §4)
- **A skin = a package:** `{ tokens.manifest (A–F), RRO overlay (the colorsxml/dimens/shape/font it emits), icon pack, wallpaper set }`. Packaging can follow the Apache `substratum/template` (guide §3).
- **Registry + loader:** a `SkinRegistry` enumerates installed skin packages; a loader mounts the active one (this is end4's `panelFamilies` + `PanelLoader` pattern — a loader that mounts a named family). **First-boot picker** chooses the default; the loader **hot-swaps at runtime** (apply RRO + refresh Material You), no reflash.
- **Engine base:** AOSP **RRO + Material You** (native), exactly as §3 says — this schema is just the *contract* those overlays fill.

---

## I. What an author actually writes (the whole "Circle Field" color+shape manifest)
```
skin "Circle Field" {
  color {
    primary #2196F3;  onPrimary #FFFFFF
    primaryContainer #2196F3;  onPrimaryContainer #FFFFFF
    background #000000;  onBackground #FFFFFF        // true-black
    surface #000000; surfaceContainer #0E0E0E; surfaceContainerHigh #171717
    outline #3A3A3A;  error #FF5449;  success #34C759
    // ...the ~50 roles. that's it.
  }
  shape { container squircle;  identity full;  window md }
  type  { main "Selawik" }
  motion{ launch circleReveal;  faces breathe;  tap ripple }
  assets{ icons "circle-monoline";  wallpaper "true-black-mark" }
}
// every hover/active/border/layer/state DERIVES. ~40 lines = a full skin.
```

---

### Build order (matches guide §4)
1. Implement the **schema + engine** (B–H) once — the RRO emitter + `SkinRegistry`/loader + Material You bridge + adaptive-translucency service.
2. Author **Circle Halo** (default, `container = full`) as the reference manifest — see [`CircleOS_Skin_CircleHalo.md`](CircleOS_Skin_CircleHalo.md).
3. Author the alternates — **Field** (`6dp`) and **Metro** (`none`) — proving swap works by changing *only* values.
4. **Icon pack v1** (monoline, Selawik-weighted) ships as the `icon` family + asset.

> Licensing note: this is a **schema/pattern reference** (Material 3 is open; end4 is GPL but we ship none of it). Every token value and asset is our own → clean under the MIT/Apache/OFL-only rule.
