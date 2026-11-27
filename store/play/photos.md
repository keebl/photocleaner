# Photos in the Play screenshots

The phone screenshots in `screenshots/nl/` and `screenshots/en/` (retaken 2026-11-27) show
free stock photos from Pexels, under the Pexels license (free to use, also commercially,
no attribution required; https://www.pexels.com/license/). No personal photos. The photos
themselves are not in the repo; download them by ID to retake the screenshots.

In order (the seed generator assigns them by file name order, so keep the `NN-` prefix):

| # | Pexels ID | Used as |
|---|-----------|---------|
| 00 | 17345550 | on this day, 2019 |
| 01 | 29014982 | on this day, 2021 |
| 02 | 36694789 | on this day, 2023 |
| 03 | 32949053 | exact duplicate pair |
| 04 | 7160695 | similar pair |
| 05 | 39085256 | panorama |
| 06 | 20318974 | leap day 2024 |
| 07 | 29020872 | no capture date (shows as today) |
| 08 | 9825919 | phone screenshot (shows as today) |
| 09–20 | 14209535, 18168206, 2290554, 28853195, 33828874, 33999682, 36468248, 36687007, 40234150, 5691261, 6489478, 12181619 | spread-out dates |

Retake (emulator running, debug build installed, from `android/`):

1. Download each as `NN-<id>.jpg` into one folder:
   `https://images.pexels.com/photos/<id>/pexels-photo-<id>.jpeg?auto=compress&cs=tinysrgb&w=1600`.
2. `SEED_PHOTOS=<folder> scripts/seed-emulator.sh`.
3. Show Pro as unlocked without a purchase (debug build, emulator only):
   `echo '{"state":"PRO","purchaseToken":"store-screenshots"}' | adb shell run-as com.guus.photocleaner sh -c "'cat > files/entitlement.json'"`,
   then force-stop and reopen the app.
4. Status bar demo mode (clock 9:41): `adb shell settings put global sysui_demo_allowed 1`
   and `adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 0941`.
5. Per language (`adb shell cmd locale set-app-locales com.guus.photocleaner --locales nl-NL`
   or `en-US`): deck, grid, Duplicates → "Lijkende foto's" / "Similar photos", one photo
   discarded → Trash, Settings. Pad each 1080×2400 capture to 1200×2400 with black sides.
