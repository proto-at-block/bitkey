use anyhow::{bail, Context, Result};
use bdk::bitcoin::secp256k1::{Message, Secp256k1, SecretKey};
use sha2::{Digest, Sha256};
use wsm_common::bitcoin::secp256k1::ecdsa::Signature;
use wsm_common::bitcoin::secp256k1::PublicKey;
use wsm_common::messages::api::GrantResponse;
use wsm_common::messages::enclave::GrantRequest;
use wsm_grant::fp_reset::verify_grant_request_signature;

const GRANT_PROTOCOL_VERSION: u8 = 1;
const GRANT_SIG_PREFIX: &[u8] = b"BKGrant";
const GRANT_APP_SIG_PREFIX: &[u8] = b"BKAppBind";

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

        verify_grant_request_signature(
            &request.serialize(false),
            &request.hw_signature,
            &self.hw_auth_public_key,
        )?;

        let wsm_signature = self.create_wsm_signature(request.version, &request)?;

        Ok(GrantResponse {
            version: request.version,
            serialized_request: request.serialize(true),
            app_signature: request.app_signature,
            wsm_signature,
        })
    }

    fn create_wsm_signature(&self, version: u8, request: &GrantRequest) -> Result<Signature> {
        let mut signing_input = Vec::new();

        // WSM signature is over: "BKGrant" + version + serialized_request + app_signature
        signing_input.extend_from_slice(GRANT_SIG_PREFIX); // "BKGrant"
        signing_input.push(version); // version (1 byte)
        signing_input.extend_from_slice(&request.serialize(true)); // serialized_request (90 bytes)
        signing_input.extend_from_slice(&request.app_signature.serialize_compact()); // app_signature (64 bytes)

        let hash = Sha256::digest(&signing_input);
        let message = Message::from_slice(&hash).context("Failed to create message from hash")?;
        let secp = Secp256k1::new();

        Ok(secp.sign_ecdsa(&message, &self.wik_private_key))
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use bdk::bitcoin::secp256k1::{PublicKey, Secp256k1};
    use rand::{rngs::StdRng, SeedableRng};
    use std::str::FromStr;
    use wsm_grant::fp_reset::GRANT_REQUEST_SIG_PREFIX;

    const TEST_RNG_SEED: [u8; 32] = [0x42; 32];

    fn setup_test_keys() -> (SecretKey, PublicKey, SecretKey, PublicKey, SecretKey) {
        let secp = Secp256k1::new();
        let mut rng = StdRng::from_seed(TEST_RNG_SEED);

        let hw_auth_secret_key = SecretKey::new(&mut rng);
        let hw_auth_public_key = PublicKey::from_secret_key(&secp, &hw_auth_secret_key);

        let app_auth_secret_key = SecretKey::new(&mut rng);
        let app_auth_public_key = PublicKey::from_secret_key(&secp, &app_auth_secret_key);

        let wik_private_key = SecretKey::new(&mut rng);

        (
            wik_private_key,
            hw_auth_public_key,
            hw_auth_secret_key,
            app_auth_public_key,
            app_auth_secret_key,
        )
    }

    fn create_test_request(hw_auth_sk: &SecretKey, app_auth_sk: &SecretKey) -> GrantRequest {
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

        let mut app_signature_data = Vec::new();
        app_signature_data.extend_from_slice(GRANT_APP_SIG_PREFIX);
        app_signature_data.push(version);
        app_signature_data.extend_from_slice(&device_id);
        app_signature_data.extend_from_slice(&challenge);
        app_signature_data.push(action);

        let secp = Secp256k1::new();
        let hw_hash = Sha256::digest(&signable_data);
        let hw_message = Message::from_slice(&hw_hash).unwrap();
        let hw_signature = secp.sign_ecdsa(&hw_message, hw_auth_sk);

        let app_hash = Sha256::digest(&app_signature_data);
        let app_message = Message::from_slice(&app_hash).unwrap();
        let app_signature = secp.sign_ecdsa(&app_message, app_auth_sk);

        GrantRequest {
            version,
            action,
            device_id,
            challenge,
            hw_signature,
            app_signature,
        }
    }

    #[test]
    fn test_request_signature_verification() {
        // arrange
        let (wik_private_key, hw_auth_public_key, hw_auth_sk, app_auth_public_key, app_auth_sk) =
            setup_test_keys();
        let request = create_test_request(&hw_auth_sk, &app_auth_sk);

        let processor = GrantCreator {
            wik_private_key,
            hw_auth_public_key,
        };

        // act
        let hw_result = verify_grant_request_signature(
            &request.serialize(false),
            &request.hw_signature,
            &hw_auth_public_key,
        );

        // assert
        assert!(hw_result.is_ok());
    }

    #[test]
    fn test_grant_signing() {
        // arrange
        let (wik_private_key, hw_auth_public_key, hw_auth_sk, app_auth_public_key, app_auth_sk) =
            setup_test_keys();
        let request = create_test_request(&hw_auth_sk, &app_auth_sk);

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
        assert_eq!(grant.app_signature, request.app_signature);
    }

    #[test]
    fn test_invalid_protocol_version() {
        // arrange
        let (wik_private_key, hw_auth_public_key, hw_auth_sk, _, app_auth_sk) = setup_test_keys();
        let mut request = create_test_request(&hw_auth_sk, &app_auth_sk);
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

        let (wik_private_key, _, _, _, app_auth_sk) = setup_test_keys();

        let mut app_signature_data = Vec::new();
        app_signature_data.extend_from_slice(GRANT_APP_SIG_PREFIX);
        app_signature_data.push(1);
        app_signature_data.extend_from_slice("38398ffffed081b6".as_bytes());
        app_signature_data.extend_from_slice("ba98c16cee90ad43cf97e0a33084edc3".as_bytes());
        app_signature_data.push(1);

        let hw_auth_public_key = PublicKey::from_slice(
            &hex::decode("02f842a7e390c222637190950d797f3677d52601fdb8bb2084da805a67cc4a30f7")
                .unwrap(),
        )
        .unwrap();

        let secp = Secp256k1::new();
        let hash = Sha256::digest(&app_signature_data);
        let message = Message::from_slice(&hash).unwrap();
        let app_signature = secp.sign_ecdsa(&message, &app_auth_sk);

        let request = GrantRequest {
            version: GRANT_PROTOCOL_VERSION,
            action: 1,
            device_id: hex::decode("38398ffffed081b6").unwrap(),
            challenge: hex::decode("ba98c16cee90ad43cf97e0a33084edc3").unwrap(),
            hw_signature: Signature::from_compact(&hex::decode("13d6b20cd5d741dff6ecc5af5cd9bbc5168b7f70af01dced8e3b3c761e1d710f021699b84231b38ead81ee22b9bb908a26b2c0652d9bd68beefb7e4532ed5879").unwrap()).unwrap(),
            app_signature,
        };

        let processor = GrantCreator {
            wik_private_key,
            hw_auth_public_key,
        };

        let result = processor.create_signed_grant(request.clone());

        assert!(result.is_ok());

        let grant = result.unwrap();
        let wsm_signature = Signature::from_str("30440220763ecba1c90a7e4977a2dc718a7d12ff2f60ae295c53613d1615552efec43be6022019f0dc799faf794ed7d2aea5ebc536bd65f0bb42368ba938039cf96d3e977653").unwrap();
        assert_eq!(grant.version, GRANT_PROTOCOL_VERSION);
        assert_eq!(grant.serialized_request, request.serialize(true));
        assert_eq!(grant.app_signature, request.app_signature);
        assert_eq!(grant.wsm_signature, wsm_signature);
    }
}
