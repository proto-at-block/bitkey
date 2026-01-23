---
name: implement-f8e-client
description: Implement an F8e HTTP client. Fromagerie API, network, Ktor, request/response bodies, NetworkingError
---

# Implement F8e Client

## Before Starting

**Search for similar F8e clients first:**
- Search `:domain:f8e-client` for existing client implementations
- Look at request/response body patterns in similar clients
- Check how authentication, error handling, and retries are implemented

For Ktor HTTP client questions, consult external Ktor documentation.

## Phase 1: Interface

Add interface to `:domain:f8e-client:public`. Follow naming and method conventions in @docs/docs/mobile/architecture/f8e-http-clients.md.

## Phase 2: Request/Response Bodies

Define serializable bodies implementing `RedactedRequestBody`/`RedactedResponseBody`. See @docs/docs/mobile/architecture/f8e-http-clients.md.

## Phase 3: Implementation

Add implementation to `:domain:f8e-client:impl`. See @docs/docs/mobile/architecture/f8e-http-clients.md for patterns and @docs/docs/mobile/architecture/dependency-injection.md for DI.

## Phase 4: Fake

Add fake to `:domain:f8e-client:fake`. No unit tests - clients are tested via service tests.

## Rules

- Stateless and side-effect-free
- No unit tests; use fakes in service tests

## References

@docs/docs/mobile/architecture/f8e-http-clients.md
@docs/docs/mobile/architecture/dependency-injection.md
@docs/docs/mobile/architecture/domain-service-testing.md
