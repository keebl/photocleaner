# iOS reference screenshots

Reference for the Android port (`specs/android-app.md`, step 5): every Android screen is
compared with its iOS counterpart here. Captured on 2026-11-10 from the iOS app at commit
`5050f11` (unchanged since), iPhone 17 simulator, iOS 27.2, dark theme, Dutch UI.

The library contains only synthetic photos from `android/scripts/generate_seed_images.py`
(no personal photos). Captured with `xcrun simctl io <device> screenshot`.

| File | Screen |
|---|---|
| `01-onboarding-1.png` … `03-onboarding-3.png` | Onboarding pages (page 3 mentions NAS; Android v1 has no NAS) |
| `04-media-deck-hint.png` | Media tab, "Op deze dag" deck with the first-use swipe hint |
| `05-media-deck.png` | Deck without the hint |
| `06-media-mode-menu.png` | Mode menu: Random / Dag / Maand / Jaar |
| `07-media-kind-menu.png` | Kind menu: Foto's / Filmpjes / Dubbelen |
| `08-duplicates-exact-empty.png` | Duplicates, exact tab, empty state |
| `09-duplicates-similar.png` | Duplicates, similar tab with groups |
| `10-trash.png` | Trash tab with one item |
| `11-settings.png`, `12-settings-bottom.png` | Settings (NAS section is iOS-only) |
| `13-grid-review.png` | Grid review with selection circles and bulk actions |
| `14-permission-gate.png` | Permission gate after access was denied |
