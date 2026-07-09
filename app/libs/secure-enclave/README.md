# Secure Enclave

This module provides the mobile app abstraction for hardware-backed P-256 keys.
Use it for key generation, public-key lookup, and Diffie-Hellman operations
where the private key should remain in platform secure hardware.

## Current API

`SecureEnclave` supports:

- generating P-256 key pairs from a `SeKeySpec`;
- loading a key pair by name;
- deriving a public key from a stored private-key handle;
- performing ECDH with a stored private key and peer public key; and
- detecting fake implementations in tests.

`SeKeySpec` defines the key name, purposes, usage constraints, and optional
validity window. Current key purposes are signing and agreement. Current usage
constraints are none, biometrics-or-PIN, and PIN-only.

## Platform Support

The shared Kotlin module has an Android production implementation that stores
keys in the Android Keystore. It requests StrongBox when available and rejects
generated keys that are not backed at least by a TEE.

The iOS app wires a Swift production `SecureEnclaveImpl` into the shared
`SecureEnclave` interface. That implementation stores keys with Apple's Secure
Enclave token. Tests use `SecureEnclaveFake`.

The current implementation only supports P-256 because that is the portable
curve expected by the mobile secure-hardware use cases. Public keys are encoded
as SEC1 uncompressed points (`0x04 || x || y`).

## Consumers

Current consumers include:

- `HardwareBackedDhImpl`, which adapts secure-enclave P-256 ECDH for Noise; and
- `SelfSovereignBackupImpl`, which uses secure-enclave backed local wrapping
  keys for self-sovereign backup material.

The imported Secure Enclave experiment page was deleted during documentation
cleanup. This README is the maintained module-level source of truth.
