# ADR-0003: Local-first with a 30-day in-app trash

Date: 2026-11-08
Status: accepted (reconstructed from README and code)

## Context
A cleanup app deletes photos, which is scary and easy to regret. Photos are personal and
must never leave the device. Deletion must feel safe and reversible.

## Decision
Keep everything local-first: decisions and a trash list live on-device; no photo is ever
uploaded. Discarded items go to a 30-day in-app trash and disappear from the cleanup views
but stay in the library; within 30 days they can be restored. Only after that (or on manual
confirm) are they really removed — and on the iPhone library that still routes through iOS
"Recently Deleted" as a second safety net.

## Consequences
- Positive: no accidental permanent loss; no privacy exposure; works offline.
- Negative: the app carries trash/retention state; NAS deletion lacks the OS-level
  "Recently Deleted" net, so the in-app trash is the only safeguard there.

## Alternatives considered
- Immediate permanent deletion — unacceptable regret risk.
- Cloud backup before deletion — violates the no-upload privacy stance.
