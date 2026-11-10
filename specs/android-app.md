# Spec: Android app (Play Store release)

Status: approved
Date: 2026-11-10

## Goal
The owner of a large Android photo library gets the same fast, low-risk cleanup flow the
iOS app offers (swipe to keep/discard, duplicates, 30-day trash), as a native Android app
that can be published on Google Play.

## Users and context
- Android phone users (Android 11+) who want to prune the photos and videos stored on
  their device. Dutch and English UI, following the system language.
- Distribution: Google Play, organisation developer account. Free, no ads, no accounts.

## Scope (in)
- Native app in Kotlin + Jetpack Compose (Material 3), living in `android/` in this repo.
  The iOS app is not changed.
- Photo source: the device library through MediaStore (images and videos).
- **Op deze dag** swipe deck: right = keep, left = discard, with undo of the last swipe.
- **Random / Dag / Maand / Jaar** browsing and the **grid review** for bulk decisions.
- **Category filter**: Alle / Screenshots / Favorieten / Panorama's (no Selfies on Android).
- **Dubbelen**: exact duplicates (capture second + dimensions) and similar photos (dHash +
  Hamming distance), with an on-disk hash cache — same rules as `DuplicateDetector`,
  `PerceptualDetector` and `ImageHashing` on iOS.
- **Prullenbak**: 30-day in-app retention, restore, empty now, lifetime "cleaned" counter.
- Keep-store: kept items never come back in review views; reset in Settings.
- Photo zoom, video playback, share button.
- Settings: theme (system/light/dark), daily "Op deze dag" reminder with time, keep reset,
  version.
- Onboarding and a permission gate.
- Play Store deliverables: signed release App Bundle (`.aab`), privacy policy page, store
  listing texts (NL/EN), icon, feature graphic, phone screenshots, and
  `docs/PLAYSTORE.md` with the Console steps (data safety, photo-permission declaration,
  content rating, internal test, production release).

## Non-goals (out)
- NAS/SMB source — separate spec (`specs/android-nas.md`) after v1.
- Sharing code with the iOS app (no Kotlin Multiplatform); logic is ported by hand.
- Tablet / foldable layouts, Wear, TV. Phone portrait only.
- `MANAGE_EXTERNAL_STORAGE` ("all files access") — Play rejects it for this use case.
- Photos that exist only in Google Photos' cloud (not in MediaStore).
- Analytics, crash reporting SDKs, ads, in-app purchases, accounts.
- Uploading to the Play Console and creating the upload key: done by Guus (credentials).

## Acceptance criteria
1. Given a fresh install, when the app starts, then onboarding is shown once and the
   system photo/video permission is requested afterwards.
2. Given permission is denied, when the app is opened, then a gate screen explains why
   access is needed and offers a button to the system settings; no photos are shown.
3. Given Android 14+ and "selected photos only" access, when the app is opened, then it
   works on the selected items and shows a banner offering to grant full access.
4. Given photos exist that were taken on today's month/day in any year (including this
   year, as on iOS), when "Op deze dag" opens, then exactly those items (minus kept and
   trashed ones) are in the deck.
5. Given a card in the deck, when it is swiped right, then its id is stored as kept and it
   does not reappear in any review view after an app restart.
6. Given a card in the deck, when it is swiped left, then it is added to the in-app trash
   with the current date and disappears from all review views, while the file stays on
   the device.
7. Given a swipe was just made, when undo is tapped, then the last decision is reverted
   and the card returns.
8. Given the grid review, when several items are selected and "discard" is tapped, then
   all selected items move to the in-app trash in one action.
9. Given a category is chosen, when a review view loads, then only items of that category
   are shown; "Alle" shows everything.
10. Given two items with the same capture second and pixel dimensions, when the exact
    duplicate scan runs, then they appear as one group whose suggested keep is the item
    with most pixels (tie: largest file).
11. Given two images whose dHash values differ by at most the configured Hamming distance,
    when the similar scan runs, then they appear in the same group.
12. Given a completed similar scan, when it is run again without library changes, then no
    image is re-hashed (hashes come from the cache, validated on modification date).
13. Given an item in the trash, when "restore" is tapped, then it leaves the trash,
    reappears in review views and the cleaned counter decreases by one.
14. Given trash items older than 30 days, when the app is opened, then the user is asked
    to confirm their removal and, after the Android system dialog is confirmed, they are
    moved to Android's system trash and removed from the in-app trash list.
15. Given the system trash dialog is cancelled, when control returns to the app, then
    nothing is moved and the items stay in the in-app trash.
16. Given a trashed or kept item was deleted outside the app, when the app loads, then it
    is silently dropped from the trash/keep lists.
17. Given the reminder is switched on and notification permission is granted, when the
    chosen time passes, then one "Op deze dag" notification is posted that day and tapping
    it opens the deck.
18. Given notification permission is refused, when the reminder is switched on, then the
    switch returns to off and a hint explains how to enable notifications.
19. Given the system language is Dutch, then all UI text is Dutch; for any other language
    it is English.
20. Given the release build, when `./gradlew :app:bundleRelease` runs with a configured
    upload key, then a signed `.aab` is produced that targets the API level Google Play
    requires at submission time.
21. Given the merged manifest of the release build, then it requests no `INTERNET`
    permission and no permissions beyond media read (images/video/user-selected), and
    post-notifications.

## Edge cases and error states
- Empty library, or no photos on this day: friendly empty state with a way to another mode.
- Permission revoked while the app is in the background: return to the gate, no crash.
- Items on a removable SD card that was ejected: treated as deleted outside the app (16).
- Corrupt or unreadable image: placeholder thumbnail, skipped by the similar scan.
- Items without a capture date: fall back to MediaStore `DATE_ADDED` for browsing; never
  grouped as exact duplicates.
- Very large libraries (50k+): paged loading; scans are cancellable and show progress.
- Process death during a scan: hash cache keeps partial progress.
- Videos: playable in deck and zoom; excluded from the similar-photo scan.

## Data and privacy
- Reads: the user's photos and videos (pixels for thumbnails/hashes; metadata: date,
  size, dimensions, name, folder, favorite flag). Purpose: review and duplicate detection.
- Stores on device only (app-private storage, excluded from cloud backup where it holds
  media ids): `trash.json`, `kept.json`, hash cache, preferences.
- Nothing leaves the device; the app has no `INTERNET` permission. No accounts.
- Play Console: data safety form = "no data collected or shared"; the Photo and Video
  Permissions declaration states photo management as core functionality.
- A public privacy policy URL is required by Play: `docs/privacy-policy.md` (EN + NL),
  published from this public repo. `SECURITY.md` gets an Android section.
- The upload keystore and its passwords never enter the repo; `android/keystore.properties`
  is git-ignored and `android/keystore.properties.example` holds placeholders.

## Design direction (UI features)
`docs/DESIGN.md` and `design/refs/` do not exist in this repo yet. Direction for v1:
Material 3 with the same three-tab structure (Media / Prullenbak / Instellingen), the same
screens and the same information density as the iOS app; simulator screenshots of each
iOS screen are saved to `design/refs/ios/` as the reference before UI work starts.
Platform conventions win where they differ (bottom navigation bar, system back gesture,
Material dialogs, dynamic color off — the app keeps its own accent color).

## Performance budget (when it matters)
- First photo visible < 1.5 s after a warm start on a mid-range device with 20k items.
- Grid scrolls at 60 fps with 20k items; swipe feedback < 100 ms.
- Similar scan: ≥ 100 photos/s on first run; a re-scan of an unchanged 20k library < 5 s.

## Test plan
- Unit (JUnit, written first): exact duplicate grouping, perceptual grouping/union-find,
  dHash on fixed bitmaps, trash retention/expiry/counter, keep-store, "on this day" date
  matching across years and leap day, category mapping.
- Component: Compose UI tests for the deck (swipe left/right/undo) and trash
  (restore/empty) against a fake `PhotoSource`.
- Manual check: Android emulator (API 30 and latest) seeded with generated test images —
  permission flows incl. partial access, system delete dialog, reminder notification;
  screenshots of every screen compared with `design/refs/ios/`.
- Release check: `bundleRelease` output inspected with `bundletool` (permissions, target
  API), Android lint clean.

## Open questions
Decided on 2026-11-10 (Guus):
- Selfies are left out on Android (no selfie flag exists). Screenshots are detected by
  folder, Favorieten by the MediaStore favorite flag (not every gallery app sets it),
  Panorama's by aspect ratio (≥ 2:1).
- After the 30 days, items move to Android's own trash (second 30-day safety net, like
  "Recent verwijderd" on iOS); space is freed when Android purges it.
- Publishing goes through a new Play organisation account (verification pending since
  2026-11-10), so the 12-testers/14-days closed test
  does not apply. `docs/PLAYSTORE.md` still routes the first build through internal
  testing before production. The business name and address will be public on the listing.
- Tooling: JDK 17, Android command-line tools and an emulator image are installed on this
  Mac in step 1 of the build.

Still open (proposal applies unless changed):
- **Application id**: `com.guus.photocleaner` (same as iOS). Permanent once published.
- **Minimum Android version**: Android 11 / API 30 (needed for the system trash dialog
  without legacy code paths).
- **DSA trader status / monetization**: free app assumed; Play still asks for the trader
  declaration (open TODO in `docs/PRODUCT.md`).

## Plan (added before building)
Each step is 1–2 hours, ends with its checks green and one commit. Kotlin package
`com.guus.photocleaner`, sources under `android/app/src/`. The package layout mirrors the
iOS boundaries in `docs/ARCHITECTURE.md` (`model/`, `source/`, `service/`, `ui/`).

1. **Tooling and skeleton** — install JDK 17, Android command-line tools, platform, build
   tools, emulator image. Create `android/` (Gradle wrapper, version catalog, Compose app
   that shows an empty screen), `.gitignore` entries, Android commands in `CLAUDE.md`,
   ADR `docs/decisions/0005-native-android-app.md`. Check: `./gradlew :app:assembleDebug`
   and the app starts in the emulator.
2. **Models and pure logic** — tests first: `DuplicateDetectorTest`,
   `PerceptualDetectorTest`, `ImageHashingTest`. Port `Models/PhotoAsset.swift`,
   `Services/DuplicateDetector.swift`, `Services/PerceptualDetector.swift`,
   `Sources/ImageHashing.swift` (dHash on a 9×8 grey array, no Android types).
3. **Stores** — tests first: `TrashStoreTest` (retention, expiry, counter),
   `KeepStoreTest`, `HashCacheTest`. Port `TrashStore`, `KeepStore`, `HashCache` with JSON
   files in app-private storage; preferences in SharedPreferences.
4. **Photo source** — `PhotoSource` interface (port of `Sources/PhotoSource.swift`),
   `MediaStoreSource` (query, on-this-day, categories, thumbnails, byte sizes, system
   trash request), `FakePhotoSource` for tests. Tests first: on-this-day date matching
   (leap day), category mapping. Script `android/scripts/seed-emulator.sh` pushes generated
   test images. Check: instrumented query test on the emulator.
5. **App shell** — first save iOS reference screenshots to `design/refs/ios/`. Theme,
   bottom navigation (Media / Prullenbak / Instellingen), onboarding, permission gate
   incl. partial access banner, `values/strings.xml` (EN) and `values-nl/strings.xml`.
   Follows `App/RootView.swift`, `Views/OnboardingView.swift`, `Views/PermissionGateView.swift`.
6. **Review deck** — swipe card stack with keep/discard/undo and the cleaned counter,
   following `Views/ReviewDeck.swift`. Compose UI test first: swipe left/right/undo against
   `FakePhotoSource`.
7. **Media modes, filter and grid** — Random / Dag / Maand / Jaar, category filter, grid
   review with multi-select, following `Views/MediaView.swift` and
   `Views/GridReviewView.swift`. UI test first: bulk discard.
8. **Zoom, video, share** — pinch zoom, video playback, share sheet, following
   `Views/PhotoZoomView.swift` and `Views/ShareButton.swift`.
9. **Duplicates** — exact and similar scans with progress, cancel and hash cache,
   following `Views/DuplicatesView.swift`. Test first: re-scan hashes nothing (AC 12).
10. **Trash** — list with days left, restore, empty, expiry prompt and the system trash
    dialog (confirm and cancel paths), following `Views/TrashView.swift`. UI test first:
    restore and cancel.
11. **Settings and reminder** — theme, keep reset, version, daily reminder with
    notification permission, following `Views/SettingsView.swift` and
    `Services/NotificationManager.swift`. Test first: next-trigger-time calculation.
12. **Release build** — launcher icon (`/image`), signing config reading the git-ignored
    `android/keystore.properties` (+ `.example`), R8, version name/code,
    `./gradlew :app:lint :app:bundleRelease`, bundle inspected for permissions and target
    API (AC 20, 21). Guus creates the upload key.
13. **Store package and docs** — `docs/privacy-policy.md` (EN + NL), listing texts NL/EN,
    feature graphic, emulator screenshots, `docs/PLAYSTORE.md`; update `SECURITY.md`,
    `docs/PRODUCT.md`, `docs/ARCHITECTURE.md`, `STATUS.md`, `CHANGELOG.md`.

New dependencies (all Google/JetBrains or the de-facto standard, lockfile committed):
Jetpack Compose + Material 3 and Navigation Compose (UI), Coil (thumbnail loading and
caching), Media3 ExoPlayer (video), kotlinx.serialization (JSON stores), WorkManager
(daily reminder). No networking, analytics or crash SDKs.

Risks:
- Play can reject broad photo access; the permission declaration must show cleanup as core
  functionality. Fallback would be the system photo picker, which breaks the product.
- The MediaStore favorite flag is rarely set by gallery apps, so Favorieten may be empty.
- The system trash dialog appears once per batch and cannot be styled or skipped.
- Verification is emulator-only until Guus tests on a physical Android phone.
- Play's required target API level is checked at step 12, not assumed now.
