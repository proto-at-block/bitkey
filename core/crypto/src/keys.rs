use bitcoin::hashes::sha256;
use bitcoin::secp256k1::ecdsa::Signature;
use bitcoin::secp256k1::{
    Error as BitcoinError, Message, Secp256k1, SecretKey as BitcoinSecretKey,
};
use std::sync::{Mutex, MutexGuard};

pub use bitcoin::secp256k1::PublicKey;

#[derive(Debug, thiserror::Error)]
pub enum SecretKeyError {
    #[error(transparent)]
    InvalidSecretBytes(#[from] BitcoinError),
}

pub struct SecretKey(Mutex<BitcoinSecretKey>);

impl SecretKey {
    // For usage with `DescriptorSecretKey` exposed via `BDK`, pass the result of `secret_bytes`
    // into this constructor.
    pub fn new(secret_bytes: Vec<u8>) -> Result<Self, SecretKeyError> {
        let seckey = BitcoinSecretKey::from_slice(&secret_bytes)?;

        Ok(Self(Mutex::new(seckey)))
    }

    pub fn sign_message(&self, message: Vec<u8>) -> Signature {
        // ECDSA signatures when messages are not hashed are considered insecure
        // https://www.rfc-editor.org/rfc/rfc6979#section-2.4
        let message = Message::from_hashed_data::<sha256::Hash>(&message);

        Secp256k1::signing_only().sign_ecdsa(&message, &self.inner())
    }

    pub fn as_public(&self) -> PublicKey {
        self.inner().public_key(&Secp256k1::new())
    }

    pub fn inner(&self) -> MutexGuard<BitcoinSecretKey> {
        self.0.lock().unwrap()
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use quickcheck_macros::quickcheck;
    use rand::RngCore;

    #[test]
    fn test_sign_message() {
        let mut random_bytes = [0u8; 32];
        rand::thread_rng().fill_bytes(&mut random_bytes);
        let secret_key = SecretKey::new(random_bytes.to_vec()).unwrap();
        let message = b"hello world";
        let signature = secret_key.sign_message(message.to_vec());
        let secp = Secp256k1::verification_only();
        let hashed_message = Message::from_hashed_data::<sha256::Hash>(message);
        assert!(secp
            .verify_ecdsa(&hashed_message, &signature, &secret_key.as_public())
            .is_ok());
        let invalid_message = b"helloworld!";
        let hashed_invalid_message = Message::from_hashed_data::<sha256::Hash>(invalid_message);
        assert!(secp
            .verify_ecdsa(&hashed_invalid_message, &signature, &secret_key.as_public())
            .is_err());
        let invalid_signature = Signature::from_compact(&[0u8; 64]).unwrap();
        assert!(secp
            .verify_ecdsa(&hashed_message, &invalid_signature, &secret_key.as_public())
            .is_err());
    }

    #[quickcheck]
    fn test_new_with_arbitrary_bytes(secret_bytes: Vec<u8>) {
        let key = SecretKey::new(secret_bytes);

        assert!(key.is_ok() || matches!(key, Err(SecretKeyError::InvalidSecretBytes(_))));
    }

    #[quickcheck]
    fn test_sign_message_with_arbitrary_bytes(message: Vec<u8>) {
        let mut random_bytes = [0u8; 32];
        rand::thread_rng().fill_bytes(&mut random_bytes);
        let secret_key = SecretKey::new(random_bytes.to_vec()).unwrap();
        let signature = secret_key.sign_message(message.clone());
        let secp = Secp256k1::verification_only();
        let hashed_message = Message::from_hashed_data::<sha256::Hash>(&message);

        assert!(secp
            .verify_ecdsa(&hashed_message, &signature, &secret_key.as_public())
            .is_ok());
    }
}
