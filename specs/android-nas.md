# Spec: NAS (SMB) photo source on Android

Status: built
Date: 2026-11-14

## Goal
An Android user who keeps photos on a home NAS can link a folder on that NAS over SMB and
clean it up with the same deck, grid, duplicates and trash flow as the phone library — the
feature iOS already has (`PhotoCleaner/Sources/SMBSource.swift`).

## Users and context
Owners of a home NAS (Synology, QNAP, a Windows or Samba share) on the same Wi-Fi network
or VPN as their Android phone (Android 11+). Same users as iOS NAS (`docs/PRODUCT.md`).

## Scope (in)
- **Connect, step 1 — server and login:** server address (IP or hostname, optional
  `:port`), username (empty = guest), password. "Verbinden en shares tonen" connects and
  lists the shares on the server.
- **Connect, step 2 — browse:** pick a share, browse its subfolders, choose "Hele share
  gebruiken" / "Deze map gebruiken". The chosen folder and all its subfolders become the
  NAS source. Change or remove the link later ("NAS-koppeling wijzigen…", "Huidige
  NAS-koppeling verwijderen").
- **Source switching:** a source menu in the media header (Telefoon / NAS name / NAS
  koppelen… or NAS-koppeling wijzigen…) and a "Fotobron" section in Settings, as on iOS
  (`MediaView.swift` source menu, `SettingsView.swift` "Fotobron"). The choice persists.
- **`NasSource`** implementing the existing `PhotoSource` interface (ADR-0002): recursive
  scan of photos and videos (iOS extension list, incl. RAW), skipping hidden and NAS system
  folders (`@eaDir`, `#recycle`, `#snapshot`, `@tmp`, `@sharesnap`, `lost+found`).
- **Previews:** thumbnails from the first 512 KB of a file (embedded EXIF thumbnail), else
  the whole file; video posters from byte-range reads; a disk cache of generated previews.
- **Everything else as on iOS:** Op deze dag, Random/Dag/Maand/Jaar, grid review, zoom,
  exact and similar duplicates (with a NAS hash cache), video playback, share, in-app trash.
- **Deletion:** after 30 days in the in-app trash (or "nu verwijderen"), the app shows its
  own confirmation that NAS deletion is permanent, then deletes the files on the NAS.
- **"NAS-cache legen"** in Settings: clears the folder listing and previews, not the link.
- **Network permission and privacy promise:** the app gains `INTERNET`, but refuses any
  server that does not resolve to a local-network address (see Data and privacy). New ADR,
  updated onboarding, Play listing, privacy policy and `docs/PLAYSTORE.md` data-safety notes.

## Non-goals (out)
- Servers on the public internet (decision 2026-11-14: local network only).
- Other protocols (WebDAV, NFS, FTP, SFTP) and cloud storage.
- More than one NAS link at a time (iOS has one too).
- Capture date from EXIF: like iOS, a NAS item's date is the file's modification time.
- Category filter on NAS (no screenshot/favourite/panorama flags on plain files; as iOS).
- A trash folder on the NAS: deletion is permanent after the in-app trash (decision
  2026-11-14, ADR-0003). A NAS-side recycle bin (e.g. Synology `#recycle`) may still catch
  it, but the app does not rely on that.
- Background sync or scanning while the app is closed.
- Changes to the iOS app (it already has this feature).

## Acceptance criteria
1. Given the connect sheet, when the server field is empty, then "Verbinden en shares
   tonen" is disabled.
2. Given a reachable SMB server and valid credentials, when "Verbinden en shares tonen" is
   tapped, then the list of disk shares on that server is shown (no `IPC$` or admin `$`
   shares).
3. Given an empty username, when connecting, then the app logs in as guest.
4. Given a wrong password, when connecting, then the message "Inloggen mislukt — controleer
   je gebruikersnaam en wachtwoord." is shown and nothing is stored.
5. Given an unreachable server, when connecting, then within 15 seconds the message "Kan de
   server niet bereiken. Controleer het serveradres en of je op hetzelfde netwerk zit als
   de NAS (wifi/VPN)." is shown.
6. Given a server address that resolves to a public (non-local) IP address, when
   connecting, then no SMB connection is opened and the message "Alleen een NAS op je eigen
   netwerk is toegestaan." is shown.
7. Given a share is opened, when its folder list loads, then its subfolders are listed
   (hidden and NAS system folders left out) with a "Hele share gebruiken" action, and each
   subfolder has "Deze map gebruiken".
8. Given a folder is chosen, when linking succeeds, then the host, port, share, folder and
   username are stored, the password is stored encrypted with an Android Keystore key, the
   sheet closes and the NAS becomes the active source.
9. Given a linked NAS, when the app is restarted, then the NAS is still the active source
   and its photos load without asking for the password again.
10. Given the NAS is the active source, when photos load, then all photos and videos in the
    chosen folder and its subfolders are shown, newest modification time first, and files
    with other extensions are ignored.
11. Given a completed scan, when the NAS source is reopened (also after an app restart),
    then the cached listing is shown without scanning again; "Opnieuw scannen" (pull to
    refresh) and any deletion scan again.
12. Given a NAS photo with an embedded EXIF thumbnail of at least half the requested size,
    when a small preview (up to 512 px: grid, hashing) is shown for the first time, then at
    most the first 512 KB of the file is read from the NAS. Larger previews (deck card,
    zoom) decode the whole file once and are cached. (Refined while building, 2026-11-14:
    EXIF thumbnails are usually 160 px, too small for a deck card.)
13. Given a NAS item whose preview was generated before, when it is shown again (also after
    a restart), then no data is read from the NAS for that preview.
14. Given a NAS video, when its card or grid cell is shown, then a poster frame is shown
    (or a film icon when no frame can be read), without downloading the whole file.
15. Given a NAS video is opened in the inspector, when play is tapped, then it is downloaded
    to a temporary local copy with a progress indicator and then plays.
16. Given a NAS item is shared, then the share sheet receives a local copy of the file via
    the app's FileProvider; temporary copies are deleted at the next app start.
17. Given the NAS source, when the exact-duplicate scan runs, then items are grouped by
    modification second **and** byte size (pixel size is unknown without reading files).
18. Given the NAS source, when the similar scan runs twice without changes, then the second
    run reads no data from the NAS (hashes come from the NAS hash cache).
19. Given NAS items in the in-app trash older than 30 days (or "nu verwijderen"), when the
    user confirms the dialog "Definitief verwijderen van je NAS? Dit kan niet ongedaan
    worden gemaakt.", then the files are deleted on the NAS and leave the in-app trash.
20. Given that dialog is cancelled, or the NAS is unreachable, then all items stay in the
    in-app trash and an error is shown when the NAS was unreachable.
21. Given items from both sources are in the trash or keep list, when one source is active,
    then the trash shows only that source's items, and the other source's entries are
    neither shown, deleted, nor dropped by the missing-item check (`StoreReconciler`).
22. Given "NAS-cache legen" is tapped, then the listing and preview caches are cleared and
    the next load scans the NAS again; the link and the photos are untouched.
23. Given "Huidige NAS-koppeling verwijderen" is confirmed, then the stored settings,
    password, listing and previews are deleted and the phone library becomes the active
    source.
24. Given the merged release manifest, then the only new permission is `INTERNET`;
    `ACCESS_NETWORK_STATE` and `WAKE_LOCK` stay removed.
25. Given the onboarding, Play listing and privacy policy (NL and EN), then none of them
    says the app has no internet access, and each says the app only connects to a NAS on
    the user's own network, and only when the user links one.
26. Given the R8-minified build (`assembleMinifiedCheck`), when a NAS is linked and photos
    are reviewed, then it works as the debug build does (keep rules for smbj, dcerpc,
    BouncyCastle and slf4j in place).

## Edge cases and error states
- Wi-Fi off or another network: load fails → empty state "NAS niet bereikbaar" with
  "Opnieuw proberen" and a hint; the cached listing is still shown if present, previews
  from the disk cache still render.
- NAS goes away mid-scan: the partial listing is not cached (iOS parity); the error state
  is shown.
- Switching source during a scan cancels the scan.
- Unreadable subfolder (permissions): skipped, the scan continues.
- Share or folder deleted or renamed on the NAS since linking: error "Share of map niet
  gevonden — controleer de sharenaam." with a button to change the link.
- File deleted outside the app: dropped from trash/keep by the reconciler (NAS-scoped,
  AC 21) on the next full scan.
- File changed on the NAS (new mtime): preview and hash are regenerated.
- Hostnames: `.local` names are resolved by Android's resolver (mDNS works on Android 12+);
  on Android 11 the user may need the IP address. The connect hint says so.
- SMB1-only servers: not supported by smbj; message "Deze NAS gebruikt het verouderde SMB1;
  zet SMB2 of hoger aan op de NAS."
- Very large folders (50 000+ files): listing runs in parallel with at most 6 concurrent
  directory reads; progress shows the number of items found so far.
- Huge RAW files without embedded thumbnails: full download per preview (as iOS); budget
  below.
- Guest access refused by the server: same message as a wrong password.

## Data and privacy
- **Stored:** host, port, share, folder, username in app-private storage (DataStore /
  existing `AppPreferences`); the password encrypted with AES-256-GCM using a
  non-exportable Android Keystore key, ciphertext in a `noBackupFilesDir` file. Not
  EncryptedSharedPreferences: `androidx.security:security-crypto` is deprecated, and the
  Keystore API needs no dependency. Backup stays off (`allowBackup="false"`).
- **Caches:** folder listing (JSON), generated previews (JPEG), NAS dHash cache, temporary
  share/playback copies — all in app-private cache/files dirs; cleared on unlink.
- **Network:** `INTERNET` permission, used only by the SMB client. Before connecting, the
  host is resolved and every resolved address must be local: IPv4 `10/8`, `172.16/12`,
  `192.168/16`, `169.254/16`, `100.64/10` (CGNAT, used by Tailscale and some VPNs),
  loopback; IPv6 `fc00::/7`, `fe80::/10`, loopback. Otherwise the app refuses (AC 6). The
  connection uses the resolved, checked address, so a DNS answer cannot change between
  check and connect. SMB signing on; SMB3 encryption when the server offers it.
- **Promise changes:** "geen internettoegang" becomes "PhotoCleaner maakt alleen verbinding
  met een NAS op je eigen netwerk, en alleen als je die zelf koppelt. Verder verlaat niets
  je toestel." Recorded in ADR-0006 (supersedes the network part of
  `specs/android-app.md` AC 21).
- **Play data safety:** the developer still receives no data; files only go between the
  phone and the user's own server. Whether Play counts that as "collected" must be checked
  against the current form help and written in `docs/PLAYSTORE.md` (open question).
- **Logs:** no host, username, password or file paths in logs; slf4j has no Android
  binding, so smbj logs go nowhere (`slf4j-nop` or no binding).
- `SECURITY.md` gets the Android password storage line next to the iOS Keychain one.

## Design direction (UI features)
Follow the existing Android screens (`docs/DESIGN.md`, `design/refs/ios/`): the connect
flow is a full-screen Material 3 sheet with the same two steps and Dutch/English strings as
`SMBConnectView.swift` (form section "Server" with footer, shares list, folder list with a
green "Deze map gebruiken" row). The source menu reuses the media header's existing title
area. No new visual language; detail level equal to the Settings screen.

## Performance budget (when it matters)
- Connect + share list: < 3 s on a local network.
- Scan: ≥ 1 000 files/s listed on a Synology over Wi-Fi 5 (6 parallel directory reads).
- Reopen with a cached listing: first card < 1 s.
- Preview from disk cache: < 50 ms; first-time preview with embedded thumbnail: < 500 ms.
- Deck swipes stay at 60 fps while previews load (decoding off the main thread).

## Test plan
- **Unit (JVM):** `LocalAddressPolicyTest` (every allowed/refused range, IPv4 and IPv6,
  mixed DNS answers → refuse); `NasPathsTest` (id ⇄ host/share/path, parent folder,
  skippable folders, extension → kind); `NasListingCacheTest` (round-trip, key per
  host/share/folder, partial scan not saved); `NasCredentialsTest` (stored fields, no
  password in prefs); `DuplicateDetectorTest` new case for unknown pixels + byte size
  (AC 17); `StoreReconcilerTest` new cases for per-source scoping (AC 21);
  `NasErrorMessagesTest` (smbj status codes → Dutch messages); trash flow test with a
  fake permanent-delete source (AC 19–20).
- **Instrumented (emulator):** `PasswordVaultTest` (Keystore encrypt/decrypt, survives a
  process restart); `NasSourceTest` against a local Samba server reachable from the
  emulator at `10.0.2.2:1445` — connect, list shares, scan, preview from prefix, video
  poster, delete; skipped (assumption) when the server is not running. Compose tests:
  `NasConnectScreenTest` (AC 1, error display, step navigation with a fake browser),
  `SettingsScreenTest` Fotobron section, source menu in `MediaScreen`.
- **Test server:** Homebrew `samba` (`smbd`) run unprivileged from `scripts/nas-test-server.sh`
  with a generated config, port 1445, a `photos` share seeded with JPEGs, a HEIC, a RAW
  and a short MP4, user `test` / a generated password written to a git-ignored file.
- **Manual:** connect, review and delete on the emulator against the test server, with
  screenshots of the connect sheet (both steps) and the deck with NAS photos; the minified
  build (AC 26). On a physical phone against Guus's real NAS (after merge, by Guus).
- **Checks:** `./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:lintDebug`
  green.

## Open questions
- Play data-safety form: does phone ↔ user's-own-NAS traffic count as "collected"? To
  verify while updating `docs/PLAYSTORE.md` (step 9); does not block building.
- dcerpc 0.12.13 is built against smbj 0.12.2; we use smbj 0.15.0. Step 2 verifies the
  share enumeration works against the newer smbj; fallback if it does not: a typed share
  name (the option Guus did not pick) — would be raised with Guus first.

## Library choice
- **smbj 0.15.0** (`com.hierynomus:smbj`, Apache-2.0, last release 2026-08-21): SMB 2/3
  with signing and encryption, random-access reads, delete. Runtime deps: `bcprov-jdk18on`,
  `asn-one`, `mbassador`, `slf4j-api`. Jar 0.7 MB; BouncyCastle is large but mostly removed
  by R8.
- **dcerpc 0.12.13** (`com.rapid7.client:dcerpc`, BSD-3, last release 2025-06-04): SRVSVC
  `NetShareEnumAll` for the share list (decision 2026-11-14, iOS parity). Pulls in Guava,
  commons-lang3 and commons-io (R8 shrinks the release; the debug APK grows by several MB).
- Rejected: jCIFS (codelibs 3.0.4, LGPL-2.1, 1.3 MB, older design); jcifs-ng (no release
  since 2023).
- All new artifacts are added to `gradle/verification-metadata.xml` with the regeneration
  command in `CLAUDE.md`.

## Plan (added before building)
Each step ends green on `./gradlew :app:testDebugUnitTest :app:lintDebug` and is its own commit.

1. **Test server and dependencies** — `scripts/nas-test-server.sh` (Homebrew `samba`,
   unprivileged `smbd` on port 1445, generated config and password in git-ignored
   `scripts/.nas-test/`, seeded `photos` share). Add smbj 0.15.0 + dcerpc 0.12.13 to
   `gradle/libs.versions.toml`, regenerate `gradle/verification-metadata.xml`, R8 keep rules
   in `app/proguard-rules.pro`. Spike: a JVM test (skipped without the server) that lists
   shares via dcerpc on smbj 0.15.0 — the open question; stop and ask Guus if it fails. ~2 h
2. **Pure core** — tests first: `LocalAddressPolicyTest`, `NasPathsTest`,
   `NasErrorMessagesTest`, `NasListingCacheTest`. Files in `source/nas/`:
   `LocalAddressPolicy.kt`, `NasPaths.kt` (ids `nas://host:port/share/path`, so ids never
   collide with MediaStore's numeric ids or with another server), `NasConfig.kt`
   (+ `NasConfigStore` in SharedPreferences), `NasErrors.kt`, `NasListingCache.kt`
   (pattern: `service/JsonFile.kt`). ~2 h
3. **Password vault** — `PasswordVault.kt` (Keystore AES-GCM, ciphertext in
   `noBackupFilesDir`); instrumented `PasswordVaultTest`. ~1 h
4. **SMB client and listing** — `NasClient.kt` (smbj connection to the checked address,
   guest/user auth, dcerpc share list, folder list, range reads, delete; at most 6 parallel
   operations), `NasSource.kt` implementing `PhotoSource` (scan, listing cache, on this
   day, assets by id, invalidate, delete). `DuplicateDetector` byte-size case (AC 17) with
   its test first. Instrumented `NasSourceTest` against the test server. ~2 h
5. **Previews, hashes, local copies** — Coil fetcher for a `NasImage` model
   (`NasThumbnails.kt`: 512 KB prefix + EXIF thumbnail, else full download with
   subsampling; JPEG disk cache keyed by id, size and mtime), video posters with
   `MediaMetadataRetriever` over a range-reading `MediaDataSource`, NAS `HashCache`
   (`nasHashes.json`) through `CachedHasher`, local copies for share/play through a
   `FileProvider` (`cache/nas-share`, wiped at start). ~2 h
6. **Sources and per-source stores** — `PhotoSource` gains `kind`, `owns(id)`,
   `deletion` (system trash vs permanent) and `suspend localUri(asset, onProgress)`;
   `SourceManager` (active source persisted, falls back to the phone library when no NAS is
   linked); `StoreReconciler`, `TrashScreen`, `ExpiredTrashPrompt` and the delete flow
   scoped to `owns` (AC 21) with the permanent-delete confirmation (AC 19–20). Tests first:
   `StoreReconcilerTest` and trash-flow cases with `FakePhotoSource`. ~2 h
7. **UI** — `ui/nas/NasConnectScreen.kt` (two steps as `SMBConnectView.swift`), "Fotobron"
   section in `SettingsScreen.kt`, source menu in `MediaHeader.kt`, NAS error/empty state
   and pull-to-refresh in `MediaScreen.kt`, download progress before NAS video playback in
   `MediaInspector.kt`, category filter hidden for NAS. NL + EN strings. Compose tests
   `NasConnectScreenTest`, Settings and header cases. ~2 h
8. **Privacy promise and docs** — `INTERNET` in the manifest (AC 24),
   `docs/decisions/0006-android-local-network-access.md`, onboarding strings, `store/play/
   listing.md`, `docs/privacy-policy.md`, `docs/PLAYSTORE.md` (incl. data-safety answer),
   `docs/PRODUCT.md`, `docs/ARCHITECTURE.md`, `SECURITY.md`, note on `specs/android-app.md`
   AC 21, `CHANGELOG.md`, `CLAUDE.md` test-server command. ~1 h
9. **Verification** — unit, instrumented and lint checks; `assembleMinifiedCheck` on the
   emulator (AC 26); screenshots of both connect steps and the NAS deck in `/tmp`, looked
   at; `STATUS.md`. ~1 h

Risks:
- dcerpc on smbj 0.15.0 (step 1 spike).
- The media-permission gate still blocks the app without phone-library access, also for a
  NAS-only user. Kept as is for this version (iOS also asks for library access first);
  noted in `STATUS.md` as a follow-up.
- Coil's fetcher API and Media3/`MediaMetadataRetriever` with a custom data source are the
  least-known parts; budget may slip in step 5.
- Homebrew `samba` may not start unprivileged on macOS; fallback is impacket's
  `smbserver.py` (SMB2) in a virtualenv, which lists shares too.
