use anyhow::{bail, Context, Result};
use bdk::bitcoin::secp256k1::{Message, Secp256k1, SecretKey};
use serde::Serialize;
use sha2::{Digest, Sha256};
use wsm_common::bitcoin::secp256k1::ecdsa::Signature;
use wsm_common::bitcoin::secp256k1::PublicKey;
use wsm_common::messages::api::GrantResponse;
use wsm_common::messages::enclave::GrantRequest;

const GRANT_PROTOCOL_VERSION: u8 = 1;
const GRANT_REQUEST_SIG_PREFIX: &[u8] = b"BKGrantReq";
const GRANT_SIG_PREFIX: &[u8] = b"BKGrant";

pub struct GrantCreator {
    pub wik_private_key: SecretKey,
    pub hw_auth_public_key: PublicKey,
}

impl GrantCreator {
    pub fn create_signed_grant(&self, request: GrantRequest) -> Result<GrantResponse> {
        if request.version != GRANT_PROTOCOL_VERSION {
            bail!(
                "Invalid protocol version: {} (expected {})",
                request.version,
                GRANT_PROTOCOL_VERSION
            );
        }

        self.verify_grant_request_signature(&request)?;

        let grant_signature = self.create_grant_signature(request.version, &request)?;

        Ok(GrantResponse {
            version: request.version,
            serialized_request: request.serialize(),
            signature: grant_signature,
        })
    }

    fn create_grant_signature(&self, version: u8, request: &GrantRequest) -> Result<Signature> {
        let mut signing_input = Vec::new();

        signing_input.extend_from_slice(GRANT_SIG_PREFIX);
        signing_input.push(version);
        signing_input.extend_from_slice(&request.serialize());

        let message = create_message(&signing_input)?;
        let secp = Secp256k1::new();

        Ok(secp.sign_ecdsa(&message, &self.wik_private_key))
    }

    fn verify_grant_request_signature(&self, request: &GrantRequest) -> Result<()> {
        let mut data = Vec::new();

        data.extend_from_slice(GRANT_REQUEST_SIG_PREFIX);
        data.push(request.version);
        data.extend_from_slice(request.action.to_string().as_bytes());
        data.extend_from_slice(request.device_id.as_bytes());
        data.extend_from_slice(&request.challenge);

        let message = create_message(&data)?;

        let secp = Secp256k1::new();
        secp.verify_ecdsa(&message, &request.signature, &self.hw_auth_public_key)?;

        Ok(())
    }
}

fn create_message(data: &[u8]) -> Result<Message> {
    let hash = Sha256::digest(data);
    Message::from_slice(&hash).context("Failed to create message from hash")
}

#[cfg(test)]
mod tests {
    use super::*;
    use bdk::bitcoin::secp256k1::rand::rngs::OsRng;
    use bdk::bitcoin::secp256k1::{PublicKey, Secp256k1};

    fn setup_test_keys() -> (SecretKey, PublicKey, SecretKey) {
        let secp = Secp256k1::new();
        let mut rng = OsRng;

        let hw_auth_secret_key = SecretKey::new(&mut rng);
        let hw_auth_public_key = PublicKey::from_secret_key(&secp, &hw_auth_secret_key);

        let wik_private_key = SecretKey::new(&mut rng);

        (wik_private_key, hw_auth_public_key, hw_auth_secret_key)
    }

    fn create_test_request(hw_auth_sk: &SecretKey) -> GrantRequest {
        let version = GRANT_PROTOCOL_VERSION;
        let action = "TRANSACTION_VERIFICATION".to_string();
        let device_id = "test-device-12345".to_string();
        let challenge = "random-challenge-98765".as_bytes().to_vec();

        let mut signable_data = Vec::new();
        signable_data.extend_from_slice(GRANT_REQUEST_SIG_PREFIX);
        signable_data.push(version);
        signable_data.extend_from_slice(action.to_string().as_bytes());
        signable_data.extend_from_slice(device_id.as_bytes());
        signable_data.extend_from_slice(&challenge);

        let secp = Secp256k1::new();
        let hash = Sha256::digest(&signable_data);
        let message = Message::from_slice(&hash).unwrap();
        let signature = secp.sign_ecdsa(&message, hw_auth_sk);

        GrantRequest {
            version,
            action,
            device_id,
            challenge,
            signature,
        }
    }

    #[test]
    fn test_request_signature_verification() {
        // arrange
        let (wik_private_key, hw_auth_public_key, hw_auth_sk) = setup_test_keys();
        let request = create_test_request(&hw_auth_sk);

        let processor = GrantCreator {
            wik_private_key,
            hw_auth_public_key,
        };

        // act
        let result = processor.verify_grant_request_signature(&request);

        // assert
        assert!(result.is_ok());
    }

    #[test]
    fn test_grant_signing() {
        // arrange
        let (wik_private_key, hw_auth_public_key, hw_auth_sk) = setup_test_keys();
        let request = create_test_request(&hw_auth_sk);

        let processor = GrantCreator {
            wik_private_key,
            hw_auth_public_key,
        };

        // act
        let result = processor.create_signed_grant(request.clone());

        // assert
        assert!(result.is_ok());

        let grant = result.unwrap();
        assert_eq!(grant.version, GRANT_PROTOCOL_VERSION);
        assert_eq!(grant.serialized_request, request.serialize());
    }

    #[test]
    fn test_invalid_protocol_version() {
        // arrange
        let (wik_private_key, hw_auth_public_key, hw_auth_sk) = setup_test_keys();
        let mut request = create_test_request(&hw_auth_sk);
        request.version = GRANT_PROTOCOL_VERSION + 1;

        let processor = GrantCreator {
            wik_private_key,
            hw_auth_public_key,
        };

        // act
        let result = processor.create_signed_grant(request);

        // assert
        let error_msg = result.unwrap_err().to_string();
        assert!(error_msg.contains(&format!("expected {}", GRANT_PROTOCOL_VERSION)));
    }
}
