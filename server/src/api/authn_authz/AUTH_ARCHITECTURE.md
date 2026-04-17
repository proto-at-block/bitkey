# Authorization Architecture

This document describes the full authentication and authorization stack in `authn_authz`. It covers everything from raw HTTP headers to the single `execute()` call that routes use to authorize actions and perform state changes.

---

## Overview

Authorization is split into two distinct concepts:

- **Credentials** (`Authorization` extractor): Client-provided proof — JWT, optional signatures, nonce, and public keys. Extracted automatically by Axum before the handler runs.
- **Policy** (`AuthorizationRequirements`): Server-authoritative requirements — what action is being performed, what hardware type is expected, what values must be signed, and how many signers are required. Routes define this.

`execute()` is the bridge. It takes credentials and policy, verifies them together, runs the caller's closure with an `AuthorizedContext`, and handles anti-replay automatically for Action Proof flows.

---

## Layer 1: JWT Validation (Axum Middleware)

**Module**: `authorizer.rs`

Every request to an account-scoped route passes through a JWT validation middleware _before_ the handler runs. This middleware:

1. Validates the JWT signature against Cognito's OIDC signing keys (refreshed every ~24 hours)
2. Validates the `exp` claim
3. Validates that the `sub` (`username`) in the token corresponds to the `account_id` in the URL path
4. Stores the validated `TokenData<AccessTokenClaims>` in request extensions

Three path flavors:

| Middleware | Allowed token types |
|---|---|
| `authorize_token_for_path` | App key or Hardware key (most routes) |
| `authorize_recovery_token_for_path` | Recovery key only |
| `authorize_account_or_recovery_token_for_path` | Any key for the account |

**If JWT validation fails**: 401 Unauthorized is returned immediately; the handler never runs.

The `Authorization` extractor (Layer 2) trusts that this middleware has already run — it reads `TokenData` from extensions without re-validating the signature.

---

## Layer 2: Authorization Extractor

**Module**: `authorization.rs`, `key_claims.rs` (pub(crate))

`Authorization` is an Axum `FromRequestParts` extractor that runs once per handler invocation. It:

1. Reads the JWT from the `Authorization: Bearer <token>` header
2. Reads `AccountId` and `token_exp` from the already-validated `TokenData` in request extensions
3. Detects the auth mechanism:

### Action Proof path (`Action-Proof` header present)

```
Action-Proof: {"version":1,"signatures":["<hex>","<hex>"],"nonce":"a3"}
```

- Fetches the account's app and hardware public keys from Cognito
- Stores raw header data (version, signatures, nonce) in `AuthorizationInner::ActionProof`
- **Signature verification is deferred** to `execute()` — this allows the route to bind the action, value, and entity ID before verification runs

### KeyClaims path (no `Action-Proof` header)

```
X-App-Signature: <hex-ecdsa-sig>
X-Hw-Signature:  <hex-ecdsa-sig>
```

- Fetches public keys from Cognito
- Verifies ECDSA signatures over the raw JWT string immediately in the extractor
- Stores the boolean results (`hw_signed`, `app_signed`) in `AuthorizationInner::KeyClaims`

The resulting `Authorization` struct exposes `account_id`, `username`, and `token_exp` to the route, with the auth mechanism stored in the private `inner` field.

---

## Layer 3: AuthorizationRequirements + execute()

**Module**: `authorization.rs`, `authorized_action.rs` (pub(crate)), `signers.rs`

Routes declare policy using `AuthorizationRequirements` and finalize it with `execute()`.

### Constructors

**`AuthorizationRequirements::new(action, HardwareType)`**

Use for routes that support Action Proof (W3) or have hardware-type-specific behavior.

- Enforces auth mechanism: W3 accounts must use Action Proof; W1 accounts must use KeyClaims. Mismatches return 403.
- Binds an `Action` for Action Proof signature verification (ignored for KeyClaims; documents intent only).
- Defaults to `Signers::All`.

**`AuthorizationRequirements::keyclaims_only()`**

Use for routes that only support KeyClaims today and haven't been migrated to Action Proof.

- Defaults to `HardwareType::W1` (Action Proof rejected at hardware type check)
- No action binding
- **TODO**: Routes using this should be migrated to `new(action, HardwareType)` as W3 Action Proof support is added per-route.

### Builder Methods

```rust
AuthorizationRequirements::new(Action::SetRecoveryEmail, hardware_type)
    .signers(Signers::All)          // signature requirements
    .value(&email)                  // bind the value that was signed
    .entity_id(&touchpoint_id)      // bind the entity being acted on
    .execute(&auth, &repo, |ctx| async move { ... })
    .await?
```

### Signers

`Signers` controls how many signatures are required from the client:

| Variant | Behavior |
|---|---|
| `Signers::All` | Both app **and** hardware signatures required |
| `Signers::Any` | At least one signature (app **or** hardware) |
| `Signers::None` | JWT alone is sufficient; no additional signatures checked |

`Signers::None` is used for **W1 accounts during onboarding** — the hardware device may not yet be provisioned, so requiring a hardware signature would block the initial setup flow. After onboarding completes, `Signers::All` is enforced.

`SignerRequirements` allows different requirements per auth mechanism:

```rust
.signers(SignerRequirements {
    action_proof: Signers::All,
    key_claims: Signers::Any,
})
```

When a plain `Signers` value is passed, it applies to both mechanisms equally.

### execute() — The Primary Terminal Method

`execute()` is the primary way to use `AuthorizationRequirements`. It verifies credentials, handles anti-replay, and runs the caller's closure. `check()` is also public for callers that manage anti-replay externally (e.g., the privileged action service), but **`execute()` should be preferred** for all new code.

What `execute()` does:

1. Calls `check()` internally: validates hardware type, verifies signatures, checks `Signers`
2. For Action Proof: checks anti-replay cache (`exists()`). On replay: deserializes and returns cached response. Closure is **not called**.
3. For Action Proof: constructs an `AntiReplayGuard` with `content_hash` + `token_exp` + repo
4. For KeyClaims: constructs a noop `AntiReplayGuard` (zero DynamoDB overhead)
5. Calls the closure with `AuthorizedContext`
6. On closure success: burns content hash with serialized response
7. On closure failure: hash is **not burned**; retry is allowed

The closure receives an `AuthorizedContext` with:
- `account_id()` — the verified account
- `hw_signed()` — whether the hardware key signed
- `app_signed()` — whether the app key signed

**Closure return type constraint**: `T: Serialize + DeserializeOwned`. The response must be serializable to cache it for anti-replay. Do not return `Json<T>` from the closure — wrap in `Json` outside of `execute()`.

---

## Hardware Type Enforcement

`HardwareType` is stored on the account's active spending keyset and reflects the hardware generation:

| Hardware type | Required auth mechanism |
|---|---|
| `HardwareType::W1` | KeyClaims (`X-App-Signature`, `X-Hw-Signature`) |
| `HardwareType::W3` | Action Proof (`Action-Proof` header) |

Enforcement happens in `check()` before signature verification. Sending KeyClaims to a W3 account (or Action Proof to a W1 account) returns 403 immediately.

---

## Action Proof Internals

**Module**: `action_proof.rs`

Action Proof provides cryptographic binding between a client action and the server's policy. The client signs a canonical payload that encodes the action, value, nonce, and a token binding.

### Canonical Payload

```
<action>:<value>\n<sorted bindings>
```

Where bindings are key-value pairs sorted lexicographically:
- `n` = nonce (1 byte, 2 lowercase hex chars, e.g. `"a3"`)
- `eid` = entity ID (if present)
- `tb` = token binding = `SHA-256("tb:v1:" + jwt)` (ties the proof to this specific token)

The content hash = `SHA-256(canonical)`. Used as the anti-replay cache key.

### Signature Verification

- Each signature in the `Action-Proof` header is an ECDSA compact signature (64 bytes, hex-encoded)
- Verified against `SHA-256(canonical)` using the account's registered public keys
- Duplicate signatures (same key used twice) are rejected
- Unknown key signatures (doesn't match app or hw key) are rejected

### Nonce

The nonce is a 1-byte random value (2 lowercase hex chars). It is included in the canonical payload to differentiate requests with identical action/value/entity combinations. For example, adding and re-adding the same email address would otherwise produce the same content hash.

---

## Full Request Flow

```
HTTP Request
    │
    ▼
JWT Middleware (authorizer.rs)
    • Validate Cognito JWT signature
    • Check exp claim
    • Check account_id matches URL path
    • Store TokenData in request extensions
    │
    │ 401 if invalid
    ▼
Authorization Extractor (authorization.rs)
    • Read JWT from Authorization: Bearer header
    • Read TokenData from extensions (already validated)
    • If Action-Proof header present:
        ─ Fetch pubkeys from Cognito
        ─ Store raw signatures/nonce → AuthorizationInner::ActionProof
      Else:
        ─ Fetch pubkeys from Cognito
        ─ Verify X-App-Signature and X-Hw-Signature over JWT
        ─ Store results → AuthorizationInner::KeyClaims
    │
    ▼
Route Handler
    • Fetch account to get HardwareType
    • Build AuthorizationRequirements:
        AuthorizationRequirements::new(action, hardware_type)
          .signers(Signers::All)
          .value(&email)
          .execute(&auth, &anti_replay_repo, |ctx| async move {
              // state change here
              Ok::<_, ApiError>(response)
          }).await?
    │
    ▼
execute() (authorization.rs + authorized_action.rs)
    • check():
        ─ Enforce hardware type (W3 rejects KeyClaims, W1 rejects ActionProof)
        ─ For ActionProof: verify_action_proof() → content_hash
        ─ validate_signature_requirements(hw_signed, app_signed, signers)
    • For ActionProof:
        ─ exists(content_hash) in DynamoDB
        ─ If cached: deserialize + return (closure NOT called)
        ─ If fresh: construct AntiReplayGuard
    • For KeyClaims: construct noop AntiReplayGuard
    • Call closure with AuthorizedContext
    • On success: burn(content_hash + response) in DynamoDB
    • On failure: return error, hash NOT burned
    │
    ▼
Response to Client
```

---

## Module Map

| Module | Visibility | Contents |
|---|---|---|
| `authorizer` | `pub` | JWT middleware functions |
| `authorization` | `pub` | `Authorization` extractor, `AuthorizationRequirements`, `AuthorizedContext` |
| `key_claims` | `pub(crate)` | `KeyClaims` extractor, ECDSA verification over JWT, header constants |
| `action_proof` | `pub` | `verify_action_proof`, `validate_signature_requirements`, `ActionProofHeader` |
| `signers` | `pub` | `Signers`, `SignerRequirements`, `IntoSignerRequirements` |
| `authorized_action` | `pub(crate)` | `AntiReplayGuard`, `AuthorizedAction` with `execute()` |
| `anti_replay` | `pub` | `ANTI_REPLAY_TTL_BUFFER_SECS`, `content_hash_to_hex` |
| `routes` | `pub` | Cognito user pool management routes |
| `test_utils` | `pub` | Test helpers for constructing signed requests |

---

## Anti-Replay

See [`../privileged_action/ANTI_REPLAY.md`](../privileged_action/ANTI_REPLAY.md) for the full design, including DynamoDB table schema, TTL tradeoffs, TOCTOU analysis, and the cached-response-on-replay behavior.
