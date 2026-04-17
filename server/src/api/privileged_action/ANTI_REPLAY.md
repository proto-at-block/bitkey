# Anti-Replay Cache Design

## Overview

The anti-replay cache prevents replay of Action Proof signatures within the auth token validity window. It is a DynamoDB-backed, content-hash-keyed cache that "burns" a proof after a successful server-side state change, blocking future replays while enabling idempotent mobile retry via cached response return.

### Why content hash, not signature?

The cache key is `SHA-256(canonical_payload)` -- the hash of the canonical data that gets signed, not the signature itself. This eliminates signature malleability as a concern entirely: even if an attacker produces a different valid signature over the same payload, the content hash is identical and the replay is detected. No low-S normalization or signature canonicalization is needed.

The canonical payload includes: action, field, value, nonce, and a token binding (`SHA-256("tb:v1:" + jwt)`). The token binding ties the proof to a specific JWT, so the same action signed under a different token produces a different content hash.

---

## Request Lifecycle

```
Client sends request with Action-Proof header
         |
         v
[1] JWT validated by Axum middleware (exp check, Cognito signature)
         |
         v
[2] Authorization extractor parses Action-Proof header, fetches pubkeys from Cognito
         |
         v
[3] Route calls: AuthorizationRequirements::new(action, hardware_type)
                   .signers(Signers::All)
                   .value(&email)
                   .execute(&auth, &anti_replay_repository, |ctx| async { ... })
         |
         v
[4] execute() calls check() internally:
    - Verifies ECDSA signatures over canonical payload
    - Computes content_hash = SHA-256(canonical)
    - Returns AuthorizedContext with content_hash
         |
         v
[5] exists(content_hash) -- consistent read from DynamoDB
    |                    \
    | None (fresh)        | Some(cached_json) (replay)
    v                     v
[6] Build AntiReplayGuard   Deserialize cached response
    with content_hash       Return cached result to caller
         |                  (closure is NEVER re-executed)
         v
[7] Closure performs the real state change (receives AuthorizedContext)
    - On success: serialize result, guard.burn(response_json) writes to DDB
    - On failure: guard is NOT burned (retry allowed)
         |
         v
[8] Client receives response
```

### Burn-after-success with response caching

The burn stores the serialized response alongside the content hash. On replay, the cached response is returned directly -- the closure is **never re-executed**. This eliminates the requirement for closures to be idempotent.

- **First request**: `exists()` -> None -> action succeeds -> `burn(response_json)` -> response returned
- **Response lost**: client never sees the 200
- **Retry**: `exists()` -> Some(cached_json) -> deserialize -> return cached result (no closure execution)
- **After token expires**: DynamoDB TTL cleans up the entry

If the action fails, the content hash is never burned. The client can retry and the action will run again.

---

## The `execute()` Pattern

### Problem

The anti-replay burn must happen after a successful state change, not before. But the state change happens in the route handler. If we return an `AuthorizedContext` directly to the route, every developer must remember to manually call `burn()` after their state change -- and forgetting is a silent security bug.

### Solution

`execute()` is the **only terminal method** on `AuthorizationRequirements`. The route handler cannot access the `AuthorizedContext` except through `execute()`, which wires the anti-replay lifecycle automatically:

```rust
let result = AuthorizationRequirements::new(action, hardware_type)
    .signers(Signers::All)
    .value(&email)
    .execute(&auth, &anti_replay_repository, |ctx| async move {
        // ctx: AuthorizedContext with account_id, hw_signed, app_signed
        // Closure performs the real state change
        account_service.activate_touchpoint(ActivateTouchpointInput {
            account_id: ctx.account_id(),
            touchpoint_id,
            dry_run: false,
        }).await?;
        Ok::<_, ApiError>(AccountActivateTouchpointResponse {})
    }).await?;
Ok(Json(result))
```

Key behaviors:

- **`check()` is private**: `execute()` calls it internally. There is no way to get an `AuthorizedContext` without also running anti-replay.
- **Closure failure = no burn**: The `?` on the closure short-circuits before `burn()` is reached.
- **Serialization failure = error, no burn**: If `serde_json::to_string` fails, an error is returned **without burning the hash**.
- **Closures must return `T: Serialize + DeserializeOwned`** (not `Json<T>`) so the response can be cached. The `Json(...)` wrapper goes outside `execute()`.

### Defense in depth

- **`#[must_use]`** on `AuthorizedAction`: The compiler warns if the action is discarded without calling `.execute()`.
- **Noop guards**: For KeyClaims (W1) flows, `content_hash` is `None` so the guard is a noop. `burn()` and `exists()` are never called -- zero DynamoDB overhead.

---

## Architecture

### DynamoDB Table

| Attribute | Type | Purpose |
|-----------|------|---------|
| `content_hash` (PK) | String | SHA-256 hex of canonical payload |
| `expiring_at` | Number | Epoch seconds for DynamoDB TTL |
| `created_at` | String | Timestamp from server clock (debugging) |
| `cached_response` | String | JSON-serialized response from the original successful request |

- **TTL**: `jwt.exp + 300s` (token expiration + 5-minute buffer for clock skew). With a 5-minute Cognito token, entries live ~10 minutes.
- **Consistent reads**: `exists()` uses `consistent_read(true)` to guarantee we see the latest burn.
- **Conditional writes**: `burn()` uses `attribute_not_exists(content_hash)` for atomic dedup. Returns `false` if already burned (concurrent request).
- **Fail-closed**: DynamoDB errors on both `exists()` and `burn()` reject the request rather than allowing a potential replay.

### Crate Structure

- **`repository::anti_replay`** -- DynamoDB operations (`burn` with response storage, `exists` with cached response retrieval, table creation with TTL)
- **`authn_authz::anti_replay`** -- Constants (`ANTI_REPLAY_TTL_BUFFER_SECS`), `content_hash_to_hex()` helper
- **`authn_authz::action_proof`** -- `ActionProofResult` with `content_hash: [u8; 32]`
- **`authn_authz::authorized_action`** (pub(crate)) -- `AntiReplayGuard`, `AuthorizedAction` with `execute()`; anti-replay lifecycle lives here
- **`authn_authz::authorization`** -- `AuthorizationRequirements::execute()` (the single public entrypoint), `AuthorizedContext`

### Server Bootstrap

The `AntiReplayRepository` is created at server startup, the DynamoDB table is created via `create_table_if_necessary()`, and the repository is injected into each `RouteState` that handles Action Proof routes (e.g., `onboarding::RouteState`, `recovery::delay_notify::RouteState`). Route handlers extract it via `State<AntiReplayRepository>` and pass it to `execute()`.

---

## Key Tradeoffs

### TOCTOU race between `exists()` and `burn()` -- accepted

There is a time-of-check-to-time-of-use gap: between `exists()` returning None and `burn()` completing, a concurrent request with the same content hash could pass the check. This is accepted because:

1. The window is milliseconds within a 5-minute token lifetime
2. Requires the same JWT on two concurrent requests from the same mobile client
3. The `burn()` conditional write ensures at most one extra duplicate
4. After burn, all future replays are blocked and return the cached response

### 5-minute token window

Cognito access tokens expire after 5 minutes. This means:

- The entire replay threat window is 5 minutes
- Anti-replay entries live ~10 minutes (token exp + buffer), then auto-cleanup via DynamoDB TTL
- Retries must happen within the token lifetime or the client needs a new token (which produces a different content hash)

### Response caching eliminates idempotency requirement

On replay, the cached response is returned directly from DynamoDB -- the closure never re-runs. This means route closures do **not** need to be idempotent. Side effects like third-party API calls (e.g., Iterable upsert) are only executed once, on the original request.

### Cached response deserialization failure -- fail-closed

If the cached response JSON cannot be deserialized into the expected response type (e.g., the response schema changed during a rolling deploy), `execute()` returns an error. This is fail-closed: response schemas must be backwards-compatible.

This is preferred over silently re-executing the closure because:

1. Re-executing with a noop guard would bypass replay protection entirely during rolling deploys
2. Non-idempotent side effects (e.g., third-party API calls) would run a second time
3. Response schema changes should be backwards-compatible -- this is enforced by design
4. The error is transient: the DynamoDB TTL (~10 minutes) will clean up the stale entry, after which the client can retry with a fresh token

---
