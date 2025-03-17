///
/// This module implements the NFC secure channel protocol, supported
/// by firmware as of version 1.0.65 (the GA release firmware).
///
/// This protocol is used to establish shared session keys used to encrypt
/// protobuf fields over the NFC channel.
///
/// Protocol details:
///
/// One-way authenticated ECDH using X25519.
///   * Authentication is done using the device's unique identity certificate.
///   * Derived keys are HKDF'd with a fixed label.
///   * Key confirmation uses HMAC with a fixed label.
///   * Derives separate keys for sending and receiving, to protect against reflection.
///
/// This is essentially SIGMA (https://www.iacr.org/cryptodb/archive/2003/CRYPTO/1495/1495.pdf)
/// See section 5.1.
///
use hkdf::hmac::Hmac;
use hkdf::Hkdf;
use hmac::Mac;
use p256::ecdsa::signature::Verifier;
use p256::ecdsa::{Signature, VerifyingKey};
use p256::PublicKey as P256PublicKey;
use ring::agreement::{agree_ephemeral, EphemeralPrivateKey, PublicKey, UnparsedPublicKey, X25519};
use ring::rand::SystemRandom;
use sha2::Sha256;
use thiserror::Error;
use x509_parser::certificate::X509Certificate;

#[derive(Error, Debug, PartialEq)]
pub enum SecureChannelError {
    #[error("failed to verify signature")]
    VerificationFailure,
    #[error("invalid certificate")]
    InvalidCertificate,
    #[error("invalid signature format")]
    InvalidSignatureFormat,
    #[error("invalid signature")]
    InvalidSignature,
    #[error("failed to derive session keys")]
    DerivationFailure,
    #[error("key confirmation failed")]
    KeyConfirmationFailed,
    #[error("failed to generate ephemeral key")]
    KeyGenerationFailure,
    #[error("failed to agree ephemeral key")]
    KeyAgreementFailure,
}

pub struct SessionKeys {
    pub send_key: [u8; 32],
    pub recv_key: [u8; 32],
    pub conf_key: [u8; 32],
}

#[derive(Debug)]
pub struct X25519Keypair {
    secret: EphemeralPrivateKey,
    pub public: PublicKey,
}

pub fn generate_ephemeral_x25519_keypair() -> Result<X25519Keypair, SecureChannelError> {
    let mut csprng = SystemRandom::new();

    let sk = EphemeralPrivateKey::generate(&X25519, &mut csprng)
        .map_err(|_| SecureChannelError::KeyGenerationFailure)?;
    let pk = sk
        .compute_public_key()
        .map_err(|_| SecureChannelError::KeyGenerationFailure)?;

    Ok(X25519Keypair {
        secret: sk,
        public: pk,
    })
}

// Egregious type juggling to acquire the public key from the certificate
pub fn extract_pk_from_cert(cert: &X509Certificate) -> Result<VerifyingKey, SecureChannelError> {
    let pk = cert.public_key().raw;
    let pk =
        P256PublicKey::from_sec1_bytes(pk).map_err(|_| SecureChannelError::InvalidCertificate)?;
    let pk = VerifyingKey::from(&pk);
    Ok(pk)
}

/// Verify the signature of the device identity certificate over the key-exchange message.
/// `their_identity_pk` is *trusted* here. This function assumes that the hardware attestation
/// dance has already been performed.
pub fn verify_exchange_signature(
    their_pk: &PublicKey, // The firmware's ephemeral public key for x25519
    our_pk: &PublicKey,   // Our ephemeral public key for x25519
    their_identity_pk: &VerifyingKey, // The device's identity public key, provisioned at manufacturing
    signature: &Signature,
) -> Result<(), SecureChannelError> {
    let mut signing_input = b"KEYEXCHANGE-V1".to_vec();
    signing_input.extend_from_slice(their_pk.as_ref());
    signing_input.extend_from_slice(our_pk.as_ref());
    their_identity_pk
        .verify(&signing_input, signature)
        .map_err(|_| SecureChannelError::VerificationFailure)
}

fn prepare_label(prefix: &[u8], device_serial: &[u8]) -> Vec<u8> {
    let mut label = prefix.to_vec();
    label.extend_from_slice(device_serial);
    label
}

fn derive_single_session_key(
    hkdf: &Hkdf<Sha256>,
    label: &[u8],
) -> Result<[u8; 32], SecureChannelError> {
    const KEY_LEN: usize = 32;
    let mut okm = [0u8; KEY_LEN];
    hkdf.expand(label, &mut okm)
        .map_err(|_| SecureChannelError::DerivationFailure)?;
    Ok(okm)
}

pub fn derive_session_keys(
    our_keypair: X25519Keypair,
    their_pk: &PublicKey,
    device_serial: &[u8],
) -> Result<SessionKeys, SecureChannelError> {
    let unparsed_pk = UnparsedPublicKey::new(&X25519, their_pk.as_ref());

    agree_ephemeral(our_keypair.secret, &unparsed_pk, |shared_secret| {
        // No salt. ikm is the shared secret.
        let hkdf = Hkdf::<Sha256>::new(None, shared_secret);

        let send_key =
            derive_single_session_key(&hkdf, prepare_label(b"HOST2BK", device_serial).as_slice())?;
        let recv_key =
            derive_single_session_key(&hkdf, prepare_label(b"BK2HOST", device_serial).as_slice())?;
        let conf_key =
            derive_single_session_key(&hkdf, prepare_label(b"CONFIRM", device_serial).as_slice())?;

        Ok(SessionKeys {
            send_key,
            recv_key,
            conf_key,
        })
    })
    .map_err(|_| SecureChannelError::KeyAgreementFailure)?
}

pub fn key_confirmation(expected_tag: &[u8], conf_key: &[u8]) -> Result<(), SecureChannelError> {
    const KEY_CONFIRMATION_TAG_LEN: usize = 16;

    let mut hmac = Hmac::<Sha256>::new_from_slice(conf_key)
        .map_err(|_| SecureChannelError::KeyConfirmationFailed)?;

    hmac.update(b"KEYCONFIRM-V1");

    let result = hmac.finalize().into_bytes();

    let truncated_tag = &result[..KEY_CONFIRMATION_TAG_LEN];
    if truncated_tag != expected_tag {
        return Err(SecureChannelError::KeyConfirmationFailed);
    }

    Ok(())
}

#[cfg(test)]
mod tests {
    use bitcoin::secp256k1::rand;
    use p256::ecdsa::{signature::SignerMut, SigningKey};
    use ring::agreement::PublicKey;

    use super::*;

    const DEVICE_SERIAL: &[u8] = b"DEVSERIAL1234";

    #[derive(Debug)]
    struct DeviceResponse {
        exchange_pk: PublicKey,    // x25519
        identity_pk: VerifyingKey, // p256
        exchange_signature: Vec<u8>,
        key_confirmation_tag: [u8; 16],
    }

    // Mock out sending the device command `secure_channel_establish_cmd`
    // and receiving the response.
    fn establish_cmd(host_pub: &PublicKey) -> DeviceResponse {
        // Generate ephemeral keypair

        let device_eph_keypair = generate_ephemeral_x25519_keypair().unwrap();
        let device_eph_pk = device_eph_keypair.public;
        let device_eph_sk = device_eph_keypair.secret;

        // Compute shared secret
        let unparsed_pk = UnparsedPublicKey::new(&X25519, host_pub.as_ref());
        let shared_secret = agree_ephemeral(device_eph_sk, &unparsed_pk, |key_material| {
            key_material.to_vec()
        })
        .unwrap();

        // Derive key confirmation key
        let info_label = [b"CONFIRM", DEVICE_SERIAL].concat();

        let hkdf = Hkdf::<Sha256>::new(None, shared_secret.as_ref());
        let mut conf_key = [0u8; 32];
        hkdf.expand(&info_label, &mut conf_key).unwrap();

        // Sign the key-exchange: KEYEXCHANGE-V1 || pk_device || pk_host
        // using a mock P-256 identity key.
        let mut rng = rand::thread_rng();
        let mut device_ecc_identity_key = SigningKey::random(&mut rng);

        let mut signing_input = b"KEYEXCHANGE-V1".to_vec();
        signing_input.extend_from_slice(device_eph_pk.as_ref());
        signing_input.extend_from_slice(host_pub.as_ref());

        let exchange_sig: Signature = device_ecc_identity_key.sign(&signing_input);
        let exchange_sig_bytes = exchange_sig.to_vec();

        // Key confirmation
        let mut hmac = Hmac::<Sha256>::new_from_slice(&conf_key).expect("HMAC key must be valid");
        hmac.update(b"KEYCONFIRM-V1");
        let full_tag = hmac.finalize().into_bytes();

        let mut key_confirmation_tag = [0u8; 16];
        key_confirmation_tag.copy_from_slice(&full_tag[..16]);

        DeviceResponse {
            exchange_pk: device_eph_pk,
            identity_pk: *device_ecc_identity_key.verifying_key(),
            exchange_signature: exchange_sig_bytes,
            key_confirmation_tag,
        }
    }

    #[test]
    fn test_round_trip() {
        let our_keypair = generate_ephemeral_x25519_keypair().unwrap();

        // Fake what the firmware would do.
        let device_response = establish_cmd(&our_keypair.public);
        let signature = Signature::from_slice(&device_response.exchange_signature).unwrap();

        // 1) Verify the exchange signature
        let verified = verify_exchange_signature(
            &device_response.exchange_pk,
            &our_keypair.public,
            &device_response.identity_pk,
            &signature,
        );
        assert!(verified.is_ok());

        // 2) Derive the session keys
        let session_keys =
            derive_session_keys(our_keypair, &device_response.exchange_pk, DEVICE_SERIAL);
        assert!(session_keys.is_ok());
        let session_keys = session_keys.unwrap();

        // 3) Key confirmation
        let key_confirmation = key_confirmation(
            &device_response.key_confirmation_tag,
            &session_keys.conf_key,
        );
        assert!(key_confirmation.is_ok());
    }

    #[test]
    fn test_signature_tamper_fails_verification() {
        let our_keypair = generate_ephemeral_x25519_keypair().unwrap();
        let mut device_response = establish_cmd(&our_keypair.public);

        // Flip byte in sig to tamper
        if !device_response.exchange_signature.is_empty() {
            device_response.exchange_signature[0] ^= 0xFF;
        }

        let signature = Signature::from_slice(&device_response.exchange_signature).unwrap();

        // Verification should fail
        let verified = verify_exchange_signature(
            &device_response.exchange_pk,
            &our_keypair.public,
            &device_response.identity_pk,
            &signature,
        );
        assert_eq!(
            verified,
            Err(SecureChannelError::VerificationFailure),
            "Expected InvalidSignature error, got: [{:?}]",
            verified
        );
    }

    #[test]
    fn test_tampered_key_confirmation_fails() {
        let our_keypair = generate_ephemeral_x25519_keypair().unwrap();
        let mut device_response = establish_cmd(&our_keypair.public);
        let signature = Signature::from_slice(&device_response.exchange_signature).unwrap();

        let verified = verify_exchange_signature(
            &device_response.exchange_pk,
            &our_keypair.public,
            &device_response.identity_pk,
            &signature,
        );
        assert!(verified.is_ok());

        let new_keypair = generate_ephemeral_x25519_keypair().unwrap();
        device_response.exchange_pk = new_keypair.public;

        let session_keys =
            derive_session_keys(our_keypair, &device_response.exchange_pk, DEVICE_SERIAL);
        assert!(session_keys.is_ok());
        let session_keys = session_keys.unwrap();

        let key_confirmation = key_confirmation(
            &device_response.key_confirmation_tag,
            &session_keys.conf_key,
        );
        assert!(key_confirmation.is_err());
    }
}
