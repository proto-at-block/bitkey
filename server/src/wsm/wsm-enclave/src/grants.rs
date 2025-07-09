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
            serialized_request: request.serialize(true),
            signature: grant_signature,
        })
    }

    fn create_grant_signature(&self, version: u8, request: &GrantRequest) -> Result<Signature> {
        let mut signing_input = Vec::new();

        signing_input.extend_from_slice(GRANT_SIG_PREFIX);
        signing_input.push(version);
        signing_input.extend_from_slice(&request.serialize(true));

        let message = create_message(&signing_input)?;
        let secp = Secp256k1::new();

        Ok(secp.sign_ecdsa(&message, &self.wik_private_key))
    }

    fn verify_grant_request_signature(&self, request: &GrantRequest) -> Result<()> {
        let mut data = Vec::new();

        data.extend_from_slice(GRANT_REQUEST_SIG_PREFIX);
        data.extend_from_slice(&request.serialize(false));

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
        let action = 1;
        let device_id = "test-device-12345".as_bytes().to_vec();
        let challenge = "random-challenge-98765".as_bytes().to_vec();

        let mut signable_data = Vec::new();
        signable_data.extend_from_slice(GRANT_REQUEST_SIG_PREFIX);
        signable_data.push(version);
        signable_data.extend_from_slice(&device_id);
        signable_data.extend_from_slice(&challenge);
        signable_data.push(action);

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
        assert_eq!(grant.serialized_request, request.serialize(true));
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

    #[test]
    fn test_request_from_real_firmware() {
        // GrantRequest(version=1,
        //              device_id=38398ffffed081b6,
        //              challenge=ba98c16cee90ad43cf97e0a33084edc3,
        //              action=1,
        //              signature=13d6b20cd5d741dff6ecc5af5cd9bbc5168b7f70af01dced8e3b3c761e1d710f021699b84231b38ead81ee22b9bb908a26b2c0652d9bd68beefb7e4532ed5879)
        // Hw auth key:
        // 02f842a7e390c222637190950d797f3677d52601fdb8bb2084da805a67cc4a30f7

        let (wik_private_key, _, _) = setup_test_keys();

        let hw_auth_public_key = PublicKey::from_slice(
            &hex::decode("02f842a7e390c222637190950d797f3677d52601fdb8bb2084da805a67cc4a30f7")
                .unwrap(),
        )
        .unwrap();

        let request = GrantRequest {
            version: GRANT_PROTOCOL_VERSION,
            action: 1,
            device_id: hex::decode("38398ffffed081b6").unwrap(),
            challenge: hex::decode("ba98c16cee90ad43cf97e0a33084edc3").unwrap(),
            signature: Signature::from_compact(&hex::decode("13d6b20cd5d741dff6ecc5af5cd9bbc5168b7f70af01dced8e3b3c761e1d710f021699b84231b38ead81ee22b9bb908a26b2c0652d9bd68beefb7e4532ed5879").unwrap()).unwrap(),
        };

        let processor = GrantCreator {
            wik_private_key,
            hw_auth_public_key,
        };

        let result = processor.create_signed_grant(request.clone());

        assert!(result.is_ok());

        let grant = result.unwrap();
        assert_eq!(grant.version, GRANT_PROTOCOL_VERSION);
        assert_eq!(grant.serialized_request, request.serialize(true));
    }
}
