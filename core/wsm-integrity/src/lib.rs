use bitcoin::base58::decode_check;
use bitcoin::hashes::{sha256, Hash};
use bitcoin::secp256k1::ecdsa::Signature;
use bitcoin::secp256k1::{Message, Secp256k1};

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
    #[error("PublicKeyLockFailure")]
    PublicKeyLockFailure,
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
        let unhashed_message = decode_check(&base58_message)
            .map_err(|_| WsmIntegrityVerifierError::Base58DecodeFailure)?;
        self.verify_with_unhashed_message(&unhashed_message, &signature)
    }

    pub fn verify_hex_message(
        &self,
        hex_message: String,
        signature: String,
    ) -> Result<bool, WsmIntegrityVerifierError> {
        let unhashed_message =
            hex::decode(hex_message).map_err(|_| WsmIntegrityVerifierError::Base16DecodeFailure)?;
        self.verify_with_unhashed_message(&unhashed_message, &signature)
    }

    /// Verify a signature over 5 concatenated public keys using the `SignPublicKeysV1` context.
    ///
    /// The WSM enclave signs `SHA256("WsmIntegrityV1" + "SignPublicKeysV1" + concat(keys))`,
    /// where each key is a 33-byte compressed secp256k1 public key provided as hex.
    pub fn verify_public_keys(
        &self,
        app_auth_pub_hex: String,
        hardware_auth_pub_hex: String,
        app_spending_pub_hex: String,
        hardware_spending_pub_hex: String,
        server_spending_pub_hex: String,
        signature: String,
    ) -> Result<bool, WsmIntegrityVerifierError> {
        // Parse and concatenate all 5 public keys (33 bytes each)
        let keys_hex = [
            &app_auth_pub_hex,
            &hardware_auth_pub_hex,
            &app_spending_pub_hex,
            &hardware_spending_pub_hex,
            &server_spending_pub_hex,
        ];
        let mut payload = Vec::with_capacity(33 * 5);
        for key_hex in &keys_hex {
            let key_bytes =
                hex::decode(key_hex).map_err(|_| WsmIntegrityVerifierError::Base16DecodeFailure)?;
            // Validate it's a valid 33-byte compressed public key
            if key_bytes.len() != 33 {
                return Err(WsmIntegrityVerifierError::MalformedPublicKey);
            }
            let pk = PublicKey::from_slice(&key_bytes)
                .map_err(|_| WsmIntegrityVerifierError::MalformedPublicKey)?;
            payload.extend_from_slice(&pk.serialize());
        }

        self.verify_with_context(b"SignPublicKeysV1", &payload, &signature)
    }

    fn verify_with_unhashed_message(
        &self,
        message: &[u8],
        signature_hex: &str,
    ) -> Result<bool, WsmIntegrityVerifierError> {
        // Turns out it's kind of hard for the app to know which context to use, so we allow both.
        // Since 'derive' and 'create' key is essentially the same operation, this is totally fine.
        // If we ever add more context labels, we should not just add them to the list, provided the
        // operation in the enclave is meaningfully different.
        let contexts = vec![WsmContext::DeriveKeyV1, WsmContext::CreateKeyV1];

        let signature_bytes = hex::decode(signature_hex)
            .map_err(|_| WsmIntegrityVerifierError::Base16DecodeFailure)?;
        let sig = Signature::from_compact(&signature_bytes)
            .map_err(|_| WsmIntegrityVerifierError::MalformedSignature)?;

        let pk = self
            .0
            .lock()
            .map_err(|_| WsmIntegrityVerifierError::PublicKeyLockFailure)?;

        let secp = Secp256k1::new();

        for ctx in contexts {
            let mut hash_input = Vec::new();
            hash_input.extend_from_slice(GLOBAL_CONTEXT);
            hash_input.extend_from_slice(ctx.to_bytes());
            hash_input.extend_from_slice(message);

            let digest = Message::from_digest(sha256::Hash::hash(&hash_input).to_byte_array());
            if secp.verify_ecdsa(&digest, &sig, &pk).is_ok() {
                return Ok(true);
            }
        }

        Ok(false)
    }

    fn verify_with_context(
        &self,
        context: &[u8],
        data: &[u8],
        signature_hex: &str,
    ) -> Result<bool, WsmIntegrityVerifierError> {
        let signature_bytes = hex::decode(signature_hex)
            .map_err(|_| WsmIntegrityVerifierError::Base16DecodeFailure)?;
        let sig = Signature::from_compact(&signature_bytes)
            .map_err(|_| WsmIntegrityVerifierError::MalformedSignature)?;

        let pk = self
            .0
            .lock()
            .map_err(|_| WsmIntegrityVerifierError::PublicKeyLockFailure)?;

        let secp = Secp256k1::new();

        let mut hash_input = Vec::new();
        hash_input.extend_from_slice(GLOBAL_CONTEXT);
        hash_input.extend_from_slice(context);
        hash_input.extend_from_slice(data);

        let digest = Message::from_digest(sha256::Hash::hash(&hash_input).to_byte_array());
        Ok(secp.verify_ecdsa(&digest, &sig, &pk).is_ok())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_base58_verifier() {
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

    #[test]
    fn test_verify_public_keys() {
        use bitcoin::secp256k1::SecretKey;

        let secp = Secp256k1::new();

        // Generate a signing key (simulating the WSM integrity key)
        let sk = SecretKey::from_slice(&[0x42; 32]).unwrap();
        let integrity_pk = PublicKey::from_secret_key(&secp, &sk);
        let verifier = WsmIntegrityVerifier::new(integrity_pk);

        // 5 distinct compressed public keys (generated from deterministic secret keys)
        let keys: Vec<PublicKey> = (1u8..=5)
            .map(|i| {
                let mut bytes = [0u8; 32];
                bytes[31] = i;
                let sk = SecretKey::from_slice(&bytes).unwrap();
                PublicKey::from_secret_key(&secp, &sk)
            })
            .collect();

        let keys_hex: Vec<String> = keys.iter().map(|k| hex::encode(k.serialize())).collect();

        // Build the expected digest: SHA256("WsmIntegrityV1" + "SignPublicKeysV1" + concat(keys))
        let mut hash_input = Vec::new();
        hash_input.extend_from_slice(b"WsmIntegrityV1");
        hash_input.extend_from_slice(b"SignPublicKeysV1");
        for key in &keys {
            hash_input.extend_from_slice(&key.serialize());
        }
        let digest = Message::from_digest(sha256::Hash::hash(&hash_input).to_byte_array());
        let sig = secp.sign_ecdsa(&digest, &sk);
        let sig_hex = hex::encode(sig.serialize_compact());

        let result = verifier.verify_public_keys(
            keys_hex[0].clone(),
            keys_hex[1].clone(),
            keys_hex[2].clone(),
            keys_hex[3].clone(),
            keys_hex[4].clone(),
            sig_hex,
        );
        assert!(result.is_ok());
        assert!(result.unwrap());
    }

    #[test]
    fn test_verify_public_keys_wrong_signature() {
        use bitcoin::secp256k1::SecretKey;

        let secp = Secp256k1::new();
        let sk = SecretKey::from_slice(&[0x42; 32]).unwrap();
        let integrity_pk = PublicKey::from_secret_key(&secp, &sk);
        let verifier = WsmIntegrityVerifier::new(integrity_pk);

        let keys_hex: Vec<String> = (1u8..=5)
            .map(|i| {
                let mut bytes = [0u8; 32];
                bytes[31] = i;
                let sk = SecretKey::from_slice(&bytes).unwrap();
                hex::encode(PublicKey::from_secret_key(&secp, &sk).serialize())
            })
            .collect();

        // Use a bogus signature (valid format but wrong data)
        let bogus_sig = hex::encode([0x01u8; 64]);

        let result = verifier.verify_public_keys(
            keys_hex[0].clone(),
            keys_hex[1].clone(),
            keys_hex[2].clone(),
            keys_hex[3].clone(),
            keys_hex[4].clone(),
            bogus_sig,
        );
        // Should either return Ok(false) or an error for malformed signature
        match result {
            Ok(valid) => assert!(!valid),
            Err(_) => {} // malformed signature error is also acceptable
        }
    }

    #[test]
    fn test_verify_public_keys_rejects_uncompressed() {
        use bitcoin::secp256k1::SecretKey;

        let secp = Secp256k1::new();
        let sk = SecretKey::from_slice(&[0x42; 32]).unwrap();
        let integrity_pk = PublicKey::from_secret_key(&secp, &sk);
        let verifier = WsmIntegrityVerifier::new(integrity_pk);

        let mut keys_hex: Vec<String> = (1u8..=5)
            .map(|i| {
                let mut bytes = [0u8; 32];
                bytes[31] = i;
                let sk = SecretKey::from_slice(&bytes).unwrap();
                hex::encode(PublicKey::from_secret_key(&secp, &sk).serialize())
            })
            .collect();

        // Replace one key with its uncompressed form (65 bytes)
        let mut bytes = [0u8; 32];
        bytes[31] = 1;
        let sk1 = SecretKey::from_slice(&bytes).unwrap();
        keys_hex[0] = hex::encode(PublicKey::from_secret_key(&secp, &sk1).serialize_uncompressed());

        let result = verifier.verify_public_keys(
            keys_hex[0].clone(),
            keys_hex[1].clone(),
            keys_hex[2].clone(),
            keys_hex[3].clone(),
            keys_hex[4].clone(),
            "aa".repeat(32), // dummy signature
        );
        assert!(result.is_err());
    }

    #[test]
    fn test_hex_verifier() {
        let test_key = "03d388d33c73b382479aa7837859ad49bbee35f15d4202b7b534e1e73ffec1711f";
        let raw_key = hex::decode(test_key).unwrap();
        let pk = PublicKey::from_slice(&raw_key).unwrap();
        let verifier = WsmIntegrityVerifier::new(pk);

        let message = "028363fa1d4dc4ec7cbb60445d020c5579fd337414d4701fdbe5526478976a73a6";
        let signature = "44158b3ebb2cc0ef6ad9b14dcc5e343b70310fe505b306cb2d09bc96f83428ca6ae9a1377cc72bc75c137fa08837f2cee0fb07b7a94f07cc8db1dcc1476ea327";

        let result = verifier.verify_hex_message(message.to_string(), signature.to_string());
        assert!(result.is_ok());
        assert!(result.unwrap());
    }
}
