use std::str::FromStr;

use http::StatusCode;

use account::entities::{FullAccountAuthKeysPayload, SpendingKeysetRequest};
use authn_authz::routes::{
    AuthRequestKey, AuthenticateWithHardwareRequest, AuthenticationRequest,
    ChallengeResponseParameters, GetTokensRequest,
};
use bdk_utils::bdk::bitcoin::hashes::sha256;
use bdk_utils::bdk::bitcoin::secp256k1::{Message, Secp256k1};
use bdk_utils::bdk::bitcoin::Network;
use bdk_utils::bdk::miniscript::DescriptorPublicKey;
use onboarding::routes::CreateAccountRequest;

use crate::tests;
use crate::tests::gen_services;
use crate::tests::lib::{create_keypair, create_pubkey};
use crate::tests::requests::axum::TestClient;

struct AuthWithHwAuthKeyTestVector {
    spending_app_xpub: DescriptorPublicKey,
    spending_hw_xpub: DescriptorPublicKey,
    network: Network,
    expected_create_status: StatusCode,
    expected_auth_status: StatusCode,
    expected_contains_auth_challenge: bool,
    should_refresh_auth: bool,
}

async fn auth_with_hw_test(vector: AuthWithHwAuthKeyTestVector) {
    let bootstrap = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let (hardware_privkey, hardware_pubkey) = create_keypair();
    // First, make an account
    let request = CreateAccountRequest::Full {
        auth: FullAccountAuthKeysPayload {
            app: create_pubkey(),
            hardware: hardware_pubkey,
            recovery: None,
        },
        spending: SpendingKeysetRequest {
            network: vector.network,
            app: vector.spending_app_xpub,
            hardware: vector.spending_hw_xpub,
        },
        is_test_account: true,
    };
    let actual_response = client.create_account(&request).await;
    assert_eq!(
        actual_response.status_code, vector.expected_create_status,
        "{}",
        actual_response.body_string
    );

    // now try to initiate auth with the hw key
    let request = AuthenticateWithHardwareRequest {
        hw_auth_pubkey: hardware_pubkey,
    };
    let actual_response = client.authenticate_with_hardware(&request).await;
    assert_eq!(
        actual_response.status_code, vector.expected_auth_status,
        "{}",
        actual_response.body_string
    );
    assert!(actual_response.body.is_some());
    if vector.expected_contains_auth_challenge {
        assert_eq!(
            hex::decode(actual_response.body.unwrap().challenge)
                .unwrap()
                .len(),
            64 // our dummy challenge is 64 bytes long
        );
    }

    // Now do the full authentication with the hw key
    let request = AuthenticationRequest {
        auth_request_key: AuthRequestKey::HwPubkey(hardware_pubkey),
    };
    let actual_response = client.authenticate(&request).await;
    assert_eq!(
        actual_response.status_code, vector.expected_auth_status,
        "{}",
        actual_response.body_string
    );
    if vector.expected_auth_status == StatusCode::OK {
        let auth_resp = actual_response.body.unwrap();
        let challenge = auth_resp.challenge;
        let secp = Secp256k1::new();
        let message = Message::from_hashed_data::<sha256::Hash>(challenge.as_ref());
        let signature = secp.sign_ecdsa(&message, &hardware_privkey);
        let request = GetTokensRequest {
            challenge: Some(ChallengeResponseParameters {
                username: auth_resp.username,
                challenge_response: signature.to_string(),
                session: auth_resp.session,
            }),
            refresh_token: None,
        };
        let actual_response = client.get_tokens(&request).await;
        assert_eq!(
            actual_response.status_code, vector.expected_auth_status,
            "{}",
            actual_response.body_string
        );

        if vector.should_refresh_auth {
            let refresh_token = actual_response.body.unwrap().refresh_token;
            let request = GetTokensRequest {
                challenge: None,
                refresh_token: Some(refresh_token),
            };
            let actual_response = client.get_tokens(&request).await;
            assert_eq!(
                actual_response.status_code, vector.expected_auth_status,
                "{}",
                actual_response.body_string
            );
        }
    }
}

tests! {
    runner = auth_with_hw_test,
    test_authenticating_with_hw_key: AuthWithHwAuthKeyTestVector {
        spending_app_xpub: DescriptorPublicKey::from_str("[74ce1142/84'/1'/0']tpubD6NzVbkrYhZ4XFo7hggmFF9qDqwrR9aqZv6j2Sgp1N5aVyxyMXxQG14grtRa3ob8ddZqxbd2hbPU7dEXvPRDRuQJ3NsMaGDaZXkLEewdthy/0/*").unwrap(),
        spending_hw_xpub: DescriptorPublicKey::from_str("[9e61ede9/84'/1'/0']tpubD6NzVbkrYhZ4Xwyrc51ZUDmxHYdTBpmTqTwSB6vr93T3Rt72nPzx2kjTV8VeWJW741HvVGvRyPSHZBgA5AEGD8Eib3sMwazMEuaQf1ioGBo/0/*").unwrap(),
        network: Network::Signet,
        expected_create_status: StatusCode::OK,
        expected_auth_status: StatusCode::OK,
        expected_contains_auth_challenge: true,
        should_refresh_auth: false,
    },
    test_authenticating_with_hw_key_and_refresh_token: AuthWithHwAuthKeyTestVector {
        spending_app_xpub: DescriptorPublicKey::from_str("[74ce1142/84'/1'/0']tpubD6NzVbkrYhZ4XFo7hggmFF9qDqwrR9aqZv6j2Sgp1N5aVyxyMXxQG14grtRa3ob8ddZqxbd2hbPU7dEXvPRDRuQJ3NsMaGDaZXkLEewdthy/0/*").unwrap(),
        spending_hw_xpub: DescriptorPublicKey::from_str("[9e61ede9/84'/1'/0']tpubD6NzVbkrYhZ4Xwyrc51ZUDmxHYdTBpmTqTwSB6vr93T3Rt72nPzx2kjTV8VeWJW741HvVGvRyPSHZBgA5AEGD8Eib3sMwazMEuaQf1ioGBo/0/*").unwrap(),
        network: Network::Signet,
        expected_create_status: StatusCode::OK,
        expected_auth_status: StatusCode::OK,
        expected_contains_auth_challenge: true,
        should_refresh_auth: true,
    },
}

struct AuthWithRecoveryAuthKeyTestVector {
    spending_app_xpub: DescriptorPublicKey,
    spending_hw_xpub: DescriptorPublicKey,
    network: Network,
    expected_create_status: StatusCode,
    expected_auth_status: StatusCode,
    expected_contains_auth_challenge: bool,
    should_refresh_auth: bool,
}

async fn auth_with_recovery_authkey_test(vector: AuthWithRecoveryAuthKeyTestVector) {
    let bootstrap = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let (recovery_privkey, recovery_pubkey) = create_keypair();
    // First, make an account
    let request = CreateAccountRequest::Full {
        auth: FullAccountAuthKeysPayload {
            app: create_pubkey(),
            hardware: create_pubkey(),
            recovery: Some(recovery_pubkey),
        },
        spending: SpendingKeysetRequest {
            network: vector.network,
            app: vector.spending_app_xpub,
            hardware: vector.spending_hw_xpub,
        },
        is_test_account: true,
    };
    let actual_response = client.create_account(&request).await;
    assert_eq!(
        actual_response.status_code, vector.expected_create_status,
        "{}",
        actual_response.body_string
    );

    // now try to initiate auth with the hw key
    let request = AuthenticationRequest {
        auth_request_key: AuthRequestKey::RecoveryPubkey(recovery_pubkey),
    };
    let actual_response = client.authenticate(&request).await;
    assert_eq!(
        actual_response.status_code, vector.expected_auth_status,
        "{}",
        actual_response.body_string
    );
    let response_body = actual_response.body;
    assert!(response_body.as_ref().is_some());
    if vector.expected_contains_auth_challenge {
        assert_eq!(
            hex::decode(response_body.as_ref().unwrap().challenge.clone())
                .unwrap()
                .len(),
            64 // our dummy challenge is 64 bytes long
        );
    }

    if vector.expected_auth_status == StatusCode::OK {
        let auth_resp = response_body.unwrap();
        let challenge = auth_resp.challenge;
        let secp = Secp256k1::new();
        let message = Message::from_hashed_data::<sha256::Hash>(challenge.as_ref());
        let signature = secp.sign_ecdsa(&message, &recovery_privkey);

        let request = GetTokensRequest {
            challenge: Some(ChallengeResponseParameters {
                username: auth_resp.username,
                challenge_response: signature.to_string(),
                session: auth_resp.session,
            }),
            refresh_token: None,
        };
        let actual_response = client.get_tokens(&request).await;
        assert_eq!(
            actual_response.status_code, vector.expected_auth_status,
            "{}",
            actual_response.body_string
        );

        if vector.should_refresh_auth {
            let refresh_token = actual_response.body.unwrap().refresh_token;
            let request = GetTokensRequest {
                challenge: None,
                refresh_token: Some(refresh_token),
            };
            let actual_response = client.get_tokens(&request).await;
            assert_eq!(
                actual_response.status_code, vector.expected_auth_status,
                "{}",
                actual_response.body_string
            );
        }
    }
}

tests! {
    runner = auth_with_recovery_authkey_test,
    test_authenticating_with_recovery_auth_key: AuthWithRecoveryAuthKeyTestVector {
        spending_app_xpub: DescriptorPublicKey::from_str("[74ce1142/84'/1'/0']tpubD6NzVbkrYhZ4XFo7hggmFF9qDqwrR9aqZv6j2Sgp1N5aVyxyMXxQG14grtRa3ob8ddZqxbd2hbPU7dEXvPRDRuQJ3NsMaGDaZXkLEewdthy/0/*").unwrap(),
        spending_hw_xpub: DescriptorPublicKey::from_str("[9e61ede9/84'/1'/0']tpubD6NzVbkrYhZ4Xwyrc51ZUDmxHYdTBpmTqTwSB6vr93T3Rt72nPzx2kjTV8VeWJW741HvVGvRyPSHZBgA5AEGD8Eib3sMwazMEuaQf1ioGBo/0/*").unwrap(),
        network: Network::Signet,
        expected_create_status: StatusCode::OK,
        expected_auth_status: StatusCode::OK,
        expected_contains_auth_challenge: true,
        should_refresh_auth: false,
    },
    test_authenticating_with_recovery_auth_key_and_refresh_token: AuthWithRecoveryAuthKeyTestVector {
        spending_app_xpub: DescriptorPublicKey::from_str("[74ce1142/84'/1'/0']tpubD6NzVbkrYhZ4XFo7hggmFF9qDqwrR9aqZv6j2Sgp1N5aVyxyMXxQG14grtRa3ob8ddZqxbd2hbPU7dEXvPRDRuQJ3NsMaGDaZXkLEewdthy/0/*").unwrap(),
        spending_hw_xpub: DescriptorPublicKey::from_str("[9e61ede9/84'/1'/0']tpubD6NzVbkrYhZ4Xwyrc51ZUDmxHYdTBpmTqTwSB6vr93T3Rt72nPzx2kjTV8VeWJW741HvVGvRyPSHZBgA5AEGD8Eib3sMwazMEuaQf1ioGBo/0/*").unwrap(),
        network: Network::Signet,
        expected_create_status: StatusCode::OK,
        expected_auth_status: StatusCode::OK,
        expected_contains_auth_challenge: true,
        should_refresh_auth: true,
    },
}
