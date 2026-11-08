# Status — PhotoCleaner

Last updated: 2026-11-08

## Now
- Project standard adopted (this branch `chore/adopt-standard`).

## Next
1. Decide what to do with `simphotos2/*.jpg` committed to the public repo (see open questions).
2. Run `swiftformat PhotoCleaner` once as its own commit so the format hook has a clean base.
3. Share the `PhotoCleaner` scheme (and add a minimal CI: build + swiftformat --lint).

## Blocked / open questions
- `simphotos2/feest_a|b|c.jpg` (3 demo photos) are tracked in this **public** repo and not
  git-ignored. Are these throwaway synthetic fixtures, or real personal photos that should be
  removed from git history (`git rm` + rewrite) and ignored? TODO(guus).
- README's architecture section names `NASFolderSource`, but the code implements `SMBSource`.
  Which name is intended? (ARCHITECTURE.md documents the current `SMBSource`.) TODO(guus).

## Recent decisions
- 2026-11-08 — Adopted the project standard — see `docs/decisions/0001-record-architecture-decisions.md`

## Health
Version: 1.0 (build 1) · Build: green (xcodebuild, iOS Simulator) · Tests: none yet · Store: not submitted · Dependencies: SPM, `Package.resolved` committed
