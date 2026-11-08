# Status — PhotoCleaner

Last updated: 2026-11-08

## Now
- Project standard adopted (this branch `chore/adopt-standard`).

## Next
1. Run `swiftformat PhotoCleaner` once as its own commit so the format hook has a clean base.
2. Share the `PhotoCleaner` scheme (and add a minimal CI: build + swiftformat --lint).

## Blocked / open questions
- README's architecture section names `NASFolderSource`, but the code implements `SMBSource`.
  Which name is intended? (ARCHITECTURE.md documents the current `SMBSource`.) TODO(guus).

## Recent decisions
- 2026-11-08 — Adopted the project standard — see `docs/decisions/0001-record-architecture-decisions.md`

## Health
Version: 1.0 (build 1) · Build: green (xcodebuild, iOS Simulator) · Tests: none yet · Store: not submitted · Dependencies: SPM, `Package.resolved` committed
