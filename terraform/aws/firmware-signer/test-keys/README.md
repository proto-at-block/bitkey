# Test Keys

This directory contains development keys for testing the firmware signing service infrastructure.

## Source

These keys are sourced from the miner-firmware repository:
https://github.com/btc-mining/miner-firmware/tree/main/distro/sources/meta-miner/recipes-dev/miner-signing-keys/files/dev/keys/dev/c3/intermediate

## Structure

```
test-keys/
└── dev/
    └── c3/
        └── intermediate/
            ├── c3-dm-verity-rootfs-signing-key-dev.priv.pem
            ├── c3-leaf-fwup-signing-key-dev.priv.pem
            ├── c3-leaf-trusted-app-signing-key-dev.priv.pem
            ├── c3-linux-image-signing-key-dev.priv.pem
            ├── c3-nontrusted-os-firmware-key-dev.priv.pem
            ├── c3-nontrusted-world-key-dev.priv.pem
            ├── c3-trusted-os-firmware-key-dev.priv.pem
            └── c3-trusted-world-key-dev.priv.pem
```

## Usage

These keys are used by the `scripts/wrap-and-upload-dev-keys.sh` script to:
1. Wrap the keys using the c3 KMS wrapping key
2. Upload the wrapped keys to AWS Secrets Manager

**⚠️ WARNING:** These are development keys only and should never be used in production environments.
