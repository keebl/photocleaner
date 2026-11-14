# Changelog

All notable, user-facing changes to this project are documented here.
Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Versioning: SemVer.

## [Unreleased]

### Added
- Android app (Android 11+, `android/`): "Op deze dag" swipe deck with undo; Random / Dag /
  Maand / Jaar browsing with a grid for bulk decisions; Foto's / Filmpjes and a category
  filter (Alle / Screenshots / Favorieten / Panorama's); photo zoom, video playback and
  sharing; exact and similar duplicates; 30-day trash that then moves items to Android's
  trash after confirmation; theme and a daily reminder; Dutch and English. No internet
  access. Google Play store package and privacy policy (EN + NL).
- Android: NAS (SMB) photo source, as on iOS — link a server, share and folder in the app,
  then review, find duplicates, zoom, play, share and trash NAS photos and videos. Only a NAS
  on your own network (Wi-Fi or VPN) is allowed; the password is stored encrypted with an
  Android Keystore key. NAS items are deleted permanently after the in-app trash, only after
  confirming. Adds the INTERNET permission (ADR-0006); onboarding, Play listing and privacy
  policy updated.
- Project standard adopted (CLAUDE.md, STATUS.md, docs, specs, security baseline).
- Category filter in the photo review: Alle / Screenshots / Selfies / Favorieten / Panorama's
  (iPhone library only).

### Changed
- The counter next to the trash icon now shows how many items are in the trash; the lifetime
  "cleaned" total moved to Settings (iOS and Android).
- Grid review: fixed-layout action bar (buttons always present), weggooien left / behouden
  right, larger top-right select circle, grid count shown.

### Fixed
- iOS: the "Klaar" summary of a day finished earlier no longer reads "0 behouden · 0 weggegooid".

<!-- No git tags exist yet, so no released versions are listed. The first tagged release
     will become the first version section below. -->
