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
    use quickcheck_macros::quickcheck;
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

        assert!(matches!(
            verifier.verify_ecdsa(invalid_message, &secret_key.public_key(&secp).serialize()),
            Err(SignatureVerifierError::Secp256k1Error(_))
        ));

        let mut invalid_signature = signature.to_vec();
        invalid_signature[5] ^= 1;
        let verifier = SignatureVerifier::new(&invalid_signature).unwrap();

        assert!(matches!(
            verifier.verify_ecdsa(message, &secret_key.public_key(&secp).serialize()),
            Err(SignatureVerifierError::Secp256k1Error(_))
        ));

        let invalid_encoding = vec![0u8; 64];
        let verifier = SignatureVerifier::new(&invalid_encoding);

        assert!(matches!(
            verifier,
            Err(SignatureVerifierError::Secp256k1Error(_))
        ));
    }

    #[quickcheck]
    fn test_new_with_arbitrary_signature(signature: Vec<u8>) {
        let verifier = SignatureVerifier::new(&signature);
        assert!(
            verifier.is_ok() || matches!(verifier, Err(SignatureVerifierError::Secp256k1Error(_)))
        )
    }

    #[quickcheck]
    fn test_verify_signature_with_arbitrary_valid_message(message: Vec<u8>) {
        let mut random_bytes = [0u8; 32];
        rand::thread_rng().fill_bytes(&mut random_bytes);
        let secret_key = SecretKey::from_slice(&random_bytes).unwrap();
        let hashed_message = Message::from_hashed_data::<sha256::Hash>(&message);
        let secp = Secp256k1::new();

        let signature = secp.sign_ecdsa(&hashed_message, &secret_key);
        let verifier = SignatureVerifier::new(&signature.serialize_der()).unwrap();

        assert!(verifier
            .verify_ecdsa(&message, &secret_key.public_key(&secp).serialize())
            .is_ok());
    }

    #[quickcheck]
    fn test_verify_signature_with_arbitrary_invalid_message(message: Vec<u8>) {
        let mut random_bytes = [0u8; 32];
        rand::thread_rng().fill_bytes(&mut random_bytes);
        let secret_key = SecretKey::from_slice(&random_bytes).unwrap();
        let hashed_message = Message::from_hashed_data::<sha256::Hash>(b"hello world");
        let secp = Secp256k1::new();

        let signature = secp.sign_ecdsa(&hashed_message, &secret_key);
        let verifier = SignatureVerifier::new(&signature.serialize_der()).unwrap();

        assert!(matches!(
            verifier.verify_ecdsa(&message, &secret_key.public_key(&secp).serialize()),
            Err(SignatureVerifierError::Secp256k1Error(_))
        ));
    }

    #[quickcheck]
    fn test_verify_signature_with_arbitrary_public_key(pubkey: Vec<u8>, message: Vec<u8>) {
        let mut random_bytes = [0u8; 32];
        rand::thread_rng().fill_bytes(&mut random_bytes);
        let secret_key = SecretKey::from_slice(&random_bytes).unwrap();
        let hashed_message = Message::from_hashed_data::<sha256::Hash>(&message);
        let secp = Secp256k1::new();

        let signature = secp.sign_ecdsa(&hashed_message, &secret_key);
        let verifier = SignatureVerifier::new(&signature.serialize_der()).unwrap();

        assert!(matches!(
            verifier.verify_ecdsa(&message, &pubkey),
            Err(SignatureVerifierError::Secp256k1Error(_))
        ));
    }
}