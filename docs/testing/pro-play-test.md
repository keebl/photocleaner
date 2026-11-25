# Pro purchase test on a Play-installed build (Pro step 8)

Manual test of "PhotoCleaner Pro" (`pro_lifetime`) against Google Play itself, on a real
Android phone with the app installed from Play. Automated tests cover the UI with
`FakeBilling`; only this run proves Play billing works. Each step names the acceptance
criterion (AC) in `specs/pro-unlock.md` it checks. Takes about 45 minutes.

UI labels are quoted as the app shows them in Dutch.

## Before you start

- **Account**: the phone's Google account must be on the tester list "Family and friends"
  (closed test "Alpha" and license testers use the same list). License testers pay with
  test cards; nothing is charged.
- **Install from Play**: a closed-test app cannot be found by searching the Play Store.
  On the phone, signed in with the tester account, open
  https://play.google.com/apps/testing/com.guus.photocleaner, tap "Tester worden", then
  https://play.google.com/store/apps/details?id=com.guus.photocleaner and install. (The
  same opt-in link is in Play Console → Testen → Gesloten test → Alpha → tab Testers,
  under "Deelnemen via internet".) A build installed with `adb` or
  Android Studio cannot buy: Play then shows "Google Play is nu niet bereikbaar".
- **Two places to watch**: the app's Pro sheet (any lock icon, or Instellingen → "Pro
  ontgrendelen") and Instellingen → Status.
- **One purchase per account**: `pro_lifetime` can be owned once. Step 6 refunds it so step
  7 can buy again; keep the order.
- **Recording**: per step ✅ / ❌ (what happened, a screenshot) / ⏭ (why). Fill in the run
  log at the bottom, then give it to Claude for `STATUS.md`.

## Steps

1. **Price and sheet** (AC 9) — tap the Filmpjes tab. The sheet shows "PhotoCleaner Pro",
   "Eenmalig, geen abonnement", four features, "Kopen voor €4,99", "Aankopen herstellen"
   and "Sluiten". Expect €4,99 (Netherlands).
2. **Cancel** (AC 12) — tap "Kopen voor €4,99", then close Play's purchase dialog with the
   back gesture. Nothing changes, no error; Status still "Gratis".
3. **Declined card** — buy again, choose payment method "Test card, always declines".
   Play shows an error; the app stays free, no app error message.
4. **Pending payment** (AC 13, 11) — buy with "Slow test card, approves after a few
   minutes". The sheet shows "Betaling in behandeling. Pro wordt ontgrendeld zodra Google
   Play hem bevestigt." and Status "Betaling in behandeling"; the locks stay. Wait (up to
   ~5 minutes) with the app open: Pro unlocks without a restart, lock icons disappear,
   Status "PhotoCleaner Pro — ontgrendeld" (AC 17).
5. **Acknowledged** (AC 11) — wait 10 more minutes, then force-stop and reopen the app
   (Android Instellingen → Apps → PhotoCleaner → Gedwongen stoppen). Pro is still unlocked.
   (Play refunds an unacknowledged test purchase after a few minutes, so this proves the
   app acknowledged it.)
6. **Offline start** (AC 16) — turn on airplane mode, force-stop, open the app: Pro features
   work (Maand, Filmpjes, grid). Turn airplane mode off.
7. **Restore after reinstall** (AC 14) — uninstall the app, reinstall from the Play Store,
   give photo access: Pro is unlocked by itself, without tapping anything, because the app
   asks Play for purchases at every start. That is the pass. "Aankopen herstellen" is only
   the fallback when that automatic check misses (for example offline at first start).
8. **Refund → locked** (AC 15) — on the computer: Play Console → Account beheren (the badge
   icon in the left rail) → **Bestellingen beheren** (or
   `play.google.com/console/developers/<account-id>/orders`) → click the arrow at the end of
   the newest "Test: PhotoCleaner Pro" row → order page → button **Teruggave** at the bottom
   of "Bestelgegevens" → choose a full refund and tick the option that removes the buyer's
   access (revoke entitlement) → confirm. The test card charges nothing. The access option
   cannot be added afterwards (the button greys out after a full refund), and a refund
   without it leaves the purchase owned in Play, so Pro correctly stays unlocked; then repeat
   this step with another tester account (2026-11-25 run). On the phone:
   force-stop and reopen. Pro is locked again; decisions and the in-app trash are kept
   (AC 8).
9. **Buy again** (AC 11, 17) — buy with "Test card, always approves". Pro unlocks at once,
   the sheet closes.

Network permission check (Recent decisions, 2026-11-15): steps 4–9 working means billing
works without `ACCESS_NETWORK_STATE`; no extra step needed.

## Run log

| Date | Phone, Android version | App version (Instellingen → Over) | Result per step 1–9 | Notes |
|------|------------------------|-----------------------------------|---------------------|-------|
| 2026-11-25 | Guus's phone (not recorded) | 1.0 (2), closed test Alpha | 1 ✅ 2 ✅ 3 ✅ 4 ✅ 5 ✅ 6 ✅ 7 ✅ 8 ⏭ 9 ✅ | Step 8: refunded without revoke, so Pro correctly stayed unlocked; AC 15 left to the `FakeBilling` tests (Guus's choice). Passed. |
