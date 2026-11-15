# Product — PhotoCleaner

## Name
Name: PhotoCleaner — final (2026-11-14)

## Problem
Phone photo libraries grow without bound: duplicates, near-duplicates, screenshots and
forgettable shots pile up and are tedious to prune in Apple Photos. PhotoCleaner makes
cleanup fast and low-risk, one decision at a time.

## Users
- The owner of a large iPhone photo library who wants to reclaim space and tidy up.
- Users who keep (some of) their photos on a home NAS and want the same cleanup flow there.

## Why now
- Guus's own iPhone library and NAS archive have grown past the point where pruning in
  Apple Photos is practical.
- The iOS proof of concept (2026-09-08) showed the swipe flow and the 30-day trash work;
  the native Android port (2026-11) brings the same tool to Google Play, where Guus already
  has an organisation account.

## Scope (what it does)
- **Op deze dag** — a swipe stack of photos/videos taken on this date across past years;
  swipe right = keep, left = discard.
- **Random / Dag / Maand / Jaar** browsing, plus a grid review for fast bulk decisions.
- **Category filter** (iPhone library): Alle / Screenshots / Selfies / Favorieten / Panorama's.
- **Dubbelen** — exact duplicates (metadata) and similar photos (perceptual / dHash).
- **Prullenbak** — 30-day in-app retention before any real deletion.
- **Photo source** — iPhone library (PhotoKit) or a NAS folder over SMB.

## Non-goals (what it deliberately does not do)
- No cloud upload, no analytics, no third-party services; nothing leaves the device except
  traffic to the user's own NAS. The optional Pro purchase is handled by the store (Google
  Play, later the App Store); the app learns only whether Pro is owned.
- No "Mensen" or "Documenten" category filter — Apple does not expose face/ML albums to
  third-party apps.
- No barcode/QR filtering — would require scanning the whole library with image recognition.
- Google Photos was dropped: its API cannot delete, which defeats a cleanup app (ADR-0003).

## Success criteria
- A user can review and discard a day's photos in seconds without fear of permanent loss.
- Duplicate detection surfaces real duplicates without iCloud downloads, fast on re-scan.

## Platforms
- iOS 17+ (iPhone), SwiftUI — see `docs/ARCHITECTURE.md` for the stack.
- Android 11+ (phone, portrait), Kotlin + Jetpack Compose in `android/`, for Google Play
  (`specs/android-app.md`, ADR-0005). Same features as iOS, including the NAS source
  (`specs/android-nas.md`, local network only — ADR-0006), except the Selfies category
  (Android has no selfie flag). After 30 days in the in-app trash, phone items move to
  Android's own trash and NAS items are deleted on the NAS, each after the user confirms.

UI: yes

## Data, privacy and money
- Personal data: the user's own photos/videos and NAS credentials — purpose per item in
  `SECURITY.md`. Nothing is sent off-device except to the user's NAS.
- Accounts: none.
- Monetization (decided 2026-11-11): one-time €4.99 "PhotoCleaner Pro" unlock, no ads,
  subscription or trial (`specs/pro-unlock.md`). Free forever: "Op deze dag" for today
  (photos), keep/discard/undo, category filter and sort, zoom and share, the trash, the
  reminder, theme, and the duplicate scan with its results. Pro adds Random / Dag / Maand /
  Jaar browsing of any date, videos, the grid and discarding duplicates. Android built
  2026-11-15 (Google Play Billing, no backend; the purchase is cached on-device); iOS
  (StoreKit 2) follows before any App Store release. TODO(guus): if sold in the EU, the DSA "trader" rules
  require public contact details on the store page.

## Open product questions
- Android: DSA trader declaration on Google Play — the organisation account is declared as a
  trader, so its name and address are public on the listing (see `docs/PLAYSTORE.md`).
- TODO(guus): Is an App Store release the goal, or personal/sideload use only?
  (`docs/APPSTORE.md` exists but no privacy policy URL yet.)
