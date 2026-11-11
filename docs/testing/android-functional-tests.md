# Android functional test script

Manual functional tests for the Android app on a real phone, installed from Play internal
testing. They cover what automated tests cannot: the real photo library, Android's system
dialogs, notifications, reboots and performance. Each test refers to the acceptance
criterion (AC) in `specs/android-app.md` it checks.

UI labels are quoted as the app shows them in Dutch (English in brackets where it helps).

## Before you start

- **Device**: a physical Android phone (Android 11 or newer), app installed via the
  internal-testing opt-in link. Note the phone model and Android version in the run log.
- **Fresh state**: for section 1, uninstall the app first (or Android Settings → Apps →
  PhotoCleaner → Storage → Clear storage), so onboarding and the permission request show.
- **Test data**: a few throw-away photos on the phone, ideally:
  - photos taken on today's date in an earlier year (for "Op deze dag"; if you have none,
    use AC 4 on another day or skip and note it);
  - two copies of the same photo (exact duplicate) and two shots of the same scene a
    second apart (similar photos);
  - one screenshot, one favourite (heart in your gallery app), one panorama, one short
    video.
- **Real deletes**: discarding only moves items to the in-app trash, but the trash's
  "Alles nu verwijderen" and the 30-day expiry move files to Android's trash (Galerij /
  Google Foto's → Prullenbak, recoverable there for 30 days). Use photos you can lose.
- **Recording**: per test write ✅ (pass), ❌ (fail, with what happened and a screenshot) or
  ⏭ (skipped, why). Fill in the run log at the bottom.

## 1. First start and permissions

| ID | AC | Steps | Expected result | Result |
|---|---|---|---|---|
| F-01 | 1 | Open the app after a fresh install. | Three onboarding pages; the last says the app has no internet access. "Overslaan" and "Volgende" work; "Aan de slag" ends it. | |
| F-02 | 1 | Finish onboarding. | Android asks for photo and video access. | |
| F-03 | 2 | Choose "Niet toestaan" (Don't allow). | Screen "Toegang tot je foto's" with "Geef toegang" / "Open instellingen"; no photos visible. | |
| F-04 | 2 | Tap "Open instellingen", allow access, return to the app. | The app shows your photos without a restart. | |
| F-05 | 3 | (Android 14+) Android Settings → Apps → PhotoCleaner → Permissions → Photos and videos → "Allow limited access", pick 3–5 photos. Return to the app. | Only the selected items are used; banner "PhotoCleaner ziet alleen de foto's die je hebt geselecteerd." with "Alles toestaan". | |
| F-06 | 3 | Tap "Alles toestaan" and allow all. | Banner disappears; the whole library is used. | |
| F-07 | 1 | Close and reopen the app. | No onboarding and no permission request again. | |

## 2. Op deze dag (swipe deck)

| ID | AC | Steps | Expected result | Result |
|---|---|---|---|---|
| F-10 | 4 | Open Media → "Foto's" → "Dag" on today ("Vandaag"). | Photos taken on today's day and month in any year, this year included; each card shows how many years ago ("Dit jaar" for this year). | |
| F-11 | — | First visit only. | Hint "Veeg om te kiezen" with "Begrepen"; it does not return after dismissing. | |
| F-12 | 5 | Swipe a card right ("Behouden"). | Card leaves; counter "x van y" advances. | |
| F-13 | 6 | Swipe a card left ("Weggooien"). | Card leaves; the trash counter in the header goes up; the file is still in your gallery app. | |
| F-14 | 7 | Tap "Laatste ongedaan maken" right after a swipe. | The last card comes back and the decision is undone (trash counter back down if it was a discard). | |
| F-15 | — | Use the "Behouden" / "Weggooien" buttons instead of swiping. | Same effect as swiping. | |
| F-16 | — | Tap a photo card. | Full-screen view; pinch to zoom works; "Sluiten" closes it; swiping there also decides. | |
| F-17 | — | In the full-screen view tap "Delen". | Android's share sheet opens with the photo. | |
| F-18 | — | Decide all cards of the day. | "Klaar" with "x behouden · y weggegooid" and the space saved; button "Volgende dag met foto's". | |
| F-19 | 5 | Force-close the app (swipe it away in recents) and reopen. | Kept and discarded items from F-12/F-13 do not reappear in any view. | |

## 3. Browsing, videos, categories and the grid

| ID | AC | Steps | Expected result | Result |
|---|---|---|---|---|
| F-20 | — | Switch between "Random", "Dag", "Maand", "Jaar". | Each shows a matching set; "Random" a shuffled mix including photos without a date. | |
| F-21 | — | In "Dag"/"Maand"/"Jaar" use "Vorige", "Volgende" and "Kies een datum". | The period changes accordingly; the title shows the chosen day/month/year. | |
| F-22 | — | "Volgorde" → "Oudste eerst", then "Nieuwste eerst". | Order of the deck flips. | |
| F-23 | 9 | Category filter: "Screenshots", "Favorieten", "Panorama's", then "Alle". | Only items of that category; "Alle" shows everything. A phone screenshot is never listed as a panorama. | |
| F-24 | — | Tab "Filmpjes" (videos). | Only videos; a card shows a play icon. | |
| F-25 | — | Open a video. | It plays with sound; scrubbing works; "Behouden" / "Weggooien" buttons work. | |
| F-26 | — | Tap "Toon als raster" (grid). | Grid with the count of items; "Toon als veegstapel" goes back. | |
| F-27 | 8 | In the grid select 3 items and tap "Gooi 3 weg". | All three move to the trash in one action; "Ongedaan maken" reverts it. | |
| F-28 | — | "Selecteer alles", then "Behoud n". | All selected are kept and disappear from the grid. | |
| F-29 | — | Tap "x opgeschoond, open prullenbak" in the header. | The trash opens. | |

## 4. Dubbelen (duplicates)

| ID | AC | Steps | Expected result | Result |
|---|---|---|---|---|
| F-30 | 10 | Tab "Dubbelen" → "Exacte dubbelen". | "Bibliotheek scannen…" with progress, then groups; per group "x exemplaren · y houden · z te winnen"; the suggested keep is the largest image. Note the scan time. | |
| F-31 | 11 | Switch to "Lijkende foto's". | "Lijkende foto's zoeken…", then groups of similar shots (your two near-identical photos together). Note the scan time. | |
| F-32 | 12 | Leave the tab, come back, or tap "Opnieuw scannen". | The second scan is much faster than the first. | |
| F-33 | — | Tap "Stoppen" during a scan. | The scan stops; the app stays responsive. | |
| F-34 | — | In a group toggle which items to keep, then "Gooi n weg". | The unchecked items go to the trash; the group disappears or shrinks. | |
| F-35 | — | Tap a photo in a group. | It opens full-screen for comparison. | |

## 5. Prullenbak (trash)

| ID | AC | Steps | Expected result | Result |
|---|---|---|---|---|
| F-40 | — | Open "Prullenbak". | Discarded items with their reason ("Op deze dag", "Dubbel", "Filmpje", …) and days left. | |
| F-41 | 13 | "Terugzetten" on one item. | It leaves the trash and shows up again in the review views; the cleaned counter drops by one. | |
| F-42 | 15 | "Meer acties" → "Alles nu verwijderen" → "Naar prullenbak van Android", then deny in Android's dialog. | Nothing is moved; all items stay in the in-app trash. | |
| F-43 | 14 | Repeat F-42 and allow in Android's dialog. | Items leave the in-app trash and appear in the gallery app's own trash (Galerij / Google Foto's → Prullenbak). | |
| F-44 | 16 | Delete a photo that is in the in-app trash from your gallery app, then reopen PhotoCleaner. | It silently disappears from the in-app trash; no error. | |
| F-45 | 14 | (Long-running) Leave an item in the trash for 30 days, then open the app. | Prompt to move expired items ("Verplaatsen" / "Later"); "Verplaatsen" shows Android's dialog. | |

## 6. Instellingen and the reminder

| ID | AC | Steps | Expected result | Result |
|---|---|---|---|---|
| F-50 | — | "Weergave": "Licht", "Donker", "Systeem". | The theme changes immediately and survives a restart. | |
| F-51 | 18 | Turn "Dagelijkse herinnering" on and deny notifications. | The switch returns to off; a hint explains how to enable notifications with "Open instellingen". | |
| F-52 | 17 | Allow notifications, set "Tijdstip" ~5 minutes ahead, lock the phone. | One "Op deze dag" notification arrives (it may be up to about an hour late; note the delay). | |
| F-53 | 17 | Tap the notification. | The app opens on "Op deze dag" for today. | |
| F-54 | 17 | Restart the phone with the reminder on. | The reminder still arrives at the set time the next day. | |
| F-55 | — | "Behoud-keuzes wissen" → "Wissen". | "Behouden foto's" goes to 0; kept photos reappear in review views; no photo is deleted. | |
| F-56 | — | "Over": retention and version. | "30 dagen" and version 1.0 (1) (or the current build). | |

## 7. General

| ID | AC | Steps | Expected result | Result |
|---|---|---|---|---|
| F-60 | 19 | Phone language Dutch, then English (Android Settings → System → Languages). | All text in Dutch, respectively English; nothing cut off or untranslated. | |
| F-61 | — | Rotate the phone. | The app stays in portrait. | |
| F-62 | — | Use the app for 10 minutes with a large library. | No crashes or freezes; scrolling and swiping feel smooth. Note anything slow. | |
| F-63 | 21 | Android Settings → Apps → PhotoCleaner → Permissions. | Only photos and videos, and notifications; no other permissions (no network listed under "App info"). | |
| F-64 | — | Swipe close to the left screen edge. | Android's back gesture may take over there; buttons near the left edge still respond. | |

## Run log

| Date | Tester | Phone / Android | App version | Passed | Failed | Skipped | Notes |
|---|---|---|---|---|---|---|---|
| | | | | | | | |

Failures go to `STATUS.md` (or an issue) with the test ID, what happened and a screenshot.
When the Pro unlock (`specs/pro-unlock.md`) is built, its manual checks are added here as
section 8.
