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
- Privacy policy: `docs/privacy-policy.md` (written for the Android release; it also covers
  the iOS app except the NAS source). TODO(guus): add an iOS/NAS paragraph before App Store
  submission (see `docs/APPSTORE.md`).

## Android app (`android/`)
- **Data**: the user's photos and videos, read through MediaStore with the user's permission
  (pixels for thumbnails and comparison hashes; date, size, dimensions, name, folder,
  favourite flag) — purpose: review and duplicate detection. Stored on-device only, in
  app-private storage: `trash.json`, `kept.json`, the hash cache and preferences. All app
  data is excluded from cloud backup and device transfer (`data_extraction_rules.xml`,
  `full_backup_content.xml`), because it holds device-specific media ids.
- **NAS (optional, `specs/android-nas.md`)**: host, port, share, folder and username in
  app-private SharedPreferences; the password encrypted with AES-256-GCM under a
  non-exportable Android Keystore key, ciphertext in `noBackupFilesDir` — never in
  preferences, code or logs. Folder listing, previews and temporary share/playback copies in
  the app cache. All of it is deleted when the user removes the NAS link.
- **Network: local only** (ADR-0006): `INTERNET` is declared for the NAS source alone.
  `LocalAddressPolicy` refuses any server that does not resolve to a private, link-local,
  CGNAT (VPN) or loopback address, and connects to the checked address. SMB signing is on;
  SMB3 encryption is used when the server offers it. Media3's `ACCESS_NETWORK_STATE` and
  `WAKE_LOCK` stay removed. No analytics, crash reporting, ads or accounts; no developer
  server.
- **Permissions** (checked on the release bundle with bundletool): media read (images,
  video, user-selected; `READ_EXTERNAL_STORAGE` up to API 32), `POST_NOTIFICATIONS` and
  `RECEIVE_BOOT_COMPLETED` for the optional daily reminder, `INTERNET` for the NAS source.
- **Deletion**: never silent. Items go to the in-app trash for 30 days, then to Android's
  system trash only after the user confirms Android's own dialog. NAS items have no system
  trash: they are deleted on the NAS only after the app's own "cannot be undone" dialog.
- **Signing**: the Play upload keystore lives outside the repo; `android/keystore.properties`
  (git-ignored, also `*.jks` / `*.keystore`) holds its path and passwords, with placeholders
  in `android/keystore.properties.example`. Play App Signing holds the app signing key.
- **Dependencies**: Gradle version catalog (`android/gradle/libs.versions.toml`), wrapper
  pinned by SHA-256, every artifact checked against `gradle/verification-metadata.xml`.
  Libraries are AndroidX/JetBrains/Coil plus, for the NAS source, smbj (Apache-2.0) and
  rapid7 dcerpc (BSD-3) with their dependencies (BouncyCastle, Guava, commons, slf4j-api
  without a logging backend). No analytics or networking SDKs.
- **Privacy policy**: `docs/privacy-policy.md` (EN + NL), the public URL for Google Play.

## Reporting a problem
Open a GitHub issue labelled `security` on `keebl/photocleaner`, or e-mail the maintainer.
Don't post exploit details publicly before a fix is released.
