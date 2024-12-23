use crypto_box::{
    aead::{Aead, OsRng},
    ChaChaBox, PublicKey, SecretKey,
};
use std::array::TryFromSliceError;
use std::sync::Mutex;
use thiserror::Error;

#[derive(Debug, Error)]
pub enum CryptoBoxError {
    #[error("Failed to create a new CryptoBox: {0}")]
    CryptoBoxInstantiationError(#[from] TryFromSliceError),
    #[error("Failed to encrypt")]
    EncryptError,
    #[error("Failed to decrypt")]
    DecryptError,
}

pub struct CryptoBox {
    chacha_box_mutex: Mutex<ChaChaBox>,
}

impl CryptoBox {
    pub fn new(public_key: &[u8], secret_key: &[u8]) -> Result<Self, CryptoBoxError> {
        let public_key = PublicKey::from_slice(public_key)
            .map_err(CryptoBoxError::CryptoBoxInstantiationError)?;
        let secret_key = SecretKey::from_slice(secret_key)
            .map_err(CryptoBoxError::CryptoBoxInstantiationError)?;
        let chacha_box = ChaChaBox::new(&public_key, &secret_key);

        Ok(Self {
            chacha_box_mutex: Mutex::new(chacha_box),
        })
    }

    pub fn encrypt(&self, nonce: &[u8], plaintext: &[u8]) -> Result<Vec<u8>, CryptoBoxError> {
        if nonce.len() != 24 {
            return Err(CryptoBoxError::EncryptError);
        }
        let cipher = self.chacha_box_mutex.lock().unwrap();
        let nonce = crypto_box::Nonce::from_slice(nonce);

        cipher
            .encrypt(nonce, plaintext)
            .map_err(|_| CryptoBoxError::EncryptError)
    }

    pub fn decrypt(&self, nonce: &[u8], ciphertext: &[u8]) -> Result<Vec<u8>, CryptoBoxError> {
        if nonce.len() != 24 {
            return Err(CryptoBoxError::DecryptError);
        }
        let cipher = self.chacha_box_mutex.lock().unwrap();
        let nonce = crypto_box::Nonce::from_slice(nonce);

        cipher
            .decrypt(nonce, ciphertext)
            .map_err(|_| CryptoBoxError::DecryptError)
    }
}

#[derive(Debug, Error)]
pub enum CryptoBoxKeyPairError {
    #[error("Failed to create a new CryptoBoxKeyPair: {0}")]
    CryptoBoxKeyPairInstantiationError(#[from] TryFromSliceError),
}

pub struct CryptoBoxKeyPair {
    secret_key_mutex: Mutex<SecretKey>,
}

impl Default for CryptoBoxKeyPair {
    fn default() -> Self {
        Self::new()
    }
}

impl CryptoBoxKeyPair {
    pub fn new() -> Self {
        let secret_key = SecretKey::generate(&mut OsRng);

        Self {
            secret_key_mutex: Mutex::new(secret_key),
        }
    }

    pub fn from_secret_bytes(secret_bytes: Vec<u8>) -> Result<Self, CryptoBoxKeyPairError> {
        let secret_key = SecretKey::from_slice(&secret_bytes[..])
            .map_err(CryptoBoxKeyPairError::CryptoBoxKeyPairInstantiationError)?;

        Ok(Self {
            secret_key_mutex: Mutex::new(secret_key),
        })
    }

    pub fn public_key(&self) -> Vec<u8> {
        self.secret_key_mutex
            .lock()
            .unwrap()
            .public_key()
            .as_ref()
            .to_vec()
    }

    pub fn secret_key(&self) -> Vec<u8> {
        self.secret_key_mutex.lock().unwrap().to_bytes().to_vec()
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use quickcheck_macros::quickcheck;
    use rand::RngCore;

    #[test]
    fn test_encrypt_decrypt() {
        let alice_keypair = CryptoBoxKeyPair::new();
        let bob_keypair = CryptoBoxKeyPair::new();

        // Alice encrypts
        let alice_crypto_box =
            CryptoBox::new(&bob_keypair.public_key(), &alice_keypair.secret_key()).unwrap();
        let mut nonce = [0u8; 24];
        rand::thread_rng().fill_bytes(&mut nonce);
        let plaintext = b"Hello, world!";
        let ciphertext = alice_crypto_box.encrypt(&nonce, plaintext).unwrap();

        // Bob decrypts
        let bob_crypto_box =
            CryptoBox::new(&alice_keypair.public_key(), &bob_keypair.secret_key()).unwrap();
        let decrypted_data = bob_crypto_box.decrypt(&nonce, &ciphertext).unwrap();

        assert_eq!(plaintext.to_vec(), decrypted_data);
    }

    #[test]
    fn test_empty_plaintext() {
        let alice_keypair = CryptoBoxKeyPair::new();
        let bob_keypair = CryptoBoxKeyPair::new();

        // Alice encrypts
        let alice_crypto_box =
            CryptoBox::new(&bob_keypair.public_key(), &alice_keypair.secret_key()).unwrap();
        let mut nonce = [0u8; 24];
        rand::thread_rng().fill_bytes(&mut nonce);
        let empty_plaintext = b"";
        let ciphertext = alice_crypto_box.encrypt(&nonce, empty_plaintext).unwrap();

        // Bob decrypts
        let bob_crypto_box =
            CryptoBox::new(&alice_keypair.public_key(), &bob_keypair.secret_key()).unwrap();
        let decrypted_data = bob_crypto_box.decrypt(&nonce, &ciphertext).unwrap();

        assert_eq!(empty_plaintext.to_vec(), decrypted_data);
    }

    #[test]
    fn test_encrypt_decrypt_with_bit_flip() {
        let alice_keypair = CryptoBoxKeyPair::new();
        let bob_keypair = CryptoBoxKeyPair::new();

        // Alice encrypts
        let alice_crypto_box =
            CryptoBox::new(&bob_keypair.public_key(), &alice_keypair.secret_key()).unwrap();
        let mut nonce = [0u8; 24];
        rand::thread_rng().fill_bytes(&mut nonce);
        let plaintext = b"Hello, world!";
        let mut ciphertext = alice_crypto_box.encrypt(&nonce, plaintext).unwrap();

        // Flip a bit in the ciphertext
        ciphertext[0] ^= 0x01;

        // Bob attempts to decrypt the tampered ciphertext
        let bob_crypto_box =
            CryptoBox::new(&alice_keypair.public_key(), &bob_keypair.secret_key()).unwrap();

        let result = bob_crypto_box.decrypt(&nonce, &ciphertext);

        // Check that decryption fails
        assert!(
            result.is_err(),
            "Decryption should fail due to tampered ciphertext"
        );
    }

    #[test]
    fn test_encrypt_decrypt_with_wrong_key() {
        let alice_keypair = CryptoBoxKeyPair::new();
        let bob_keypair = CryptoBoxKeyPair::new();

        // Alice encrypts
        let alice_crypto_box =
            CryptoBox::new(&bob_keypair.public_key(), &alice_keypair.secret_key()).unwrap();
        let mut nonce = [0u8; 24];
        rand::thread_rng().fill_bytes(&mut nonce);
        let plaintext = b"Hello, world!";
        let ciphertext = alice_crypto_box.encrypt(&nonce, plaintext).unwrap();

        // Bob attempts to decrypt the ciphertext with the wrong key
        let charlie_keypair = CryptoBoxKeyPair::new();
        let bob_crypto_box =
            CryptoBox::new(&alice_keypair.public_key(), &charlie_keypair.secret_key()).unwrap();

        let result = bob_crypto_box.decrypt(&nonce, &ciphertext);

        // Check that decryption fails
        assert!(result.is_err(), "Decryption should fail due to wrong key");
    }

    #[quickcheck]
    fn test_new_keypair_from_bytes(secret_key: Vec<u8>) {
        let crypto_box_keypair = CryptoBoxKeyPair::from_secret_bytes(secret_key);

        assert!(
            crypto_box_keypair.is_ok()
                || matches!(
                    crypto_box_keypair,
                    Err(CryptoBoxKeyPairError::CryptoBoxKeyPairInstantiationError(_))
                )
        );
    }

    #[quickcheck]
    fn test_new_with_arbitrary_bytes(public_key: Vec<u8>, secret_key: Vec<u8>) {
        let crypto_box = CryptoBox::new(&public_key, &secret_key);

        assert!(
            crypto_box.is_ok()
                || matches!(
                    crypto_box,
                    Err(CryptoBoxError::CryptoBoxInstantiationError(_))
                )
        );
    }

    #[quickcheck]
    fn test_encrypt_decrypt_with_arbitrary_plaintext(plaintext: Vec<u8>) {
        let alice_keypair = CryptoBoxKeyPair::new();
        let bob_keypair = CryptoBoxKeyPair::new();

        // Alice encrypts
        let alice_crypto_box =
            CryptoBox::new(&bob_keypair.public_key(), &alice_keypair.secret_key()).unwrap();
        let mut nonce = [0u8; 24];
        rand::thread_rng().fill_bytes(&mut nonce);
        let ciphertext = alice_crypto_box.encrypt(&nonce, &plaintext).unwrap();

        // Bob decrypts
        let bob_crypto_box =
            CryptoBox::new(&alice_keypair.public_key(), &bob_keypair.secret_key()).unwrap();
        let decrypted_data = bob_crypto_box.decrypt(&nonce, &ciphertext).unwrap();

        assert_eq!(plaintext.to_vec(), decrypted_data);
    }

    #[quickcheck]
    fn test_encrypt_with_arbitrary_inputs(nonce: Vec<u8>, plaintext: Vec<u8>) {
        let alice_keypair = CryptoBoxKeyPair::new();
        let bob_keypair = CryptoBoxKeyPair::new();

        let alice_crypto_box =
            CryptoBox::new(&bob_keypair.public_key(), &alice_keypair.secret_key()).unwrap();
        let ciphertext = alice_crypto_box.encrypt(&nonce, &plaintext);

        assert!(ciphertext.is_ok() || matches!(ciphertext, Err(CryptoBoxError::EncryptError)));
    }

    #[quickcheck]
    fn test_decrypt_with_arbitrary_ciphertext(ciphertext: Vec<u8>) {
        let alice_keypair = CryptoBoxKeyPair::new();
        let bob_keypair = CryptoBoxKeyPair::new();

        let mut nonce = [0u8; 24];
        rand::thread_rng().fill_bytes(&mut nonce);

        let bob_crypto_box =
            CryptoBox::new(&alice_keypair.public_key(), &bob_keypair.secret_key()).unwrap();
        let decrypted_data = bob_crypto_box.decrypt(&nonce, &ciphertext);

        assert!(matches!(decrypted_data, Err(CryptoBoxError::DecryptError)));
    }

    #[quickcheck]
    fn test_decrypt_with_arbitrary_inputs(nonce: Vec<u8>, ciphertext: Vec<u8>) {
        let alice_keypair = CryptoBoxKeyPair::new();
        let bob_keypair = CryptoBoxKeyPair::new();

        let bob_crypto_box =
            CryptoBox::new(&alice_keypair.public_key(), &bob_keypair.secret_key()).unwrap();
        let decrypted_data = bob_crypto_box.decrypt(&nonce, &ciphertext);

        assert!(matches!(decrypted_data, Err(CryptoBoxError::DecryptError)));
    }
}
