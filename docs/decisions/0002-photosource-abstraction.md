# ADR-0002: Decouple storage behind a PhotoSource protocol

Date: 2026-11-08
Status: accepted (reconstructed from README and code)

## Context
The app must clean up photos from more than one place: the iPhone library (PhotoKit) and a
home NAS (SMB). The review stack, duplicate detection and trash should behave identically
regardless of where the photos live.

## Decision
Introduce a `PhotoSource` protocol and a source-independent `PhotoAsset` model. The UI and
cleanup logic depend only on the protocol; concrete sources (`PhotoKitSource`, `SMBSource`)
implement it. `SourceManager` holds the active source.

## Consequences
- Positive: one UI and one cleanup flow serve both sources; a new source is additive.
- Negative: source-specific capabilities (e.g. PhotoKit categories, NAS folder paths) must
  be modelled generically or degrade gracefully when a source lacks them.

## Alternatives considered
- Separate screens per source — duplicated UI and logic, drifts over time.
- Coupling the UI directly to PhotoKit — would block the NAS use case entirely.
