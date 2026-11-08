# Product — PhotoCleaner

## Problem
Phone photo libraries grow without bound: duplicates, near-duplicates, screenshots and
forgettable shots pile up and are tedious to prune in Apple Photos. PhotoCleaner makes
cleanup fast and low-risk, one decision at a time.

## Users
- The owner of a large iPhone photo library who wants to reclaim space and tidy up.
- Users who keep (some of) their photos on a home NAS and want the same cleanup flow there.

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
  traffic to the user's own NAS.
- No "Mensen" or "Documenten" category filter — Apple does not expose face/ML albums to
  third-party apps.
- No barcode/QR filtering — would require scanning the whole library with image recognition.
- Google Photos was dropped: its API cannot delete, which defeats a cleanup app (ADR-0003).

## Success criteria
- A user can review and discard a day's photos in seconds without fear of permanent loss.
- Duplicate detection surfaces real duplicates without iCloud downloads, fast on re-scan.

## Platforms
iOS 17+ (iPhone), SwiftUI — see `docs/ARCHITECTURE.md` for the stack.

## Data, privacy and money
- Personal data: the user's own photos/videos and NAS credentials — purpose per item in
  `SECURITY.md`. Nothing is sent off-device except to the user's NAS.
- Accounts: none.
- Monetization: none decided yet. TODO(guus): if sold in the EU, the DSA "trader" rules
  require public contact details on the store page.

## Open product questions
- TODO(guus): Is an App Store release the goal, or personal/sideload use only?
  (`docs/APPSTORE.md` exists but no privacy policy URL yet.)
