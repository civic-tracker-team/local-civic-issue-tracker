# ADR-0031: Configure separate internal read, processing and maintenance permissions

- **Status:** Accepted
- **Date:** 2026-11-08
- **Recorded:** 2026-11-13
- **Refined:** 2026-11-13, command roles and CSRF exchange
- **Deciders:** Kazim Yigit Ablay
- **Related requirements:** LIM-09, NFR-15, NFR-17

## Context

Internal APIs expose retained originals and commands that change processing state. A private route name or Docker network does not determine which caller can read, submit or resume work.

## Decision

Java services use Spring Security with externally configured service identities. Records uses HTTP Basic authentication with bcrypt password hashes and separate read-only, processing and maintenance permissions. Unconfigured identities receive no access, and permissions name implemented routes and methods rather than an entire future route namespace.

Processing POSTs retain CSRF protection through an explicit client token exchange. Communication across machines requires HTTPS, while the loopback HTTP exception is limited to local operation and cannot be enabled by untrusted forwarding headers.

These credentials authorize internal service work. The decision does not add a browser login or end-user account system.

## Alternatives considered

Network placement alone would not authenticate callers. One shared unrestricted credential would make read access sufficient for recovery commands. An OAuth identity service could support a larger deployment but is not required for the current configured service clients.

## Consequences

- **Positive:** Read access does not imply authority to submit work or reconcile failures.
- **Negative:** Clients must manage credentials and command tokens, and proxy deployment needs an explicit trust configuration.
- **Follow-up:** Verify denied routes, methods and roles as interfaces change. OCR uses its separate service-only bearer credential.

## AI assistance and provenance

Codex helped evaluate configured credentials and check route permissions. Kazim accepted service authentication and the later processing and maintenance separation.

## References

- SRS v2.0
- ADR-0008: Service data ownership
- ADR-0021: Imported-content boundary
- ADR-0037: OCR recovery
