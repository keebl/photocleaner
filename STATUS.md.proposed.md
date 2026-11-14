<!-- Proposal (2026-11-14, standard repair round): Health now has iOS tests, and Next item 5 is partly done.
     On approval: replace STATUS.md with this file minus this comment, then delete this file. -->
# Status — PhotoCleaner

Last updated: 2026-11-14

## Now
- Android app built on branch `feat/android-app` (`specs/android-app.md` steps 1–13 done):
  all v1 features, release bundle configuration, store assets (`store/play/`), privacy
  policy and `docs/PLAYSTORE.md`. Not merged to `main` yet.
- Android NAS (SMB) source built on `feat/android-app` (`specs/android-nas.md`, ADR-0006):
  link screen, source menu, Fotobron in Settings, scan/previews/duplicates/zoom/video/share,
  permanent NAS delete after the in-app trash. Tested on the emulator against a local Samba
  server (`android/scripts/nas-test-server.sh`); not yet against a real NAS.
- Play organisation account verification pending (Guus).

## Next
1. Guus: open the PR for `feat/android-app`, review and merge (the privacy policy URL on
   `main` is needed for the Play listing).
2. Guus: create the upload key and `android/keystore.properties`, then follow
   `docs/PLAYSTORE.md` (Console forms, internal testing, production).
3. Guus: try the Android NAS source on a phone against the real NAS (Synology), incl. a
   `.local` name, a large folder and HEIC/RAW files; decide the Play data-safety answer for
   NAS traffic (`docs/PLAYSTORE.md`).
4. Test the Android app on a physical phone (so far emulator only, API 36): partial access,
   system trash dialog, reminder timing, video playback.
5. iOS: run `swiftformat PhotoCleaner` once as its own commit (scheme shared and CI added 2026-11-14).

## Blocked / open questions
- README's architecture section names `NASFolderSource`, but the code implements `SMBSource`.
  Which name is intended? (ARCHITECTURE.md documents the current `SMBSource`.) TODO(guus).
- Android, to confirm: exact duplicates are not split by file size (spec AC 10; iOS does
  split), and photos from 29 February only appear on 29 February (iOS parity).
- Android NAS: the media-permission gate still blocks the app for a NAS-only user (phone
  library access is required first, as on iOS). Follow-up if NAS-only use matters.
- Android emulator for API 30 not yet installed; the spec's manual check asks for API 30 and
  the latest API.

## Risks
1. Technical — data loss: the app deletes the user's own photos (NAS items permanently).
   iOS has no automated tests yet; a bug in the trash or duplicate logic deletes the wrong
   files. Android has unit and instrumented tests, but the NAS path is tested against a
   local Samba server only, not a real NAS.
2. Product — release blocked on accounts: Apple Developer is not enrolled (no TestFlight
   or App Store), and the Play organisation account verification is still pending.
3. Legal — store declarations: the Play DSA trader status makes the organisation's name
   and address public, and the Play data-safety answer for NAS traffic is still open
   (`docs/PLAYSTORE.md`); the privacy policy must stay true to "nothing leaves the device
   except to the user's NAS".

## Recent decisions
- 2026-11-14 — Android NAS: local network only, `INTERNET` added — see `docs/decisions/0006-android-local-network-access.md`
- 2026-11-14 — Android NAS: smbj + rapid7 dcerpc (share list as on iOS); NAS items deleted permanently after confirmation (`specs/android-nas.md`)
- 2026-11-10 — Android: "Op deze dag" includes this year's photos (iOS parity); spec AC 4 updated.
- 2026-11-10 — Android: `RECEIVE_BOOT_COMPLETED` allowed so the reminder survives a reboot; spec AC 21 updated.
- 2026-11-10 — Native Android app in `android/`, no shared code — see `docs/decisions/0005-native-android-app.md`
- 2026-11-08 — Adopted the project standard — see `docs/decisions/0001-record-architecture-decisions.md`

## Deferred
- `design-refs` — the references are the 14 iOS screenshots in `design/refs/ios/`, described in `docs/DESIGN.md` (Guus, 2026-11-14); the checker only counts files directly in `design/refs/`

## Workbench feedback
- 2026-11-14 — `tests` counts only the inferred stack (swift); the 121 Android unit tests in `android/` did not satisfy it. Multi-app repos need per-app detection.
- 2026-11-14 — `toolbox.json` `apple` and `google-play` limits say "through Expo/EAS", but this project is native Xcode + Gradle: no Expo path applies.
- 2026-11-14 — `templates/github/workflows/ci-swift.yml` runs `-destination 'platform=macOS' test`, which fails for an iOS-only app; it needs an iOS Simulator destination and a shared scheme (here added by hand).
- 2026-11-14 — "Repairs only add" made outdated lines unfixable in place (CLAUDE.md "Test: none yet", STATUS Health "Tests: none yet"): two `.proposed.md` files for one-line corrections.
- 2026-11-14 — The `tests` repair needed a new Xcode target, which `~/.claude/rules/swift.md` forbids by hand-editing `project.pbxproj`; the standard has no sanctioned path (XcodeGen?).
- 2026-11-14 — Play Console lookup for `## Tools` failed: the built-in browser has no Google sign-in; the toolbox gives no other access path.

- 2026-11-14 — Setting `UI: yes` (product-complete) opened three new design items mid-round; and `design-refs` counts only top-level files, so `design/refs/ios/` (14 screenshots) scored 0.

## Health
- iOS: Version 1.0 (build 1) · Build: green (xcodebuild, iOS Simulator) · Tests: 11 unit (PhotoCleanerTests), green ·
  Store: not submitted · Dependencies: SPM, `Package.resolved` committed
- Android: Version 1.0 (1) · Build: green (`assembleDebug`, `bundleRelease`) · Tests: 97 unit,
  34 instrumented, all green · Lint: 0 errors · Store: not submitted (account verification
  pending) · Dependencies: Gradle version catalog, wrapper pinned by SHA-256
