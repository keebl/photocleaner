# ADR-0005: Native Android app in Kotlin + Compose, no shared code

Date: 2026-11-10
Status: accepted

## Context
PhotoCleaner should also ship on Google Play (`specs/android-app.md`). The iOS app is a
native SwiftUI + PhotoKit app whose core value is fast, smooth interaction with the
device photo library (swipe deck, large grids, duplicate scans) and the platform's own
delete/trash flows. One developer maintains both apps.

## Decision
Build a separate native Android app in Kotlin with Jetpack Compose (Material 3), living
in `android/` in this repository as a standalone Gradle project. No code is shared with
the iOS app: models and logic (duplicate detection, dHash, trash/keep stores) are ported
by hand and verified with unit tests against the same rules. The package layout mirrors
the iOS boundaries (`model/`, `source/`, `service/`, `ui/`) and keeps the `PhotoSource`
abstraction from ADR-0002, with MediaStore as the only v1 source. The NAS/SMB source is
deferred to a separate spec (`specs/android-nas.md`). The iOS code under `PhotoCleaner/`
is not changed for Android.

## Consequences
- Positive: each app uses its platform's native photo APIs, UI conventions and system
  trash dialogs without an abstraction layer; the iOS app carries no risk from Android work.
- Positive: one repository keeps specs, docs and both apps together.
- Negative: logic is duplicated in Swift and Kotlin; a rule change must be made twice and
  can drift. Mitigated by keeping the ported logic small, pure and unit-tested.
- Negative: Android v1 has no NAS source, so it is not at feature parity with iOS.

## Alternatives considered
- Kotlin Multiplatform for shared logic — adds a build and interop layer to the iOS app
  for a small amount of pure logic; not worth it for one developer.
- Cross-platform UI (Flutter, React Native) — would mean rewriting a working native iOS
  app, and photo-library performance and system dialogs need native code anyway.
- A separate repository for Android — splits specs and docs that describe one product.
