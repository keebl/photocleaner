# Spec: Pro unlock (one-time purchase)

Status: approved
Date: 2026-11-11

## Goal
PhotoCleaner earns money without ads, subscriptions or data collection: the daily "Op deze
dag" ritual stays free forever, and a single lifetime purchase ("PhotoCleaner Pro", €4.99)
unlocks free browsing, videos, the grid and cleaning up duplicates.

## Users and context
- Free users: open the app daily (often via the reminder) to clear today's memories.
- Pro buyers: want to clean up a large library in one go — any period, videos, duplicates.
- Android first (Google Play Billing), before the public production release; nothing has
  shipped to the public yet, so no existing user loses a feature. iOS follows with the same
  model (StoreKit 2) before any App Store release (Plan step 8, its own build round).

## Scope (in)
- Free tier: "Op deze dag" for **today** (photos only) as deck, keep/discard/undo, the
  category filter and sort order within today, zoom and share, the in-app trash (restore,
  empty, expiry flow), the reminder, theme, onboarding, and running the duplicate scan with
  its results visible.
- Pro tier (adds): Random / Dag / Maand / Jaar browsing of any date and period navigation
  (previous/next, "Volgende" after finishing a set); the Filmpjes (videos) tab; the grid
  review; discarding from Dubbelen (exact and similar).
- One non-consumable product `pro_lifetime`, €4.99 (price set in Play Console), family
  sharing on.
- Locked features stay visible with a lock icon; tapping one opens the Pro sheet (what Pro
  includes, price from the store, Buy, Restore, Close).
- "PhotoCleaner Pro" row in Settings: status (Free / Pro), Buy, Restore purchases.
- Entitlement cached on-device so Pro works offline and at start-up before the store
  answers.
- Privacy policy (EN + NL), Play Data safety answers, store listing ("In-app purchases"),
  `docs/PRODUCT.md` (monetization), `SECURITY.md` updated.

## Non-goals (out)
- Subscriptions, ads, consumables, tips, promo codes inside the app.
- A free trial or time-limited Pro (no count-down timers, no "wait a day" energy mechanic).
- Server-side receipt validation or any backend; piracy of a €4.99 unlock is an accepted
  risk.
- The iOS implementation in the Android build round (separate step 8, same rules).
- Paywalling anything already in the in-app trash: restoring and emptying the trash are
  always free.

## Acceptance criteria
Free tier
1. Given a free user, when the app opens on the media tab, then the deck shows today's
   photos across all years ("Op deze dag") and keep / discard / undo work as today.
2. Given a free user on "Op deze dag", then videos taken on this day are not in the deck.
3. Given a free user, when they tap Random, Maand, Jaar, the date picker, or the
   previous/next period controls, then the Pro sheet opens and the selection does not change.
4. Given a free user who has decided every photo of today, when the deck is empty, then the
   empty state says today is done and offers Pro to continue with other days, instead of
   moving to the next period.
5. Given a free user, when they tap the Filmpjes tab or the grid toggle, then the Pro sheet
   opens.
6. Given a free user in Dubbelen, when the scan finishes, then the groups and the space they
   take are shown, and tapping discard (per item or per group) opens the Pro sheet.
7. Given a free user, then the category filter, sort order, zoom, share, trash, reminder and
   theme work as before.
8. Given any user with items in the in-app trash, then restore, empty and the expiry
   confirmation work regardless of Pro.

Pro sheet and purchase
9. Given the Pro sheet, then it lists the Pro features, shows the price from Play
   (localized), and has Buy, Restore purchases and Close.
10. Given the store cannot be reached or the product cannot be loaded, then the sheet shows
    a "store not available" message instead of a price and the Buy button is disabled.
11. Given a user taps Buy and completes the purchase, then Pro is unlocked immediately
    without restarting, the purchase is acknowledged, and the sheet closes.
12. Given a user cancels the purchase dialog, then nothing changes and no error is shown.
13. Given a purchase is pending (e.g. cash payment), then the sheet and Settings show
    "payment pending" and Pro stays locked until Play reports it as purchased.
14. Given a purchase made on another device or before a reinstall with the same Google
    account (or a family member's purchase), when the app starts or the user taps Restore,
    then Pro is unlocked.
15. Given Play reports the purchase as refunded or revoked, when the app next queries
    purchases, then Pro is locked again; decisions and the trash are kept.

Entitlement
16. Given a user who unlocked Pro, when the app starts offline, then Pro features are
    available from the cached entitlement.
17. Given a Pro user, then every lock icon is gone and Settings shows "PhotoCleaner Pro —
    unlocked" without a Buy button.

Privacy and permissions
18. Given the release bundle, then its permissions are those of `specs/android-app.md`
    AC 21 plus `com.android.vending.BILLING` only, and it has no `INTERNET` permission
    (see Open questions if billing cannot work without it).
19. The privacy policy (EN + NL) states that a purchase is handled by Google Play, that the
    app receives only whether Pro is owned, and that photos still never leave the device.

## Edge cases and error states
- Billing service disconnected or Play Store missing/outdated: free tier works; the sheet
  shows "store not available"; reconnect on next sheet open.
- Purchase succeeds but acknowledgement fails: retry on next start (Play refunds
  unacknowledged purchases after 3 days).
- User switches Google account in Play: entitlement follows the account that owns the
  purchase after the next query.
- Clock/date changes: "today" is the device's local date, as in the existing deck; no
  server time, no cool-down state.
- A Pro user who is refunded while viewing another period: the next query locks Pro; the
  media tab returns to "Op deze dag" for today.
- License testers (Play internal testing) buy with test cards; the flow must behave the same.

## Data and privacy
- New on-device data: a cached entitlement (Pro owned yes/no, purchase state, purchase
  token for acknowledgement) in app-private storage, excluded from backup like the other
  stores. No personal data; the Google account itself is never read by the app.
- Google Play processes the payment and account; Play Data safety: "Purchase history" is
  handled by Google Play, not collected by the developer — confirm the exact answer when
  filling the form (Plan step 7).
- Photos, decisions, hashes and the trash remain on-device; no new network traffic from the
  app itself.

## Design direction (UI features)
`docs/DESIGN.md` and the existing Android theme: Pro sheet as a bottom sheet in the app's
style (accent `#3380E6`, true-black dark mode), a short feature list with the existing
Material Symbols icons, one primary Buy button; lock icon as a small badge on locked
controls, not greyed-out controls. Friendly copy, no pressure ("Eenmalig, geen abonnement").
NL and EN strings.

## Performance budget
- App start: the free/Pro decision comes from the cache; never block the first screen on
  the store (< 100 ms added).
- Pro sheet: shows instantly; price fills in when the store answers.

## Test plan
- Unit: entitlement rules (purchased / pending / refunded / unacknowledged → state), the
  gate logic deciding which controls are locked, the cache store (JSON, atomic, like
  `TrashStore`).
- Compose UI (instrumented, fake billing): locked controls open the sheet (AC 3, 5, 6),
  free deck excludes videos (AC 2), empty-state offer (AC 4), sheet states (AC 9, 10, 13),
  Pro unlock removes locks (AC 17), trash works without Pro (AC 8).
- Bundle check: bundletool manifest dump for AC 18.
- Manual (Play internal testing, license tester): buy, cancel, pending (slow test card),
  restore after reinstall, refund from Play Console → locked again, offline start.

## Open questions
- Can billing work with the `INTERNET` permission removed (`tools:node="remove"`)? The
  Play Billing Library 9.1.0 itself only declares `BILLING`, but it depends on Google's
  `transport-backend-cct` (usage logging to Google, needs network) and
  `play-services-location`. Plan step 1 tests this; if a purchase fails without
  `INTERNET`, Guus decides between allowing it (and stating it in the privacy policy and
  Data safety) or another approach.
- Exact Play Data safety answer for an app with in-app purchases but no developer-side
  collection (checked in the Console in Plan step 7).
- iOS round: is the NAS (SMB) source free or Pro? Decide before Plan step 9.

## Plan (added before building)
Android first (steps 1–8), then iOS (step 9). Follow the existing patterns: stores as atomic
JSON files (`service/TrashStore.kt`, `service/JsonFile.kt`), a pure-logic object with JVM
unit tests (`ui/media/MediaSelection.kt`), an interface with a fake for UI tests
(`source/PhotoSource.kt` / `FakePhotoSource`), wiring in `AppContainer.kt` and
`MainActivity.kt`, strings in `values/strings.xml` and `values-nl/strings.xml`.

1. **Billing spike and permission check** (1–2 h) — `gradle/libs.versions.toml` (Play
   Billing 9.1.0, `billing-ktx` if still published), `app/build.gradle.kts`, regenerate
   `gradle/verification-metadata.xml` (command in `CLAUDE.md`), `AndroidManifest.xml`
   (`tools:node="remove"` for `INTERNET` / `ACCESS_NETWORK_STATE` if merged in). Bundle
   dump: only `BILLING` added (AC 18). Bump `versionCode` to 2. Guus uploads this build to
   internal testing and creates the one-time product `pro_lifetime` (€4.99) in Play Console
   — Play only allows products after a build with `BILLING` is uploaded. Then confirm on a
   license-tester device that the product loads and a test purchase completes without
   `INTERNET`; if not, stop and ask Guus (Open questions).
2. **Entitlement rules and cache** (1–2 h) — `service/ProEntitlement.kt` (pure: purchase
   list → FREE / PENDING / PRO, needs-acknowledge), `service/EntitlementStore.kt` (JSON,
   atomic, excluded from backup via the existing rules files). Tests first:
   `ProEntitlementTest`, `EntitlementStoreTest` (purchased, pending, refunded/absent,
   unacknowledged, corrupt file → FREE).
3. **Billing service** (2 h) — `billing/Billing.kt` (interface: `entitlement: StateFlow`,
   `productPrice`, `refresh()`, `launchPurchase(activity)`, `restore()`),
   `billing/PlayBilling.kt` (BillingClient: connect with retry, query product details and
   purchases on start and `ON_RESUME`, purchase listener, acknowledge, write the cache),
   `billing/FakeBilling.kt` for tests; wire into `AppContainer.kt` / `MainActivity.kt`.
   Tests first: `FakeBilling`-driven unit tests for state transitions (AC 11–16).
4. **Gate logic and media screen locks** (2 h) — `ui/pro/ProGate.kt` (pure: which tab /
   browse / period / grid / discard actions are allowed for FREE vs PRO), `MediaScreen.kt`,
   `media/MediaHeader.kt`, `deck/ReviewDeck.kt` (empty state "vandaag klaar" + Pro offer,
   no "Volgende" for free), free deck photos only. Tests first: `ProGateTest` (JVM), then
   `MediaScreenProTest` (AC 1–5, 7) with `FakeBilling`.
5. **Pro sheet and settings row** (1–2 h) — `ui/pro/ProSheet.kt` (feature list, store price,
   Buy / Restore / Close, unavailable and pending states), lock badge composable,
   `settings/SettingsScreen.kt` row; NL/EN strings ("Eenmalig, geen abonnement"). Tests:
   `ProSheetTest` (AC 9, 10, 12, 13), `SettingsScreenTest` additions (AC 17).
6. **Duplicates gate, trash and refund paths** (1–2 h) — `duplicates/DuplicatesScreen.kt`
   (discard → sheet for free; scan and results stay), refund while browsing → back to today.
   Tests: `DuplicatesProTest` (AC 6), `TrashScreenTest` addition (AC 8), refund transition
   (AC 15) via `FakeBilling`.
7. **Docs, policy and store** (1–2 h) — `docs/privacy-policy.md` (EN + NL, AC 19),
   `docs/PLAYSTORE.md` (create `pro_lifetime`, license testers, Data safety for purchases,
   "Contains in-app purchases"), `store/play/listing.md` (mention Pro honestly),
   `docs/PRODUCT.md` (monetization decided), `SECURITY.md`, `docs/ARCHITECTURE.md`
   (billing boundary), `specs/android-app.md` AC 21 note, `CHANGELOG.md`, `STATUS.md`. Run
   all Android checks; build and dump the signed bundle.
8. **Manual verification on Play internal testing** (1 h, with Guus) — license tester on a
   real phone: buy, cancel, pending (slow test card), restore after reinstall, refund from
   the Console → locked, offline start with Pro. Record results in `STATUS.md`.
9. **iOS (own build round; split into 1–2 h steps when it starts)** — StoreKit 2 with a
   non-consumable `pro_lifetime`, Family Sharing on, same gates in `MediaView.swift`,
   `GridReviewView.swift`, `DuplicatesView.swift`, `SettingsView.swift`; a `StoreKit
   Configuration` file for local testing; App Store privacy answers.

Risks:
- `INTERNET` may be required by billing in practice (step 1 decides; privacy promise and
  Data safety change if so).
- In-app products can only be created after a `BILLING` build is in a Play track, so step 1
  needs Guus in the Console before purchases can be tested end-to-end.
- Real purchase flows cannot run in instrumented tests; `FakeBilling` covers the UI and
  step 8 covers Play itself.
- The dependency adds Google Play services libraries (size, R8 rules); check the bundle
  size and the `minifiedCheck` build in step 1.
