# action-proof

Payload format for actions requiring cryptographic authorization.

Build and parse authorization payloads in 0x1F (Unit Separator) delimited format for signing.

## Usage

```rust
use action_proof::{Action, Field, build_payload, compute_token_binding};

// 1. Compute token binding from JWT
let jwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...";
let token_binding = compute_token_binding(jwt);

// 2. Build payload
let payload = build_payload(
    Action::Add,
    Field::RecoveryContacts,
    Some("Alice"),
    None,
    &[("tb", &token_binding)],
).expect("valid payload");

// 3. Sign the payload (with hardware and/or app key)
// let signature = sign_recoverable(&payload, &private_key);

// 4. Send in Action-Proof header as JSON:
// {"version": 1, "signatures": ["<hex-encoded-65-byte-signature>"], "nonce": "optional"}
// Note: Server reconstructs the payload from the request; client only sends version + signatures
```

## Modules

### payload

Builds and parses payloads in this format:

```
ACTIONPROOF␟1␟Action␟Field␟Value␟Current␟key1=val1,key2=val2
```

`␟` is byte 0x1F (Unit Separator).

- `build_payload()` - Constructs payload with validation

### actions

The `Action` enum represents operations:

- `Add` - Add new value
- `Remove` - Remove existing value
- `Set` - Set or change value
- `Disable` - Disable feature
- `Accept` - Accept invitation

### fields

The `Field` enum identifies what's being modified: `RecoveryContacts`, `SpendWithoutHardware`, `RecoveryEmail`, etc.

Each field knows which actions are valid for it. For example, `RecoveryContacts` allows `Add` and `Remove`, while `SpendWithoutHardware` allows `Set` and `Disable`.

### binding

`compute_token_binding()` creates a 64-character hex string from a JWT using SHA-256 with domain separation (`"ActionProof tb v1"`).

### validation

Validates values for security:

- Maximum 128 bytes
- No control characters (0x00-0x1F, 0x7F)
- No zero-width or directional override characters
- Homoglyph protection (prevents mixing Latin with Cyrillic/Greek/Armenian)

Functions: `validate_value()`, `validate_if_present()`, `is_valid_value()`
