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

        Secp256k1::new().sign_ecdsa(&message, &self.inner())
    }

    pub fn as_public(&self) -> PublicKey {
        self.inner().public_key(&Secp256k1::new())
    }

    pub fn inner(&self) -> MutexGuard<BitcoinSecretKey> {
        self.0.lock().unwrap()
    }
}
