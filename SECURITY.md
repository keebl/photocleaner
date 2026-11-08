# Security — PhotoCleaner

## Data this project handles
- Photos & videos — purpose: the user reviews and deletes them — stored: on-device (iOS
  Photo Library) or on the user's own NAS (SMB) — retention: the app never copies them off
  the device; deletions go through a 30-day in-app trash before any real removal.
- NAS/SMB credentials (host, share, username, password) — purpose: connect to the user's NAS
  — stored: iOS Keychain (`kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`) — retention:
  until the user disconnects. Never in code, UserDefaults, or logs.
- NAS folder access — persisted as a security-scoped bookmark (iOS Files), not a path.

## Secrets
- No secrets are committed. This is a public repo: no API keys, no credentials, no personal
  photos. The user's NAS password exists only in the device Keychain at runtime.
- `.claude/settings.json` denies Claude Code read access to `.env*`, `tokens.json`, `*.pem`,
  `*.p12`, `*.keystore` and asks before force-push / hard reset.
- Signing: a personal Apple ID team is used for local device installs; no distribution
  certificates or provisioning profiles are committed (`*.mobileprovision` is off-limits).

## Dependencies
- Swift Package Manager; `Package.resolved` is committed. New dependencies are justified in
  the PR (why, maintenance, size); prefer Apple frameworks over small unknown packages.

## Platform and privacy
- Permissions requested: Photo Library access (with an in-app reason), optional local
  notifications (daily reminder), optional local network access (NAS/SMB).
- `PrivacyInfo.xcprivacy` is present and declares the app's data use for the App Store.
- No analytics, no third-party SDKs, no network calls except to the user's own NAS.
- Privacy policy: TODO(guus) — required before App Store submission (see `docs/APPSTORE.md`).

## Reporting a problem
Open a GitHub issue labelled `security` on `keebl/photocleaner`, or e-mail the maintainer.
Don't post exploit details publicly before a fix is released.
