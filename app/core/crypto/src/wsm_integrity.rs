use bitcoin::hashes::sha256;
use bitcoin::secp256k1::ecdsa::Signature;
use bitcoin::secp256k1::{Message, Secp256k1};
use bitcoin::util::base58::from_check as decode_check;
use hex;
use std::sync::Mutex;
use thiserror::Error;

pub use bitcoin::secp256k1::PublicKey;

pub struct WsmIntegrityVerifier(Mutex<PublicKey>);

pub enum WsmContext {
    DeriveKeyV1,
    CreateKeyV1,
}

#[derive(Error, Debug)]
pub enum WsmIntegrityVerifierError {
    #[error("MalformedSignature")]
    MalformedSignature,
    #[error("MalformedPublicKey")]
    MalformedPublicKey,
    #[error("Base58DecodeFailure")]
    Base58DecodeFailure,
    #[error("Base16DecodeFailure")]
    Base16DecodeFailure,
}

impl WsmContext {
    fn to_bytes(&self) -> &'static [u8] {
        match self {
            WsmContext::DeriveKeyV1 => b"DeriveKeyV1",
            WsmContext::CreateKeyV1 => b"CreateKeyV1",
        }
    }
}

const GLOBAL_CONTEXT: &[u8] = b"WsmIntegrityV1";

impl WsmIntegrityVerifier {
    pub fn new(public_key: PublicKey) -> Self {
        Self(Mutex::new(public_key))
    }

    pub fn verify(
        &self,
        base58_message: String,
        signature: String,
    ) -> Result<bool, WsmIntegrityVerifierError> {
        // Turns out it's kind of hard for the app to know which context to use, so we allow both.
        // Since 'derive' and 'create' key is essentially the same operation, this is totally fine.
        // If we ever add more context labels, we should not just add them to the list, provided the
        // operation in the enclave is meaningfully different.
        let contexts = vec![WsmContext::DeriveKeyV1, WsmContext::CreateKeyV1];

        let unhashed_message = decode_check(&base58_message)
            .map_err(|_| WsmIntegrityVerifierError::Base58DecodeFailure)?;

        let signature_bytes =
            hex::decode(signature).map_err(|_| WsmIntegrityVerifierError::Base16DecodeFailure)?;
        let sig = Signature::from_compact(&signature_bytes)
            .map_err(|_| WsmIntegrityVerifierError::MalformedSignature)?;

        let pk = self
            .0
            .lock()
            .map_err(|_| WsmIntegrityVerifierError::MalformedPublicKey)?;

        for ctx in contexts {
            let mut hash_input = Vec::new();
            hash_input.extend_from_slice(GLOBAL_CONTEXT);
            hash_input.extend_from_slice(ctx.to_bytes());
            hash_input.extend_from_slice(&unhashed_message);

            let digest = Message::from_hashed_data::<sha256::Hash>(&hash_input);
            if Secp256k1::new().verify_ecdsa(&digest, &sig, &pk).is_ok() {
                // Verification successful, exit early
                return Ok(true);
            }
        }

        Ok(false)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_verifier() {
        let test_key = "03078451e0c1e12743d2fdd93ae7d03d5cf7813d2f612de10904e1c6a0b87f7071";
        let raw_key = hex::decode(test_key).unwrap();
        let pk = PublicKey::from_slice(&raw_key).unwrap();
        let verifier = WsmIntegrityVerifier::new(pk);

        let message = "tpubDDXfJeMxwsGKevLkFsJ9LDc4m999EYHmzgh161aD94FeJFKPuLFfGB57CA2EJgM18DFYL9vq6oXodXpDTdbZg6k6UqnoHhYmh4n6CYe1KmD";
        let signature = "aa0d883dff66d7d627369e46458faf1bbb7c41e4365ab52541e0d4333b79839218e3dcecb34ee1b671fcf382bf10277ace84b5943e2599eef92d576b5a7a156d";

        let result = verifier.verify(message.to_string(), signature.to_string());
        assert!(result.is_ok());
        assert!(result.unwrap());
    }
}
