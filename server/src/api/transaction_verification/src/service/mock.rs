use async_trait::async_trait;
use wsm_common::bitcoin::hashes::{sha256, Hash, HashEngine};
use wsm_common::bitcoin::secp256k1::{Message, PublicKey, Secp256k1, SecretKey};
use wsm_common::messages::api::TransactionVerificationGrant;
use wsm_rust_client::{Error, GrantService};

/// Mock implementation of GrantService for testing
pub struct MockGrantService {
    /// Secret key used for generating deterministic signatures
    secret_key: SecretKey,
}

impl MockGrantService {
    pub fn new() -> Self {
        // Use a deterministic secret key for testing
        let secret_key_bytes = [1u8; 32];
        let secret_key = SecretKey::from_slice(&secret_key_bytes).expect("valid secret key");
        Self { secret_key }
    }
}

impl Default for MockGrantService {
    fn default() -> Self {
        Self::new()
    }
}

#[async_trait]
impl GrantService for MockGrantService {
    async fn approve_psbt(
        &self,
        psbt: &str,
        hw_auth_public_key: PublicKey,
    ) -> Result<TransactionVerificationGrant, Error> {
        // Create a deterministic signature based on the PSBT and public key
        let mut hasher = sha256::HashEngine::default();
        hasher.input(b"TVA1"); // Transaction Verification Approval version 1
        hasher.input(&hw_auth_public_key.serialize());
        hasher.input(psbt.as_bytes());
        let commitment = sha256::Hash::from_engine(hasher);

        // Sign the hash
        let secp = Secp256k1::new();
        let message = Message::from_slice(&commitment.to_byte_array()).expect("valid message");
        let signature = secp.sign_ecdsa(&message, &self.secret_key);

        Ok(TransactionVerificationGrant {
            version: 0,
            hw_auth_public_key,
            commitment: commitment.to_byte_array().to_vec(),
            reverse_hash_chain: vec![commitment.to_byte_array().to_vec()],
            signature,
        })
    }
}
