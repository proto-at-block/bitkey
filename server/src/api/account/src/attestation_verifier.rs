//! Server-side hardware-attestation verifier. Thin wrapper over
//! [`device_attestation`] that prepends the `b"HWV1"` domain prefix and
//! the compressed `hardware_pub` to form the signed payload.

use bdk_utils::bdk::bitcoin::secp256k1::PublicKey;
use device_attestation::AttestationError;

pub mod test_fixture;

/// Domain-separation prefix for the keyset-creation attestation payload:
/// device identity key signs `HWV1 || hardware_pub_compressed`.
const HWV1_PREFIX: &[u8] = b"HWV1";

/// `cert_chain` must contain the device identity cert followed by its
/// batch (intermediate) cert in DER form; the Silicon Labs roots are
/// baked into [`device_attestation`].
pub fn verify_hardware_attestation(
    cert_chain: &[Vec<u8>],
    signature: &[u8],
    hardware_pub: &PublicKey,
) -> Result<String, AttestationError> {
    if cert_chain.len() < 2 {
        return Err(AttestationError::InvalidChain);
    }
    let device = device_attestation::verify_device_identity_chain(&cert_chain[0], &cert_chain[1])?;

    let mut payload = Vec::with_capacity(HWV1_PREFIX.len() + 33);
    payload.extend_from_slice(HWV1_PREFIX);
    payload.extend_from_slice(&hardware_pub.serialize());
    device.verify_signature(&payload, signature)?;

    Ok(device.serial().to_string())
}

#[cfg(test)]
mod tests {
    use super::*;
    use bdk_utils::bdk::bitcoin::secp256k1::{Secp256k1, SecretKey};

    fn dummy_hardware_pub() -> PublicKey {
        let secp = Secp256k1::new();
        let sk = SecretKey::from_slice(&[1u8; 32]).unwrap();
        sk.public_key(&secp)
    }

    #[test]
    fn rejects_too_short_chain() {
        let err = verify_hardware_attestation(&[vec![0u8; 32]], &[0u8; 64], &dummy_hardware_pub())
            .unwrap_err();
        assert_eq!(err, AttestationError::InvalidChain);
    }

    #[test]
    fn rejects_garbage_cert_bytes() {
        let err = verify_hardware_attestation(
            &[vec![0u8; 32], vec![0u8; 32]],
            &[0u8; 64],
            &dummy_hardware_pub(),
        )
        .unwrap_err();
        assert_eq!(err, AttestationError::ParseFailure);
    }
}
