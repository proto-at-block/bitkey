//! P256Box: A libsodium "CryptoBox"-like utility using P-256 ECDH, HKDF SHA256,
//! and ChaCha20-Poly1305.

use chacha20poly1305::{
    aead::{Aead, KeyInit},
    ChaCha20Poly1305, Nonce,
};
use hkdf::Hkdf;
use p256::{
    ecdh::diffie_hellman,
    elliptic_curve::{sec1::ToEncodedPoint, zeroize::Zeroizing},
    pkcs8::{DecodePrivateKey, DecodePublicKey},
    PublicKey, SecretKey,
};
use rand::rngs::OsRng;
use sha2::Sha256;
use thiserror::Error;

const INFO_BYTES: &[u8] = b"p256_box-hkdf_sha256-chacha20_poly1305-v1";

#[derive(Debug, Error)]
pub enum P256BoxError {
    /// Failed to parse public key bytes.
    #[error("Invalid public key")]
    PublicKeyError,

    /// Failed to parse secret key bytes.
    #[error("Invalid secret key")]
    SecretKeyError,

    /// Encryption failure.
    #[error("Failed to encrypt")]
    EncryptError,

    /// Decryption failure.
    #[error("Failed to decrypt")]
    DecryptError,

    /// HKDF key derivation failed.
    #[error("Failed to perform HKDF")]
    HkdfError,

    /// Failed to construct AEAD cipher instance.
    #[error("Failed to instantiate cipher")]
    CipherInstantiationError,

    /// Invalid nonce length.
    #[error("Invalid nonce length")]
    InvalidNonceLengthError,
}

/// P256Box encapsulates ECDH key exchange and AEAD-based encryption.
///
/// The symmetric key is derived per message using:
/// - ECDH over P-256 to compute a shared secret
/// - HKDF-SHA256 with a 12-byte salt (first half of the nonce)
/// - Domain-separated `INFO_BYTES` context
///
/// ChaCha20-Poly1305 uses the remaining 12 bytes of the nonce for encryption.
pub struct P256Box {
    /// Raw shared secret derived via P-256 ECDH
    shared_secret: Zeroizing<[u8; 32]>,
}

impl P256Box {
    /// Constructs a new `P256Box` from a peer's public key and a local secret key.
    ///
    /// # Arguments
    /// - `public_key`: SEC1-encoded public key bytes
    /// - `secret_key`: Raw 32-byte private key
    ///
    /// # Errors
    /// - Returns `PublicKeyError` or `SecretKeyError` if keys are malformed
    pub fn new(public_key: &[u8], secret_key: &[u8]) -> Result<Self, P256BoxError> {
        let sk = match secret_key.len() {
            32 => SecretKey::from_slice(secret_key).map_err(|_| P256BoxError::SecretKeyError)?,
            _ => SecretKey::from_pkcs8_der(secret_key).map_err(|_| P256BoxError::SecretKeyError)?,
        };

        let pk = match public_key.len() {
            91 => PublicKey::from_public_key_der(public_key)
                .map_err(|_| P256BoxError::PublicKeyError)?,
            _ => {
                PublicKey::from_sec1_bytes(public_key).map_err(|_| P256BoxError::PublicKeyError)?
            }
        };

        // Compute shared secret using ECDH (x-coordinate only)
        let shared_secret = Zeroizing::new(
            diffie_hellman(sk.to_nonzero_scalar(), pk.as_affine())
                .raw_secret_bytes()
                .as_slice()
                .try_into()
                .map_err(|_| P256BoxError::HkdfError)?,
        );

        Ok(Self { shared_secret })
    }

    /// Encrypts a plaintext message using a 24-byte nonce.
    ///
    /// # Nonce Format (24 bytes total):
    /// - `nonce[0..12]` → Used as HKDF salt
    /// - `nonce[12..24]` → Used as ChaCha20Poly1305 nonce
    ///
    /// # Returns
    /// - Ciphertext with a 16-byte authentication tag
    ///
    /// # Errors
    /// - Returns `EncryptError`, `HkdfError`, or `CipherInstantiationError`
    pub fn encrypt(&self, nonce: &[u8], plaintext: &[u8]) -> Result<Vec<u8>, P256BoxError> {
        if nonce.len() != 24 {
            return Err(P256BoxError::InvalidNonceLengthError);
        }

        let salt = &nonce[0..12];

        let mut k = Zeroizing::new([0; 32]);
        // Derive AEAD key from shared secret using HKDF
        Hkdf::<Sha256>::new(Some(salt), self.shared_secret.as_slice())
            .expand(&INFO_BYTES, k.as_mut())
            .map_err(|_| P256BoxError::HkdfError)?;

        let cipher = ChaCha20Poly1305::new_from_slice(k.as_ref())
            .map_err(|_| P256BoxError::CipherInstantiationError)?;

        let nonce = Nonce::from_slice(&nonce[12..24]);

        cipher
            .encrypt(nonce, plaintext)
            .map_err(|_| P256BoxError::EncryptError)
    }

    /// Decrypts a ciphertext using the given 24-byte nonce.
    ///
    /// # Nonce Format (24 bytes total):
    /// - `nonce[0..12]` → Used as HKDF salt
    /// - `nonce[12..24]` → Used as ChaCha20Poly1305 nonce
    ///
    /// # Returns
    /// - Decrypted plaintext
    ///
    /// # Errors
    /// - Returns `DecryptError`, `HkdfError`, or `CipherInstantiationError`
    pub fn decrypt(&self, nonce: &[u8], ciphertext: &[u8]) -> Result<Vec<u8>, P256BoxError> {
        if nonce.len() != 24 {
            return Err(P256BoxError::InvalidNonceLengthError);
        }

        let salt = &nonce[0..12];

        let mut k = Zeroizing::new([0; 32]);
        // Derive AEAD key from shared secret using HKDF
        Hkdf::<Sha256>::new(Some(salt), self.shared_secret.as_slice())
            .expand(&INFO_BYTES, k.as_mut())
            .map_err(|_| P256BoxError::HkdfError)?;

        let cipher = ChaCha20Poly1305::new_from_slice(k.as_ref())
            .map_err(|_| P256BoxError::CipherInstantiationError)?;

        let nonce = Nonce::from_slice(&nonce[12..24]);

        cipher
            .decrypt(nonce, ciphertext)
            .map_err(|_| P256BoxError::DecryptError)
    }
}

#[derive(Debug, Error)]
pub enum P256BoxKeyPairError {
    #[error("Failed to create a new P256BoxKeyPair")]
    InstantiationError,
}

pub struct P256BoxKeyPair {
    secret_key: SecretKey,
}

impl Default for P256BoxKeyPair {
    fn default() -> Self {
        Self::new()
    }
}

impl P256BoxKeyPair {
    pub fn new() -> Self {
        let secret_key = SecretKey::random(&mut OsRng);

        Self { secret_key }
    }

    pub fn from_secret_bytes(secret_bytes: Vec<u8>) -> Result<Self, P256BoxKeyPairError> {
        let secret_key = SecretKey::from_slice(&secret_bytes)
            .map_err(|_| P256BoxKeyPairError::InstantiationError)?;

        Ok(Self { secret_key })
    }

    pub fn public_key(&self) -> Vec<u8> {
        self.secret_key
            .public_key()
            .to_encoded_point(true)
            .as_bytes()
            .to_vec()
    }

    pub fn secret_key(&self) -> Vec<u8> {
        self.secret_key.to_bytes().to_vec()
    }
}

// These all mirror crypto_box.rs tests
#[cfg(test)]
mod tests {
    use super::*;
    use quickcheck_macros::quickcheck;
    use rand::RngCore;

    #[test]
    fn test_encrypt_decrypt() {
        let alice_keypair = P256BoxKeyPair::new();
        let bob_keypair = P256BoxKeyPair::new();

        // Alice encrypts
        let alice_crypto_box =
            P256Box::new(&bob_keypair.public_key(), &alice_keypair.secret_key()).unwrap();
        let mut nonce = [0u8; 24];
        rand::thread_rng().fill_bytes(&mut nonce);
        let plaintext = b"Hello, world!";
        let ciphertext = alice_crypto_box.encrypt(&nonce, plaintext).unwrap();

        // Bob decrypts
        let bob_crypto_box =
            P256Box::new(&alice_keypair.public_key(), &bob_keypair.secret_key()).unwrap();
        let decrypted_data = bob_crypto_box.decrypt(&nonce, &ciphertext).unwrap();

        assert_eq!(plaintext.to_vec(), decrypted_data);
    }

    #[test]
    fn test_empty_plaintext() {
        let alice_keypair = P256BoxKeyPair::new();
        let bob_keypair = P256BoxKeyPair::new();

        // Alice encrypts
        let alice_crypto_box =
            P256Box::new(&bob_keypair.public_key(), &alice_keypair.secret_key()).unwrap();
        let mut nonce = [0u8; 24];
        rand::thread_rng().fill_bytes(&mut nonce);
        let empty_plaintext = b"";
        let ciphertext = alice_crypto_box.encrypt(&nonce, empty_plaintext).unwrap();

        // Bob decrypts
        let bob_crypto_box =
            P256Box::new(&alice_keypair.public_key(), &bob_keypair.secret_key()).unwrap();
        let decrypted_data = bob_crypto_box.decrypt(&nonce, &ciphertext).unwrap();

        assert_eq!(empty_plaintext.to_vec(), decrypted_data);
    }

    #[test]
    fn test_encrypt_decrypt_with_bit_flip() {
        let alice_keypair = P256BoxKeyPair::new();
        let bob_keypair = P256BoxKeyPair::new();

        // Alice encrypts
        let alice_crypto_box =
            P256Box::new(&bob_keypair.public_key(), &alice_keypair.secret_key()).unwrap();
        let mut nonce = [0u8; 24];
        rand::thread_rng().fill_bytes(&mut nonce);
        let plaintext = b"Hello, world!";
        let mut ciphertext = alice_crypto_box.encrypt(&nonce, plaintext).unwrap();

        // Flip a bit in the ciphertext
        ciphertext[0] ^= 0x01;

        // Bob attempts to decrypt the tampered ciphertext
        let bob_crypto_box =
            P256Box::new(&alice_keypair.public_key(), &bob_keypair.secret_key()).unwrap();

        let result = bob_crypto_box.decrypt(&nonce, &ciphertext);

        // Check that decryption fails
        assert!(
            result.is_err(),
            "Decryption should fail due to tampered ciphertext"
        );
    }

    #[test]
    fn test_encrypt_decrypt_with_wrong_key() {
        let alice_keypair = P256BoxKeyPair::new();
        let bob_keypair = P256BoxKeyPair::new();

        // Alice encrypts
        let alice_crypto_box =
            P256Box::new(&bob_keypair.public_key(), &alice_keypair.secret_key()).unwrap();
        let mut nonce = [0u8; 24];
        rand::thread_rng().fill_bytes(&mut nonce);
        let plaintext = b"Hello, world!";
        let ciphertext = alice_crypto_box.encrypt(&nonce, plaintext).unwrap();

        // Bob attempts to decrypt the ciphertext with the wrong key
        let charlie_keypair = P256BoxKeyPair::new();
        let bob_crypto_box =
            P256Box::new(&alice_keypair.public_key(), &charlie_keypair.secret_key()).unwrap();

        let result = bob_crypto_box.decrypt(&nonce, &ciphertext);

        // Check that decryption fails
        assert!(result.is_err(), "Decryption should fail due to wrong key");
    }

    #[quickcheck]
    fn test_new_keypair_from_bytes(secret_key: Vec<u8>) {
        let crypto_box_keypair = P256BoxKeyPair::from_secret_bytes(secret_key);

        assert!(
            crypto_box_keypair.is_ok()
                || matches!(
                    crypto_box_keypair,
                    Err(P256BoxKeyPairError::InstantiationError)
                )
        );
    }

    #[quickcheck]
    fn test_new_with_arbitrary_bytes(public_key: Vec<u8>, secret_key: Vec<u8>) {
        let crypto_box = P256Box::new(&public_key, &secret_key);

        assert!(
            crypto_box.is_ok()
                || matches!(
                    crypto_box,
                    Err(P256BoxError::PublicKeyError | P256BoxError::SecretKeyError)
                )
        );
    }

    #[quickcheck]
    fn test_encrypt_decrypt_with_arbitrary_plaintext(plaintext: Vec<u8>) {
        let alice_keypair = P256BoxKeyPair::new();
        let bob_keypair = P256BoxKeyPair::new();

        // Alice encrypts
        let alice_crypto_box =
            P256Box::new(&bob_keypair.public_key(), &alice_keypair.secret_key()).unwrap();
        let mut nonce = [0u8; 24];
        rand::thread_rng().fill_bytes(&mut nonce);
        let ciphertext = alice_crypto_box.encrypt(&nonce, &plaintext).unwrap();

        // Bob decrypts
        let bob_crypto_box =
            P256Box::new(&alice_keypair.public_key(), &bob_keypair.secret_key()).unwrap();
        let decrypted_data = bob_crypto_box.decrypt(&nonce, &ciphertext).unwrap();

        assert_eq!(plaintext.to_vec(), decrypted_data);
    }

    #[quickcheck]
    fn test_encrypt_with_arbitrary_inputs(nonce: Vec<u8>, plaintext: Vec<u8>) {
        let alice_keypair = P256BoxKeyPair::new();
        let bob_keypair = P256BoxKeyPair::new();

        let alice_crypto_box =
            P256Box::new(&bob_keypair.public_key(), &alice_keypair.secret_key()).unwrap();
        let ciphertext = alice_crypto_box.encrypt(&nonce, &plaintext);

        assert!(
            ciphertext.is_ok()
                || matches!(
                    ciphertext,
                    Err(P256BoxError::InvalidNonceLengthError | P256BoxError::EncryptError)
                )
        );
    }

    #[quickcheck]
    fn test_decrypt_with_arbitrary_ciphertext(ciphertext: Vec<u8>) {
        let alice_keypair = P256BoxKeyPair::new();
        let bob_keypair = P256BoxKeyPair::new();

        let mut nonce = [0u8; 24];
        rand::thread_rng().fill_bytes(&mut nonce);

        let bob_crypto_box =
            P256Box::new(&alice_keypair.public_key(), &bob_keypair.secret_key()).unwrap();
        let decrypted_data = bob_crypto_box.decrypt(&nonce, &ciphertext);

        assert!(matches!(decrypted_data, Err(P256BoxError::DecryptError)));
    }

    #[quickcheck]
    fn test_decrypt_with_arbitrary_inputs(nonce: Vec<u8>, ciphertext: Vec<u8>) {
        let alice_keypair = P256BoxKeyPair::new();
        let bob_keypair = P256BoxKeyPair::new();

        let bob_crypto_box =
            P256Box::new(&alice_keypair.public_key(), &bob_keypair.secret_key()).unwrap();
        let decrypted_data = bob_crypto_box.decrypt(&nonce, &ciphertext);

        assert!(matches!(
            decrypted_data,
            Err(P256BoxError::InvalidNonceLengthError | P256BoxError::DecryptError)
        ));
    }
}
