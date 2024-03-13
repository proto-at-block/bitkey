use std::sync::Mutex;

use bitcoin::{
    hashes::sha256,
    secp256k1::{ecdsa::Signature, Error as Secp256k1Error, Message, PublicKey, Secp256k1},
};
use thiserror::Error;

#[derive(Debug, Error)]
pub enum SignatureVerifierError {
    #[error("Secp256k1 operation failed: {0}")]
    Secp256k1Error(#[from] Secp256k1Error),
}

pub struct SignatureVerifier(Mutex<Signature>);

impl SignatureVerifier {
    pub fn new(signature: &[u8]) -> Result<Self, SignatureVerifierError> {
        let signature = Signature::from_der(signature)?;

        Ok(Self(Mutex::new(signature)))
    }

    pub fn verify_ecdsa(
        &self,
        message: &[u8],
        pubkey: &[u8],
    ) -> Result<(), SignatureVerifierError> {
        let pubkey = PublicKey::from_slice(pubkey)?;
        let secp = Secp256k1::verification_only();
        let message = Message::from_hashed_data::<sha256::Hash>(message);
        let sig = self.0.lock().unwrap();

        Ok(secp.verify_ecdsa(&message, &sig, &pubkey)?)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use bitcoin::secp256k1::SecretKey;
    use rand::RngCore;

    #[test]
    fn test_verify_signature() {
        let mut random_bytes = [0u8; 32];
        rand::thread_rng().fill_bytes(&mut random_bytes);
        let secret_key = SecretKey::from_slice(&random_bytes).unwrap();
        let message = b"hello world";
        let hashed_message = Message::from_hashed_data::<sha256::Hash>(message);
        let secp = Secp256k1::new();

        let signature = secp
            .sign_ecdsa(&hashed_message, &secret_key)
            .serialize_der();
        let verifier = SignatureVerifier::new(&signature).unwrap();

        assert!(verifier
            .verify_ecdsa(message, &secret_key.public_key(&secp).serialize())
            .is_ok());

        let invalid_message = b"helloworld!";

        assert!(verifier
            .verify_ecdsa(invalid_message, &secret_key.public_key(&secp).serialize())
            .is_err());

        let mut invalid_signature = signature.to_vec();
        invalid_signature[5] ^= 1;
        let verifier = SignatureVerifier::new(&invalid_signature).unwrap();

        assert!(verifier
            .verify_ecdsa(message, &secret_key.public_key(&secp).serialize())
            .is_err());

        let invalid_encoding = vec![0u8; 64];
        let verifier = SignatureVerifier::new(&invalid_encoding);

        assert!(verifier.is_err());
    }
}
