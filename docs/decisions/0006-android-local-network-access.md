# ADR-0006: Android network access for the NAS source, local network only

Date: 2026-11-14
Status: accepted

## Context
Android v1 had no `INTERNET` permission, and the Play listing, onboarding and privacy policy
promised "no internet access" (`specs/android-app.md` AC 21). The NAS source
(`specs/android-nas.md`) needs a TCP connection to the user's SMB server, which Android only
allows with `INTERNET`. Android has no permission for "local network only", so the
permission itself cannot keep that promise.

## Decision
Add `INTERNET` and enforce the limit in code. Before any SMB connection, `LocalAddressPolicy`
resolves the server name and requires every resolved address to be local: IPv4 `10/8`,
`172.16/12`, `192.168/16`, `169.254/16`, `100.64/10` (CGNAT, used by Tailscale and some
VPNs) or loopback; IPv6 `fc00::/7`, `fe80::/10` or loopback. Otherwise the app refuses. The
connection goes to the checked address, so a DNS answer cannot change between check and
connect. Nothing else in the app uses the network; no analytics, no developer server.

The promise changes from "no internet access" to "only connects to a NAS on your own network,
and only when you link one". Onboarding, the Play listing, the privacy policy and
`docs/PLAYSTORE.md` say so. This supersedes the network part of `specs/android-app.md` AC 21.

## Consequences
- Positive: Android reaches parity with iOS for NAS users; the privacy promise stays
  concrete and checkable (unit tests for every range, an instrumented test that a public
  address is refused).
- Negative: the manifest now shows `INTERNET`, which some users read as "goes online";
  the copy has to explain the local-only rule.
- Negative: a NAS reached through a public address or DDNS name is not supported, also when
  it is the user's own; a VPN such as Tailscale works.
- Negative: the check depends on code, not on the OS; a future network use must go through
  the same policy (reviewed in code review).

## Alternatives considered
- Allow any address the user types — simpler and covers DDNS, but weakens the promise to
  "only the server you enter"; Guus chose local-only (2026-11-14).
- A separate "NAS" build flavour with `INTERNET` — two listings to maintain, and Play users
  cannot pick a flavour.
- No NAS on Android — keeps the old promise, but leaves NAS users without the feature.
