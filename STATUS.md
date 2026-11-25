# Status — PhotoCleaner

Last updated: 2026-11-25

## Now
- Android app built on branch `feat/android-app` (`specs/android-app.md` steps 1–13 done):
  all v1 features, release bundle configuration, store assets (`store/play/`), privacy
  policy and `docs/PLAYSTORE.md`. Merged to `main` (PR 1).
- Android NAS (SMB) source built (`specs/android-nas.md`, ADR-0006), merged to `main` (PR 3):
  link screen, source menu, Fotobron in Settings, scan/previews/duplicates/zoom/video/share,
  permanent NAS delete after the in-app trash. Tested on the emulator against a local Samba
  server (`android/scripts/nas-test-server.sh`); not yet against a real NAS.
- Android Pro unlock built (`specs/pro-unlock.md` steps 1–7), merged to `main` (PR 7): Play Billing 9.1.0, entitlement cache, Pro sheet, Settings row, locks on
  the media tab and duplicate discard; version 1.0 (2). Tested with `FakeBilling` (unit and
  emulator); not yet against Play (needs the Console product, step 8).
- Play organisation account verification pending (Guus).
- Android 1.0 (2) with `BILLING` published to Play internal testing on 2026-11-16 (1.0 (1) was
  there since 2026-11-11). Review warning only: no native debug symbols uploaded.
- Play (2026-11-20, hand-off): closed test "Alpha" (release 2 (1.0), 178 countries, testers
  "Family and friends") plus all store listing, store settings and app-content changes are in
  Google's review ("Wijzigingen worden beoordeeld"); managed publishing is off, so the test goes
  live for the testers once approved. Default listing is en-US only. Main has versionCode 3 with
  the optional portrait feature (PR 11), not yet built or uploaded.
- Play review approved: mission control read it in the Console on 2026-11-25 (submission 1 of
  2026-11-20 07:04, "Gesloten test - Alpha, Winkelvermelding, App-content, Store-instellingen":
  "Gepubliceerd"; no pending changes). Closed test "Alpha" (1.0 (2)) is live for the testers.
- Open asks: none. Answered: `ask-8de865ce` (Play review → approved, 2026-11-25),
  `ask-3f9c2a71` (route → closed test first), `ask-7c1e4b90` (portrait warning → proceed anyway,
  fix in 1.0 (3)).
- Open pull requests (2026-11-25), all Dependabot, all green, merged by mission control:
  PR 12 (`actions/setup-java` 5 → 6), PR 13 (AGP 9.5.1 → 9.5.2, head `aa48e07`), PR 14
  (kotlinx-serialization-json 1.12.0 → 1.13.0, head `b310123`). PR 13 and 14 were red because
  Dependabot does not update `verification-metadata.xml`; fixed with
  `android/scripts/verification-metadata-bump.py` (CLAUDE.md Gotchas).
- Project main sessions were removed on 2026-11-24: sessions report to mission control directly.

## Next
1. Play review approved 2026-11-25. Next: Pro step 8 on a Play-installed build (item 3,
   `docs/testing/pro-play-test.md`).
   Still open in the Console: nl-NL listing translation (`store/play/listing.md`,
   `screenshots/nl/`), DSA trader status.
   Before production: Guus builds the signed bundle 1.0 (3) (versionCode 3 on `main`) in his
   own Terminal, after PR 13/14 are merged, as one line:
   `cd /Users/guus/Claude/Projects/photo-cleaner && git switch main && git pull --ff-only && cd android && export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home && ./gradlew --stop && ./gradlew :app:lintRelease :app:bundleRelease`
   (the `--stop` clears a Gradle daemon started inside Claude's sandbox; signed only when
   `android/keystore.properties` exists) → `android/app/build/outputs/bundle/release/app-release.aab`;
   upload it to the closed test and promote to production.
2. Done 2026-11-16: merchant account set up; `pro_lifetime` active (option `buy`, NL €4.99;
   Play's conversion put the other euro countries at €5.99, US $5.49); license testers = list
   "Family and friends" (6), RESPOND_NORMALLY. Open: family sharing (no setting found in the
   2026 Console). Done 2026-11-19: all 33 EUR-priced countries set to €4.99 and saved (Guus's choice). Guus enrolled in the 15% service fee on 2026-11-19. Original plan:
   create the one-time product `pro_lifetime` at €4.99 with family sharing and add license testers
   (`docs/PLAYSTORE.md` step 7a). Play allows products only after a `BILLING` build is in a
   track.
3. Pro step 8 (Guus, ~45 min): follow `docs/testing/pro-play-test.md` on a real phone
   installed from the closed-test opt-in link — price, cancel, declined card, pending (slow
   test card), acknowledgement, offline start, restore after reinstall, refund → locked, buy
   again; this also confirms billing works without `ACCESS_NETWORK_STATE`. Record the run log
   here.
4. Pro step 9: iOS StoreKit 2 round (`specs/pro-unlock.md`); decide first whether the NAS
   source is free or Pro on iOS (Open questions).
5. Guus: try the Android NAS source on a phone against the real NAS (Synology), incl. a
   `.local` name, a large folder and HEIC/RAW files; decide the Play data-safety answer for
   NAS traffic (`docs/PLAYSTORE.md`).
6. Test the Android app on a physical phone (so far emulator only, API 36): partial access,
   system trash dialog, reminder timing, video playback.
7. iOS: run `swiftformat PhotoCleaner` once as its own commit (scheme shared and CI added 2026-11-14).

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
- 2026-11-15 — Android Pro: `ACCESS_NETWORK_STATE` stays removed with Play Billing; its usage logger then drops its events (spec Plan, step 1 finding)
- 2026-11-11 — Monetization: one-time €4.99 "PhotoCleaner Pro", today's "Op deze dag" stays free (`specs/pro-unlock.md`; on `main` since 2026-11-15)
- 2026-11-14 — Android NAS: local network only, `INTERNET` added — see `docs/decisions/0006-android-local-network-access.md`
- 2026-11-14 — Android NAS: smbj + rapid7 dcerpc (share list as on iOS); NAS items deleted permanently after confirmation (`specs/android-nas.md`)
- 2026-11-10 — Android: "Op deze dag" includes this year's photos (iOS parity); spec AC 4 updated.
- 2026-11-10 — Android: `RECEIVE_BOOT_COMPLETED` allowed so the reminder survives a reboot; spec AC 21 updated.
- 2026-11-10 — Native Android app in `android/`, no shared code — see `docs/decisions/0005-native-android-app.md`
- 2026-11-08 — Adopted the project standard — see `docs/decisions/0001-record-architecture-decisions.md`

## Deferred
- `design-refs` — the references are the 14 iOS screenshots in `design/refs/ios/`, described in `docs/DESIGN.md` (Guus, 2026-11-14); the checker only counts files directly in `design/refs/`

## Workbench feedback
- 2026-11-25 — The `heavy ./gradlew` vetter refuses `--write-verification-metadata`, so every Dependabot Gradle PR (always red: no metadata update) needs a Terminal step or the hash script; worth a sanctioned form or a CI job that regenerates on Dependabot branches.
- 2026-11-19 — Android builds still cannot run through the Bash sandbox: the proxy blocks Google Maven, `--offline` fails on the blocked system temp dir, and the sandbox-started Gradle daemon is then reused by the Terminal panel ("plugin not found" in 300 ms) until `./gradlew --stop`. Reported to mission control at Guus's request.
- 2026-11-15 — Claude's Bash sandbox blocks `~/.gradle` (wrapper lock) and `gh` fails TLS (OSStatus -26276): the release build ran in the Terminal panel and PR 7 was merged with git. Worth a line in handbook 80.
- 2026-11-14 — `tests` counts only the inferred stack (swift); the 121 Android unit tests in `android/` did not satisfy it. Multi-app repos need per-app detection.
- 2026-11-14 — `toolbox.json` `apple` and `google-play` limits say "through Expo/EAS", but this project is native Xcode + Gradle: no Expo path applies.
- 2026-11-14 — `templates/github/workflows/ci-swift.yml` runs `-destination 'platform=macOS' test`, which fails for an iOS-only app; it needs an iOS Simulator destination and a shared scheme (here added by hand).
- 2026-11-14 — "Repairs only add" made outdated lines unfixable in place (CLAUDE.md "Test: none yet", STATUS Health "Tests: none yet"): two `.proposed.md` files for one-line corrections.
- 2026-11-14 — The `tests` repair needed a new Xcode target, which `~/.claude/rules/swift.md` forbids by hand-editing `project.pbxproj`; the standard has no sanctioned path (XcodeGen?).
- 2026-11-14 — Play Console lookup for `## Tools` failed: the built-in browser has no Google sign-in; the toolbox gives no other access path.

- 2026-11-14 — `/main-session`: the session-start check ran on the main checkout's stale, already-merged `feat/android-app` branch and reported a missing `## Tools` that `main` has; Start/Intake has no step to put the checkout back on an up-to-date `main` first.
- 2026-11-14 — `/main-session` Start pins before filing in the group, and `move_sessions` unpins: re-pin needed (also seen in oakbook).
- 2026-11-14 — `/main-session` merge rule needs "green and reviewed"; Dependabot PRs never get a review, so they always wait on Guus (also seen in Puzzels).
- 2026-11-14 — Setting `UI: yes` (product-complete) opened three new design items mid-round; and `design-refs` counts only top-level files, so `design/refs/ios/` (14 screenshots) scored 0.
- 2026-11-14 — `tools` did not infer SonarCloud: the project uses automatic analysis (no `sonar-project.properties`), so it only showed up as a PR comment.

## Health
- iOS: Version 1.0 (build 1) · Build: green (xcodebuild, iOS Simulator) · Tests: 11 unit (PhotoCleanerTests), green ·
  Store: not submitted · Dependencies: SPM, `Package.resolved` committed
- Android: Version 1.0 (2) · Build: green (`assembleDebug`, `bundleRelease`) · Tests: 155 unit,
  81 instrumented (8 NAS tests skipped without the Samba server), all green · Lint: 0 errors · Store: not submitted (account verification
  pending) · Dependencies: Gradle version catalog, wrapper pinned by SHA-256
