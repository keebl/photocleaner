# PhotoCleaner

Native iOS app (SwiftUI + PhotoKit) to clean up a photo library — scope and non-goals in
`docs/PRODUCT.md`. Read `STATUS.md` first.

## Commands
- Install deps: none — Swift Package Manager resolves on build (`Package.resolved` committed)
- Build: `xcodebuild build -project PhotoCleaner.xcodeproj -scheme PhotoCleaner -destination 'generic/platform=iOS Simulator' -configuration Debug CODE_SIGNING_ALLOWED=NO`
- Run on simulator (after a build): `xcrun simctl install booted com.guus.photocleaner` then `xcrun simctl launch booted com.guus.photocleaner` (locate the built `.app` under DerivedData/.../Build/Products/Debug-iphonesimulator/)
- Run on device / in Xcode: `open PhotoCleaner.xcodeproj`, pick a target, press ▶︎
- Test (all): none yet — no test target exists. TODO(guus): add `PhotoCleanerTests`
- Typecheck: covered by `xcodebuild build` (no separate step for Swift)
- Lint / format: `swiftformat --lint PhotoCleaner` / `swiftformat PhotoCleaner`
- Screenshot (UI check): `xcrun simctl io booted screenshot /tmp/shot.png`
- Where it runs: iOS Simulator (iPhone 17, iOS 27) or a physical iPhone

## Gotchas
- The `PhotoCleaner` scheme is not shared (it lives in git-ignored `xcuserdata/`). Xcode
  regenerates it locally, but CI would need a shared scheme. TODO(guus).
- Existing code does not pass `swiftformat` yet (30/33 files). Run `swiftformat PhotoCleaner`
  once as its own commit before relying on the format hook.
- Left screen edge reserves touches ~2s for the iOS back-gesture ("system gesture gate"
  log is benign). Keep tap targets away from the left edge in review views.
- Simulator test results can be contaminated if someone else taps the same simulator while
  Claude tests — re-test as the sole operator before concluding a feature is broken.

## Project conventions (only what differs from the global rules)
- NAS/SMB password lives only in the iOS Keychain
  (`kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`), never in code or UserDefaults.
- This is a public repo: no secrets, keys, or personal photos committed.

## Where to look
- `STATUS.md` — current state and next steps
- `docs/PRODUCT.md` — scope, users, non-goals
- `docs/ARCHITECTURE.md` and `docs/decisions/` — why things are the way they are
- `specs/` — feature specs (written by `/new-feature`)
- `docs/APPSTORE.md` — App Store submission notes; `docs/INSTALL-OP-TELEFOON.md` — sideload guide
