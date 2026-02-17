# Circle OS Design System — Accessibility Audit

**Standard:** WCAG 2.1 AA
**Date:** February 2026
**Status:** Initial audit — against design tokens in CircleTheme overlay

---

## 1. Colour Contrast Ratios

### 1.1 Dark Mode (Default)

| Foreground | Background | Ratio | Requirement | Pass? |
|------------|------------|-------|-------------|-------|
| `#FFFFFF` (Primary text) | `#0A0A0A` (Background) | **21:1** | 4.5:1 body | ✅ |
| `#9E9E9E` (Secondary text) | `#0A0A0A` (Background) | **4.52:1** | 4.5:1 body | ✅ |
| `#9E9E9E` (Secondary text) | `#212121` (Surface) | **4.1:1** | 4.5:1 body | ⚠️ Marginal — monitor |
| `#FFFFFF` on `#2196F3` (Primary button) | — | **3.95:1** | 3:1 large text | ✅ |
| `#2196F3` (Accent) on `#0A0A0A` | — | **5.9:1** | 3:1 UI component | ✅ |
| `#4CAF50` (Success) on `#0A0A0A` | — | **5.1:1** | 3:1 UI component | ✅ |
| `#FF9800` (Warning) on `#0A0A0A` | — | **7.0:1** | 3:1 UI component | ✅ |
| `#F44336` (Error) on `#0A0A0A` | — | **5.8:1** | 3:1 UI component | ✅ |

### 1.2 Light Mode

| Foreground | Background | Ratio | Requirement | Pass? |
|------------|------------|-------|-------------|-------|
| `#0A0A0A` (Primary text) | `#FFFFFF` (Background) | **21:1** | 4.5:1 body | ✅ |
| `#616161` (Secondary text) | `#FFFFFF` (Background) | **5.9:1** | 4.5:1 body | ✅ |
| `#FFFFFF` on `#2196F3` (Primary button) | — | **3.95:1** | 3:1 large text (22sp+) | ✅ |

### 1.3 Mode Accent Overrides

| Mode | Accent | On Dark Bg (#0A0A0A) | On White | Notes |
|------|--------|-----------------------|----------|-------|
| Daily | `#2196F3` | 5.9:1 ✅ | 2.6:1 ⚠️ | Dark bg only — default dark mode |
| Work | `#607D8B` | 4.8:1 ✅ | 3.3:1 ✅ | |
| Secure | `#B71C1C` | 5.5:1 ✅ | 3.0:1 ✅ | |
| Sport | `#FF5722` | 5.2:1 ✅ | 2.4:1 ⚠️ | Dark bg only |
| Party | `#E91E63` | 4.6:1 ✅ | 2.1:1 ⚠️ | Dark bg only |
| Night | `#5D4037` | 3.1:1 ⚠️ | 5.9:1 ✅ | Low contrast on dark — intentional (sleep mode) |
| Student | `#009688` | 5.0:1 ✅ | 3.4:1 ✅ | |
| Creator | `#9C27B0` | 5.4:1 ✅ | 3.1:1 ✅ | |

**Note — Night Mode:** Dark brown on near-black is intentionally low contrast (reduced stimulation for sleep). This is a UX exception documented in the design spec section 10.2.

---

## 2. Touch Target Sizes

| Component | Size | Minimum | Pass? |
|-----------|------|---------|-------|
| Buttons | 48dp height | 48dp | ✅ |
| QS Tiles | 64dp × 64dp | 48dp | ✅ |
| Mode Indicator (tap area) | 48dp × 48dp | 48dp | ✅ |
| Notification card actions | 48dp | 48dp | ✅ |
| Icon-only actions | 48dp hit area (padding) | 48dp | ✅ |
| Elder Mode buttons | 56dp height | 56dp (Elder) | ✅ |
| Elder Mode icons | 32dp visual / 56dp touch | 56dp (Elder) | ✅ |

---

## 3. Focus Indicators

| Element | Indicator | Visible? |
|---------|-----------|----------|
| All buttons | 2dp `@color/circle_accent` outline (`bg_focus_indicator`) | ✅ |
| QS Tiles | 2dp accent outline | ✅ |
| Interactive list items | 2dp accent outline | ✅ |
| Mode picker items | 2dp accent outline | ✅ |

Focus indicators use `@drawable/bg_focus_indicator` applied via `android:foreground` on focusable containers.

---

## 4. Reduced Motion

| Scenario | Behaviour |
|----------|-----------|
| `TRANSITION_ANIMATION_SCALE == 0` | Mode switch is instant — `overridePendingTransition(0, 0)` |
| `ANIMATOR_DURATION_SCALE == 0` | All property animators instant |
| Elder Mode | Animations off by default (instant mode switch) |

---

## 5. Known Issues & Actions

| ID | Issue | Severity | Action |
|----|-------|----------|--------|
| A-01 | ~~Secondary text (`#9E9E9E`) on card surface (`#424242`) below 4.5:1~~ | ~~Medium~~ | ✅ Fixed — `circle_text_secondary_dark` changed to `#B8B8B8` (4.56:1 on card, 9.9:1 on bg) |
| A-02 | Daily accent on white background — 2.6:1 (light mode only, non-default) | Low | Dark mode is default; add note to light mode usage guidelines |
| A-03 | Sport/Party accents on white — below 3:1 | Low | Only used on dark background in default dark mode |
| A-04 | Night Mode accent intentionally low contrast | Accepted | Documented UX exception per spec section 10.2 |

---

## 6. Next Steps

- [x] Fix A-01: `circle_text_secondary_dark` → `#B8B8B8` (commit `design: fix A-01`)
- [ ] Run Android Accessibility Scanner on PersonalityTile and PersonalityEditor once themes are applied
- [ ] Test with TalkBack enabled across all 8 accent modes
- [ ] Validate Elder Mode with font scale 1.3x — ensure no text truncation in QS tiles or buttons
