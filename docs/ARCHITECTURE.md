# Architecture — PhotoCleaner

This document explains *why* the code is shaped the way it is. The *what* is in the code.

## Overview
A single SwiftUI iPhone app (iOS 17+, Swift 5). The UI and cleanup logic are decoupled from
storage by a `PhotoSource` protocol, so the same review/duplicate/trash flows run against
either the iPhone library or a NAS. State is local-first: decisions and the trash live
on-device; nothing is uploaded. Duplicate detection runs on-device with on-disk hash caching.

## Key decisions
| Decision | Why | ADR |
|---|---|---|
| `PhotoSource` abstraction over storage | Reuse one UI/cleanup flow for iPhone library and NAS | `docs/decisions/0002-photosource-abstraction.md` |
| Local-first + 30-day in-app trash | Make deletion reversible and never upload photos | `docs/decisions/0003-local-first-trash.md` |
| Dropped Google Photos source | Its API cannot delete, which defeats a cleanup app | `docs/decisions/0004-drop-google-photos.md` |

## Boundaries
- `Models/` — source-independent types (`PhotoAsset`, `PhotoCategory`, `SourceKind`,
  `SMBCredentials`). No UI, no PhotoKit.
- `Sources/` — `PhotoSource` protocol and its implementations: `PhotoKitSource` (iPhone
  library) and `SMBSource` (NAS over SMB), plus `ImageHashing`. The UI depends on the
  protocol, not the concrete source.
- `Services/` — cross-cutting stores and helpers: `SourceManager`, `TrashStore`,
  `KeepStore`, `DuplicateDetector`, `PerceptualDetector`, `HashCache`, `KeychainStore`,
  `NotificationManager`, `ThemeManager`, `Haptics`, `PhotoLibrary`.
- `Views/` — SwiftUI screens (review deck, grid review, duplicates, trash, settings, SMB
  connect). Views talk to sources through `SourceManager` and the stores.

> Note: the README's architecture section still calls the NAS source `NASFolderSource`; the
> code implements it as `SMBSource`. TODO(guus) to reconcile the naming.

## Data and state
- Persistence: iOS Photo Library / NAS files are the source of truth; the app stores only
  decisions (keep/trash), a 30-day trash list, and on-disk perceptual-hash caches (Caches,
  validated on modification date).
- Client state: SwiftUI `@State` / `@AppStorage` / `@EnvironmentObject` stores.
- NAS access persisted as a security-scoped bookmark; credentials in the Keychain.

## External services
- The user's NAS over SMB — used for the NAS photo source — failure handled by surfacing
  connection errors in `SMBConnectView`; no retries against unknown hosts.
- No other network services. No analytics, no third-party SDKs.

## Testing strategy
No automated tests yet (TODO). Verification is manual in the iOS Simulator and on-device;
the perceptual/duplicate logic is the first candidate for unit tests.

## Known debt
- `swiftformat` not yet applied to existing code (30/33 files) — accepted until a dedicated
  format commit.
- `PhotoCleaner` scheme not shared, blocking CI — accepted until CI is added.
- No unit tests — accepted for a proof-of-concept; revisit before an App Store release.
