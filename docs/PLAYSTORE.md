# PhotoCleaner on Google Play

Step-by-step route from this repo to a production release. Steps marked **(Guus)** need the
Play Console account, passwords or the upload key and can only be done by the account owner;
everything else is prepared in the repo. Spec: `specs/android-app.md`.

## Status (2026-11-15)
- App: feature-complete for v1 (`specs/android-app.md` steps 1–13), target API 36 (Play
  requirement since 2026-08-31), min API 30, version 1.0 (1). Since 2026-11-14 the NAS
  source (`specs/android-nas.md`) adds `INTERNET`, used only for SMB to a NAS on the user's
  own network (ADR-0006).
- Store assets: `store/play/` (listing texts NL/EN, 512 px icon, feature graphic, phone
  screenshots NL/EN).
- Privacy policy: `docs/privacy-policy.md` (EN + NL). Public URL once merged to `main`:
  https://github.com/keebl/photocleaner/blob/main/docs/privacy-policy.md
- Since 2026-11-15: PhotoCleaner Pro, a one-time in-app purchase `pro_lifetime` (€4.99,
  `specs/pro-unlock.md`), built on Play Billing 9.1.0; version 1.0 (2) adds the `BILLING`
  permission. Pro must be in the first public release (step 7a).
- Open: organisation account verification, upload key, Console forms below, the Pro
  product (step 7a).

## 1. Account (Guus)
- Organisation developer account (verification started 2026-11-10). Because it is an
  organisation account, the 12-testers / 14-days closed-test rule for new personal accounts
  does not apply.
- The organisation name and address are shown publicly on the store listing (EU DSA trader
  declaration, see step 6).

## 2. Upload key (Guus)
Create the upload key **outside the repo** and never commit it:

macOS's own `keytool` finds no Java runtime (Homebrew's `openjdk@17` is keg-only), so call
the Homebrew one by its full path:

```bash
mkdir -p ~/keys && /opt/homebrew/opt/openjdk@17/bin/keytool -genkeypair -v -keystore ~/keys/photocleaner-upload.jks -alias upload \
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
`DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, `INTERNET` (NAS source only) and
`com.android.vending.BILLING` (Pro unlock); no `ACCESS_NETWORK_STATE` or `WAKE_LOCK`.
Without bundletool, `unzip -p app-release.aab base/manifest/AndroidManifest.xml | strings |
grep -i permission` lists the same names.

Every new upload needs a higher `versionCode` in `android/app/build.gradle.kts`.

## 4. Create the app in Play Console (Guus)
Play Console → **Create app**: name `PhotoCleaner`, default language Dutch (nl-NL), App,
Free. Accept the declarations. (Done 2026-11-11; the Console shows the app's default
language as en-US, so release notes default to English.) Package name comes from the first upload:
`com.guus.photocleaner` (permanent).

Enrol in **Play App Signing** (default) when uploading the first bundle.

## 5. Store listing
Grow → Store presence → **Main store listing**: paste from `store/play/listing.md`, upload
`store/play/icon-512.png`, `store/play/feature-graphic.png` and the screenshots from
`store/play/screenshots/nl/` (add the English translation with `screenshots/en/`).
Store settings: category **Photography**, contact e-mail (shown publicly), privacy policy
URL from the status section above.

Done 2026-11-19: category **Fotografie** (Photography) and the public contact e-mail (Guus's
Gmail) saved and published; the default listing en-US saved with the EN texts, icon,
feature graphic and the five `screenshots/en/` images in order (deck, grid, duplicates,
trash, settings), AI-generated assets declaration "do not label". Not yet added: the nl-NL
translation (`listing.md` NL texts and `screenshots/nl/`). The dashboard's setup tasks are all
done, but "Send for review" in the Publishing overview stays locked: the dashboard now asks to
finish the closed test track (countries, a release, preview, send), whose tester list was
already set.

## 6. App content (Policy → App content)
All declarations saved 2026-11-19 (overview: "Je bent helemaal bij"); not yet sent for review
in the Publishing overview. Data safety: no data collected or shared (Guus's choice).
- **Privacy policy**: the URL above.
- **Ads**: no ads.
- **App access**: no login, but Play requires reviewer access to paid content: declared
  "some functionality is restricted" with the instruction set "No account needed (Pro and NAS
  notes)", which gives the reviewer a Play promo code for `pro_lifetime` (promotion "Play review
  access", id 131816898, 1 code, valid until 2027-05-19). The code itself is only in the Console
  form, never in the repo. Renew the promotion before it expires while the app is in review.
- **Content rating**: questionnaire, category "Utility, Productivity, Communication or
  Other"; answer no to violence, sexuality, language, controlled substances, gambling, user
  interaction and data sharing → expected rating PEGI 3 / Everyone. Saved 2026-11-19 with one
  "yes": users can buy digital products (Pro). Result: PEGI 3, ESRB Everyone, USK 0, IARC 3+,
  ClassInd 14+ (Brazil rates in-app purchases higher).
- **Target audience**: 18 and over (not designed for children).
- **Data safety**: "Does your app collect or share any of the required user data types?" →
  proposed answer **No** — Guus decides before submitting (checked 2026-11-14 against Play's
  help page "Provide information for Google Play's Data safety section"). Photos, the trash
  and keep lists, hashes and settings stay on the device in app-private storage (excluded
  from backup). Play defines collection as "transmitting data from your app off a user's
  device"; the only traffic is SMB between the phone and the user's own NAS on the local
  network — the developer never receives or can access any of it, and the app contacts no
  other server. Play does not name this case; its closest guidance (uploads to the user's
  own Drive/Dropbox that the app "never collects or accesses") says such traffic may not
  need declaring. The conservative alternative is to declare "Photos and videos" (and the
  NAS username) as collected, not shared, purpose "App functionality", not encrypted in
  transit unless the NAS enforces SMB3 encryption. Sharing a photo is user-initiated through
  Android's share sheet, which Play does not count as sharing.
- **Photo and video permissions** declaration (required for `READ_MEDIA_IMAGES` /
  `READ_MEDIA_VIDEO`): core functionality is **photo management / gallery cleanup**. The 2026
  form has two fields of at most 250 characters (one per permission); saved 2026-11-19 with a
  shortened version of the text below. Suggested text:
  > PhotoCleaner is a photo and video clean-up app. Its core function is reviewing the
  > user's whole library: an "on this day" swipe deck, day/month/year browsing, a grid for
  > bulk decisions, and finding exact and visually similar duplicates across all photos.
  > Finding duplicates requires reading every photo, which the system photo picker cannot
  > provide. All processing happens on the device; the app only connects to a NAS on the
  > user's own network when the user links one.
- **DSA trader status**: the account belongs to an organisation, so declare as **trader**;
  name, address, e-mail and phone become public on the listing.
- **Government apps / financial features / health**: not applicable.
- **In-app purchases**: Play adds "Contains in-app purchases" to the listing on its own once
  `pro_lifetime` is active. Data safety for the purchase: the app uses Google Play Billing
  only, the payment and account data are handled by Google Play, and the app keeps only
  "Pro owned" plus Play's purchase token on the device (nothing reaches the developer). Check
  the exact answer for "Purchase history" against Play's Data safety help when filling the
  form (Guus); the proposed answer stays **No collection** unless that help page says
  otherwise.

## 7. Internal testing first
Test and release → Testing → **Internal testing** → create a release, upload
`app-release.aab`, add testers (an e-mail list), roll out. Install from the opt-in link on a
real Android phone and run the manual checks in `specs/android-app.md` (permissions incl.
partial access, system trash dialog, reminder, video playback).

## 7a. Pro product and license testers (Guus)
Play only allows in-app products after a build with the `BILLING` permission is in a track,
so do this after the first internal-testing upload (version 1.0 (2) or later):
0. The products page stays locked until a **Google Payments merchant account** is set up
   (Console prompt "Een verkopersaccount instellen": organisation details, bank account,
   tax info). Found 2026-11-16.
   Done 2026-11-16 (notes): the bulk price "4,99 EUR" became €5.99 in euro countries (Play
   adds VAT and rounds), so the Netherlands was set to €4.99 by hand per row; the purchase
   option ID is `buy`; no family-sharing switch was found in the product form or monetization
   setup; license testers live under Account beheren → Licentie testen.
   2026-11-19: every EUR row (33 countries, including Spain, Finland and France with
   regional VAT overrides) set to €4.99 by hand via each row's pencil, then the page Save.
1. Monetize with Play → Products → **One-time products** → Create: product ID
   `pro_lifetime` (permanent, must match `ProEntitlement.PRODUCT_ID`), name "PhotoCleaner
   Pro", description "Browse any day, month or year, clean up videos, use the grid and
   clean up duplicates. One-time purchase, no subscription." Default price **€4.99**
   (let Play convert other countries), one purchase option "Buy", **family sharing on**.
   Activate it.
2. Setup → **License testing**: add the Google accounts that test purchases (test cards,
   no real charge), response "RESPOND_NORMALLY".
3. On a tester's phone (installed from the internal-testing link): the Pro sheet shows the
   price; buy with the "Test card, always approves", then "slow test card" for the pending
   state; reinstall and Restore; refund from Order management → Pro locks again at the next
   start; airplane mode start keeps Pro. Record results in `STATUS.md`
   (`specs/pro-unlock.md` Plan step 8).

## 8. Production
When internal testing is clean: Test and release → **Production** → create release → promote
the tested bundle (or upload it again), choose countries, roll out. First review can take
several days for new organisation accounts.

## After release
- Bump `versionCode` (and `versionName`) for every update; keep `targetSdk` at Play's
  required level (check developer.android.com/google/play/requirements/target-sdk each
  year before 31 August).
- Record each release in `CHANGELOG.md`.
