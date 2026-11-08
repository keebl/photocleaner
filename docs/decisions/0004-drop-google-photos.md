# ADR-0004: Drop Google Photos as a source

Date: 2026-11-08
Status: accepted (reconstructed from README)

## Context
Google Photos was considered as a third photo source alongside the iPhone library and NAS.

## Decision
Do not support Google Photos. Its API does not allow deleting photos, which is the core
action of a cleanup app; a source you can only read from cannot serve the product's purpose.

## Consequences
- Positive: avoids a half-working source that frustrates the main use case.
- Negative: users who keep photos only in Google Photos are not served.

## Alternatives considered
- Read-only Google Photos (review/mark only) — misleading in a delete-oriented app.
