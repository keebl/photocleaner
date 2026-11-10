# PhotoCleaner

Native iOS app (SwiftUI + PhotoKit) to clean up a photo library — scope and non-goals in
`docs/PRODUCT.md`. Read `STATUS.md` first. A native Android port (Kotlin + Compose) lives
in `android/` (`specs/android-app.md`, ADR-0005).

## Commands (iOS)
- Install deps: none — Swift Package Manager resolves on build (`Package.resolved` committed)
- Build: `xcodebuild build -project PhotoCleaner.xcodeproj -scheme PhotoCleaner -destination 'generic/platform=iOS Simulator' -configuration Debug CODE_SIGNING_ALLOWED=NO`
- Run on simulator (after a build): `xcrun simctl install booted com.guus.photocleaner` then `xcrun simctl launch booted com.guus.photocleaner` (locate the built `.app` under DerivedData/.../Build/Products/Debug-iphonesimulator/)
- Run on device / in Xcode: `open PhotoCleaner.xcodeproj`, pick a target, press ▶︎
- Test (all): none yet — no test target exists. TODO(guus): add `PhotoCleanerTests`
- Typecheck: covered by `xcodebuild build` (no separate step for Swift)
- Lint / format: `swiftformat --lint PhotoCleaner` / `swiftformat PhotoCleaner`
- Screenshot (UI check): `xcrun simctl io booted screenshot /tmp/shot.png`
- Where it runs: iOS Simulator (iPhone 17, iOS 27) or a physical iPhone

## Commands (Android) — run from `android/`
- Environment: `export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home`
  and `export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools` (Homebrew
  `openjdk@17` + `android-commandlinetools`; `android/local.properties` holds `sdk.dir`)
- Install deps: none — Gradle resolves on build (wrapper pinned with a SHA-256 checksum)
- Build: `./gradlew :app:assembleDebug`
- Test (unit): `./gradlew :app:testDebugUnitTest`
- Test (instrumented, emulator running): `./gradlew :app:connectedDebugAndroidTest`
- Lint: `./gradlew :app:lintDebug` (report in `app/build/reports/lint-results-debug.txt`)
- Start emulator: `$ANDROID_HOME/emulator/emulator -avd pc_api36 -no-snapshot-save -no-audio &`
- Run on emulator (after a build): `adb install -r app/build/outputs/apk/debug/app-debug.apk`
  then `adb shell am start -n com.guus.photocleaner/.MainActivity`
- Seed test photos (emulator running, needs Pillow): `scripts/seed-emulator.sh`
  (`--clean` removes them)
- Screenshot (UI check): `adb exec-out screencap -p > /tmp/android-shot.png`
- Where it runs: Android Emulator (AVD `pc_api36`, Pixel 8, API 36) or a physical phone

## Gotchas
- The `PhotoCleaner` scheme is not shared (it lives in git-ignored `xcuserdata/`). Xcode
  regenerates it locally, but CI would need a shared scheme. TODO(guus).
- Existing code does not pass `swiftformat` yet (30/33 files). Run `swiftformat PhotoCleaner`
  once as its own commit before relying on the format hook.
- Left screen edge reserves touches ~2s for the iOS back-gesture ("system gesture gate"
  log is benign). Keep tap targets away from the left edge in review views.
- Android: `compileSdk` is 37 because current AndroidX/Compose releases require it;
  `targetSdk` stays 36 until Play's required level is checked in spec step 12.
- Android: the system `java` wrapper finds no JDK (Homebrew's `openjdk@17` is keg-only and
  not symlinked), so `JAVA_HOME` must be set before `./gradlew`.
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
