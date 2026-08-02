# Dev PKI for Core-Sim Attestation

This directory contains a development certificate chain for testing attestation flows
with the core-sim emulator.

## Certificate Chain

```
Dev Root CA (self-signed)
    |
    +-- Dev Factory Intermediate
            |
            +-- Dev Batch Certificate
                    |
                    +-- Device Certificate (generated at runtime by core-sim)
```

## Files

| File | Description | Committed |
|------|-------------|-----------|
| `dev-root-cert.der` | Root CA certificate | Yes |
| `dev-factory-cert.der` | Factory intermediate certificate | Yes |
| `dev-batch-cert.der` | Batch certificate | Yes |
| `dev-batch.key` | Batch private key (for runtime signing) | Yes |
| `dev-root.key` | Root CA private key | No (gitignored) |
| `dev-factory.key` | Factory private key | No (gitignored) |

## Usage

### Core-Sim
When `CORE_SIM_PROVISION=1` is set, core-sim will:
1. Generate a random P-256 keypair
2. Generate a random 8-byte serial
3. Create a device certificate signed by the batch key
4. Return these via SE stub functions

### App Verification
The app must be built with `--features dev-attestation` to verify devices
using this dev certificate chain.

## Regenerating the PKI

If you need to regenerate the certificate chain:

```bash
./generate-dev-pki.sh
```

Note: This will invalidate any existing device certificates that were signed
with the previous batch key.

## Security Note

These are **development-only** certificates. The batch private key is committed
to the repo intentionally since it's needed at runtime for the simulator.
This key should NEVER be used for production devices.
