# PhotoCleaner on Google Play

Step-by-step route from this repo to a production release. Steps marked **(Guus)** need the
Play Console account, passwords or the upload key and can only be done by the account owner;
everything else is prepared in the repo. Spec: `specs/android-app.md`.

## Status (2026-11-10)
- App: feature-complete for v1 (`specs/android-app.md` steps 1–13), target API 36 (Play
  requirement since 2026-08-31), min API 30, version 1.0 (1), no `INTERNET` permission.
- Store assets: `store/play/` (listing texts NL/EN, 512 px icon, feature graphic, phone
  screenshots NL/EN).
- Privacy policy: `docs/privacy-policy.md` (EN + NL). Public URL once merged to `main`:
  https://github.com/keebl/photocleaner/blob/main/docs/privacy-policy.md
- Open: organisation account verification, upload key, Console forms below.

## 1. Account (Guus)
- Organisation developer account (verification started 2026-11-10). Because it is an
  organisation account, the 12-testers / 14-days closed-test rule for new personal accounts
  does not apply.
- The organisation name and address are shown publicly on the store listing (EU DSA trader
  declaration, see step 6).

## 2. Upload key (Guus)
Create the upload key **outside the repo** and never commit it:

```bash
mkdir -p ~/keys && keytool -genkeypair -v -keystore ~/keys/photocleaner-upload.jks -alias upload \
  -keyalg RSA -keysize 4096 -validity 9125
```

Then copy `android/keystore.properties.example` to `android/keystore.properties`
(git-ignored), set `storeFile` to the absolute path of the `.jks` (Gradle does not expand
`~`; a relative path is resolved against `android/`) and fill in the passwords. Back up the keystore and passwords in a password manager: with Play App Signing a
lost upload key can be reset, but only through Play support.

## 3. Build the bundle

```bash
cd android
./gradlew :app:lintRelease :app:bundleRelease
```

Output: `android/app/build/outputs/bundle/release/app-release.aab`, signed with the upload
key. Optional check (bundletool from github.com/google/bundletool):

```bash
java -jar bundletool.jar dump manifest --bundle app/build/outputs/bundle/release/app-release.aab
```

Expected: `targetSdkVersion="36"`, permissions `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`,
`READ_MEDIA_VISUAL_USER_SELECTED`, `READ_EXTERNAL_STORAGE` (max SDK 32),
`POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED` and AndroidX's internal
`DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`; no `INTERNET`.

Every new upload needs a higher `versionCode` in `android/app/build.gradle.kts`.

## 4. Create the app in Play Console (Guus)
Play Console → **Create app**: name `PhotoCleaner`, default language Dutch (nl-NL), App,
Free. Accept the declarations. Package name comes from the first upload:
`com.guus.photocleaner` (permanent).

Enrol in **Play App Signing** (default) when uploading the first bundle.

## 5. Store listing
Grow → Store presence → **Main store listing**: paste from `store/play/listing.md`, upload
`store/play/icon-512.png`, `store/play/feature-graphic.png` and the screenshots from
`store/play/screenshots/nl/` (add the English translation with `screenshots/en/`).
Store settings: category **Photography**, contact e-mail (shown publicly), privacy policy
URL from the status section above.

## 6. App content (Policy → App content)
- **Privacy policy**: the URL above.
- **Ads**: no ads.
- **App access**: all functionality is available without login.
- **Content rating**: questionnaire, category "Utility, Productivity, Communication or
  Other"; answer no to violence, sexuality, language, controlled substances, gambling, user
  interaction and data sharing → expected rating PEGI 3 / Everyone.
- **Target audience**: 18 and over (not designed for children).
- **Data safety**: "Does your app collect or share any of the required user data types?" →
  **No**. The app has no internet access; photos, the trash and keep lists, hashes and
  settings stay on the device in app-private storage (excluded from backup). Sharing a photo
  is user-initiated through Android's share sheet, which Play does not count as collection.
- **Photo and video permissions** declaration (required for `READ_MEDIA_IMAGES` /
  `READ_MEDIA_VIDEO`): core functionality is **photo management / gallery cleanup**. Suggested
  text:
  > PhotoCleaner is a photo and video clean-up app. Its core function is reviewing the
  > user's whole library: an "on this day" swipe deck, day/month/year browsing, a grid for
  > bulk decisions, and finding exact and visually similar duplicates across all photos.
  > Finding duplicates requires reading every photo, which the system photo picker cannot
  > provide. All processing happens on the device; the app has no internet access.
- **DSA trader status**: the account belongs to an organisation, so declare as **trader**;
  name, address, e-mail and phone become public on the listing.
- **Government apps / financial features / health**: not applicable.

## 7. Internal testing first
Test and release → Testing → **Internal testing** → create a release, upload
`app-release.aab`, add testers (an e-mail list), roll out. Install from the opt-in link on a
real Android phone and run the manual checks in `specs/android-app.md` (permissions incl.
partial access, system trash dialog, reminder, video playback).

## 8. Production
When internal testing is clean: Test and release → **Production** → create release → promote
the tested bundle (or upload it again), choose countries, roll out. First review can take
several days for new organisation accounts.

## After release
- Bump `versionCode` (and `versionName`) for every update; keep `targetSdk` at Play's
  required level (check developer.android.com/google/play/requirements/target-sdk each
  year before 31 August).
- Record each release in `CHANGELOG.md`.
