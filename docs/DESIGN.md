# Design — PhotoCleaner

The visual target in words. Claude reads this and the images in `design/refs/` before any
UI or illustration work, and compares its output against them before calling it done.

Reconstructed 2026-11-14 from the iOS code and the reference screenshots; this describes the
app as built, not a new direction. Lines marked (assumption) are inferred, not decided.

## Mood (three to five words)
Calm, native, photo-first, decisive. The photos carry the colour; the chrome stays quiet
and system-standard so one keep/discard decision at a time feels safe. (assumption)

## References
`design/refs/ios/` contains 14 screenshots of the iOS app (dark theme, Dutch UI, synthetic
photos; captured 2026-11-10, see its `README.md`). They are the reference for both apps, and
every Android screen is compared with its iOS counterpart. What to take from them:
- `05-media-deck.png` — the core screen: source pill and filter pills at the top, a large
  rounded photo card, red "Weggooien" left and green "Behouden" right, floating tab bar.
- `09-duplicates-similar.png` — grouped cards on a dark surface: green ring = keep, red ring
  = discard, a short hint card, a red destructive action per group.
- `13-grid-review.png` — dense grid with selection circles and bulk actions.
- `10-trash.png` — the 30-day trash: days left per item, restore before delete.
- `01-onboarding-1.png` … `03-onboarding-3.png` — large SF Symbol, short title, one sentence.

## Palette
- Background / surface / text: system colours (`.primary`, `.secondary`, `.tertiary`,
  grouped backgrounds); `.ultraThinMaterial` / `.regularMaterial` for floating controls.
- Accent: `AccentColor` asset, sRGB (0.20, 0.50, 0.90) ≈ `#3380E6`, used for pills, links
  and the selected tab.
- Meaning colours: green = keep, red = discard / destructive. Never swap or reuse them for
  decoration.
- Light and dark both supported (Weergave: Systeem / Licht / Donker), **dark by default**
  (`ThemeManager`). Tokens and system colours only; the few `.white` foregrounds sit on
  photos or scrims, not on surfaces.

## Typography
- System font (SF Pro on iOS, the platform default on Android) with Dynamic Type text
  styles: `.title`/`.title2` for screen titles, `.headline`/`.subheadline` for rows,
  `.caption` for metadata. Fixed sizes (52–96 pt) only for onboarding and empty-state
  symbols.

## Shape, density, detail
- Corners: 22 pt for the photo card and group cards, 12–16 pt for hint cards and sheets,
  8 pt for thumbnails. Capsule pills for source and filter menus. No borders except the
  green/red selection rings; no drop shadows beyond the system materials.
- Density: generous around the single-photo deck; dense in the grid and duplicate views.
- Detail level for illustrations: none in the app; SF Symbols (iOS) and Material-style vector
  drawables (`android/app/src/main/res/drawable/ic_*.xml`) only. The app icon and Play feature graphic live in `design/icon/`.

## Motion
Subtle and physical: spring / snappy animations for swipes and card changes, ease-out for
fades. Light haptic taps on deck and grid actions, a warning haptic before deleting from
the trash (`Haptics`).

## Language and accessibility
- iOS: Dutch UI, strings in code (no string catalog yet). Android: Dutch and English
  (`values-nl`, `values`). Copy is short and plain; destructive actions say what happens
  ("Gooi 1 weg").
- Icon-only controls carry accessibility labels (15 on iOS); every new one gets one.
  (assumption: not audited screen by screen)
- Dynamic Type through text styles; meaning never by colour alone (arrows and words next
  to red/green). (assumption: contrast not yet measured)
- Keep tap targets away from the left screen edge (iOS back-gesture gate, CLAUDE.md).

## Avoid
- Custom branding colours on the chrome, gradients or decoration that compete with photos.
- Any destructive action without the trash step or a confirmation.
- Red and green for anything other than discard and keep.

## Asset conventions
- Icons: SF Symbols (iOS), vector drawables `ic_*.xml` (Android); custom artwork as SVG in `design/icon/`.
- Store graphics: `store/`; generated images follow `/image` with a `.txt` sidecar.
