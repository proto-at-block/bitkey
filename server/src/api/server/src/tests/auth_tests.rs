use std::collections::HashMap;
use std::str::FromStr;

use authn_authz::routes::{
    AuthRequestKey, AuthenticateWithHardwareRequest, AuthenticationRequest,
    ChallengeResponseParameters, GetTokensRequest,
};
use bdk_utils::bdk::bitcoin::hashes::sha256;
use bdk_utils::bdk::bitcoin::secp256k1::{Message, Secp256k1};
use bdk_utils::bdk::bitcoin::Network;
use bdk_utils::bdk::miniscript::DescriptorPublicKey;
use http::StatusCode;
use onboarding::routes::CreateAccountRequest;
use rstest::rstest;
use types::account::entities::{FullAccountAuthKeysPayload, SpendingKeysetRequest};
use types::account::identifiers::AccountId;

use crate::tests::requests::axum::TestClient;
use crate::tests::{gen_services_with_overrides, lib::create_new_authkeys, GenServiceOverrides};

#[rstest]
#[case::basic(
    DescriptorPublicKey::from_str("[74ce1142/84'/1'/0']tpubD6NzVbkrYhZ4XFo7hggmFF9qDqwrR9aqZv6j2Sgp1N5aVyxyMXxQG14grtRa3ob8ddZqxbd2hbPU7dEXvPRDRuQJ3NsMaGDaZXkLEewdthy/0/*").unwrap(),
    DescriptorPublicKey::from_str("[9e61ede9/84'/1'/0']tpubD6NzVbkrYhZ4Xwyrc51ZUDmxHYdTBpmTqTwSB6vr93T3Rt72nPzx2kjTV8VeWJW741HvVGvRyPSHZBgA5AEGD8Eib3sMwazMEuaQf1ioGBo/0/*").unwrap(),
    Network::Signet,
    false,
    StatusCode::OK,
    StatusCode::OK,
    true,
    false,
    false
)]
#[case::basic_with_debug(
    DescriptorPublicKey::from_str("[74ce1142/84'/1'/0']tpubD6NzVbkrYhZ4XFo7hggmFF9qDqwrR9aqZv6j2Sgp1N5aVyxyMXxQG14grtRa3ob8ddZqxbd2hbPU7dEXvPRDRuQJ3NsMaGDaZXkLEewdthy/0/*").unwrap(),
    DescriptorPublicKey::from_str("[9e61ede9/84'/1'/0']tpubD6NzVbkrYhZ4Xwyrc51ZUDmxHYdTBpmTqTwSB6vr93T3Rt72nPzx2kjTV8VeWJW741HvVGvRyPSHZBgA5AEGD8Eib3sMwazMEuaQf1ioGBo/0/*").unwrap(),
    Network::Signet,
    true,
    StatusCode::OK,
    StatusCode::OK,
    true,
    false,
    false
)]
#[case::account_id_username(
    DescriptorPublicKey::from_str("[74ce1142/84'/1'/0']tpubD6NzVbkrYhZ4XFo7hggmFF9qDqwrR9aqZv6j2Sgp1N5aVyxyMXxQG14grtRa3ob8ddZqxbd2hbPU7dEXvPRDRuQJ3NsMaGDaZXkLEewdthy/0/*").unwrap(),
    DescriptorPublicKey::from_str("[9e61ede9/84'/1'/0']tpubD6NzVbkrYhZ4Xwyrc51ZUDmxHYdTBpmTqTwSB6vr93T3Rt72nPzx2kjTV8VeWJW741HvVGvRyPSHZBgA5AEGD8Eib3sMwazMEuaQf1ioGBo/0/*").unwrap(),
    Network::Signet,
    false,
    StatusCode::OK,
    StatusCode::OK,
    true,
    false,
    true
)]
#[case::account_id_username_debug(
    DescriptorPublicKey::from_str("[74ce1142/84'/1'/0']tpubD6NzVbkrYhZ4XFo7hggmFF9qDqwrR9aqZv6j2Sgp1N5aVyxyMXxQG14grtRa3ob8ddZqxbd2hbPU7dEXvPRDRuQJ3NsMaGDaZXkLEewdthy/0/*").unwrap(),
    DescriptorPublicKey::from_str("[9e61ede9/84'/1'/0']tpubD6NzVbkrYhZ4Xwyrc51ZUDmxHYdTBpmTqTwSB6vr93T3Rt72nPzx2kjTV8VeWJW741HvVGvRyPSHZBgA5AEGD8Eib3sMwazMEuaQf1ioGBo/0/*").unwrap(),
    Network::Signet,
    true,
    StatusCode::OK,
    StatusCode::OK,
    true,
    false,
    true
)]
#[case::refresh_token(
    DescriptorPublicKey::from_str("[74ce1142/84'/1'/0']tpubD6NzVbkrYhZ4XFo7hggmFF9qDqwrR9aqZv6j2Sgp1N5aVyxyMXxQG14grtRa3ob8ddZqxbd2hbPU7dEXvPRDRuQJ3NsMaGDaZXkLEewdthy/0/*").unwrap(),
    DescriptorPublicKey::from_str("[9e61ede9/84'/1'/0']tpubD6NzVbkrYhZ4Xwyrc51ZUDmxHYdTBpmTqTwSB6vr93T3Rt72nPzx2kjTV8VeWJW741HvVGvRyPSHZBgA5AEGD8Eib3sMwazMEuaQf1ioGBo/0/*").unwrap(),
    Network::Signet,
    false,
    StatusCode::OK,
    StatusCode::OK,
    true,
    true,
    false
)]
#[case::refresh_token_debug(
    DescriptorPublicKey::from_str("[74ce1142/84'/1'/0']tpubD6NzVbkrYhZ4XFo7hggmFF9qDqwrR9aqZv6j2Sgp1N5aVyxyMXxQG14grtRa3ob8ddZqxbd2hbPU7dEXvPRDRuQJ3NsMaGDaZXkLEewdthy/0/*").unwrap(),
    DescriptorPublicKey::from_str("[9e61ede9/84'/1'/0']tpubD6NzVbkrYhZ4Xwyrc51ZUDmxHYdTBpmTqTwSB6vr93T3Rt72nPzx2kjTV8VeWJW741HvVGvRyPSHZBgA5AEGD8Eib3sMwazMEuaQf1ioGBo/0/*").unwrap(),
    Network::Signet,
    true,
    StatusCode::OK,
    StatusCode::OK,
    true,
    true,
    false
)]
#[tokio::test]
async fn auth_with_hw_test(
    #[case] spending_app_xpub: DescriptorPublicKey,
    #[case] spending_hw_xpub: DescriptorPublicKey,
    #[case] network: Network,
    #[case] is_debug_flag_enabled: bool,
    #[case] expected_create_status: StatusCode,
    #[case] expected_auth_status: StatusCode,
    #[case] expected_contains_auth_challenge: bool,
    #[case] should_refresh_auth: bool,
    #[case] should_use_account_id_as_username: bool,
) {
    let feature_flag_override = if is_debug_flag_enabled {
        vec![(
            "f8e-debug-authentication-calls".to_owned(),
            "true".to_owned(),
        )]
    } else {
        vec![]
    }
    .into_iter()
    .collect::<HashMap<String, String>>();
    let overrides = GenServiceOverrides::new().feature_flags(feature_flag_override);
    let (mut context, bootstrap) = gen_services_with_overrides(overrides).await;
    let client = TestClient::new(bootstrap.router).await;

    let keys = create_new_authkeys(&mut context);
    // First, make an account
    let request = CreateAccountRequest::Full {
        auth: FullAccountAuthKeysPayload {
            app: keys.app.public_key,
            hardware: keys.hw.public_key,
            recovery: Some(keys.recovery.public_key),
        },
        spending: SpendingKeysetRequest {
            network,
            app: spending_app_xpub,
            hardware: spending_hw_xpub,
        },
        is_test_account: true,
    };
    let actual_response = client.create_account(&mut context, &request).await;
    assert_eq!(
        actual_response.status_code, expected_create_status,
        "{}",
        actual_response.body_string
    );
    let account_id = if expected_create_status == StatusCode::OK {
        actual_response.body.unwrap().account_id
    } else {
        AccountId::gen().unwrap()
    };

    // now try to initiate auth with the hw key
    let request = AuthenticateWithHardwareRequest {
        hw_auth_pubkey: keys.hw.public_key,
    };
    let actual_response = client.authenticate_with_hardware(&request).await;
    assert_eq!(
        actual_response.status_code, expected_auth_status,
        "{}",
        actual_response.body_string
    );
    assert!(actual_response.body.is_some());
    if expected_contains_auth_challenge {
        assert_eq!(
            hex::decode(actual_response.body.unwrap().challenge)
                .unwrap()
                .len(),
            64 // our dummy challenge is 64 bytes long
        );
    }

    // Now do the full authentication with the hw key
    let request = AuthenticationRequest {
        auth_request_key: AuthRequestKey::HwPubkey(keys.hw.public_key),
    };
    let actual_response = client.authenticate(&request).await;
    assert_eq!(
        actual_response.status_code, expected_auth_status,
        "{}",
        actual_response.body_string
    );
    if expected_auth_status == StatusCode::OK {
        let auth_resp = actual_response.body.unwrap();
        let challenge = auth_resp.challenge;
        let secp = Secp256k1::new();
        let message = Message::from_hashed_data::<sha256::Hash>(challenge.as_ref());
        let signature = secp.sign_ecdsa(&message, &keys.hw.secret_key);
        let request = GetTokensRequest {
            challenge: Some(ChallengeResponseParameters {
                username: if should_use_account_id_as_username {
                    serde_json::from_str(&format!("\"{}\"", account_id)).unwrap()
                } else {
                    auth_resp.username
                },
                challenge_response: signature.to_string(),
                session: auth_resp.session,
            }),
            refresh_token: None,
        };
        let actual_response = client.get_tokens(&request).await;
        assert_eq!(
            actual_response.status_code, expected_auth_status,
            "{}",
            actual_response.body_string
        );

        if should_refresh_auth {
            let refresh_token = actual_response.body.unwrap().refresh_token;
            let request = GetTokensRequest {
                challenge: None,
                refresh_token: Some(refresh_token),
            };
            let actual_response = client.get_tokens(&request).await;
            assert_eq!(
                actual_response.status_code, expected_auth_status,
                "{}",
                actual_response.body_string
            );
        }
    }
}

#[rstest]
#[case::basic_recovery(
    DescriptorPublicKey::from_str("[74ce1142/84'/1'/0']tpubD6NzVbkrYhZ4XFo7hggmFF9qDqwrR9aqZv6j2Sgp1N5aVyxyMXxQG14grtRa3ob8ddZqxbd2hbPU7dEXvPRDRuQJ3NsMaGDaZXkLEewdthy/0/*").unwrap(),
    DescriptorPublicKey::from_str("[9e61ede9/84'/1'/0']tpubD6NzVbkrYhZ4Xwyrc51ZUDmxHYdTBpmTqTwSB6vr93T3Rt72nPzx2kjTV8VeWJW741HvVGvRyPSHZBgA5AEGD8Eib3sMwazMEuaQf1ioGBo/0/*").unwrap(),
    Network::Signet,
    false,
    StatusCode::OK,
    StatusCode::OK,
    true,
    false,
)]
#[case::basic_recovery_debug(
    DescriptorPublicKey::from_str("[74ce1142/84'/1'/0']tpubD6NzVbkrYhZ4XFo7hggmFF9qDqwrR9aqZv6j2Sgp1N5aVyxyMXxQG14grtRa3ob8ddZqxbd2hbPU7dEXvPRDRuQJ3NsMaGDaZXkLEewdthy/0/*").unwrap(),
    DescriptorPublicKey::from_str("[9e61ede9/84'/1'/0']tpubD6NzVbkrYhZ4Xwyrc51ZUDmxHYdTBpmTqTwSB6vr93T3Rt72nPzx2kjTV8VeWJW741HvVGvRyPSHZBgA5AEGD8Eib3sMwazMEuaQf1ioGBo/0/*").unwrap(),
    Network::Signet,
    true,
    StatusCode::OK,
    StatusCode::OK,
    true,
    false,
)]
#[case::recovery_refresh_token(
    DescriptorPublicKey::from_str("[74ce1142/84'/1'/0']tpubD6NzVbkrYhZ4XFo7hggmFF9qDqwrR9aqZv6j2Sgp1N5aVyxyMXxQG14grtRa3ob8ddZqxbd2hbPU7dEXvPRDRuQJ3NsMaGDaZXkLEewdthy/0/*").unwrap(),
    DescriptorPublicKey::from_str("[9e61ede9/84'/1'/0']tpubD6NzVbkrYhZ4Xwyrc51ZUDmxHYdTBpmTqTwSB6vr93T3Rt72nPzx2kjTV8VeWJW741HvVGvRyPSHZBgA5AEGD8Eib3sMwazMEuaQf1ioGBo/0/*").unwrap(),
    Network::Signet,
    false,
    StatusCode::OK,
    StatusCode::OK,
    true,
    true,
)]
#[case::recovery_refresh_token_debug(
    DescriptorPublicKey::from_str("[74ce1142/84'/1'/0']tpubD6NzVbkrYhZ4XFo7hggmFF9qDqwrR9aqZv6j2Sgp1N5aVyxyMXxQG14grtRa3ob8ddZqxbd2hbPU7dEXvPRDRuQJ3NsMaGDaZXkLEewdthy/0/*").unwrap(),
    DescriptorPublicKey::from_str("[9e61ede9/84'/1'/0']tpubD6NzVbkrYhZ4Xwyrc51ZUDmxHYdTBpmTqTwSB6vr93T3Rt72nPzx2kjTV8VeWJW741HvVGvRyPSHZBgA5AEGD8Eib3sMwazMEuaQf1ioGBo/0/*").unwrap(),
    Network::Signet,
    true,
    StatusCode::OK,
    StatusCode::OK,
    true,
    true,
)]
#[tokio::test]
async fn auth_with_recovery_authkey_test(
    #[case] spending_app_xpub: DescriptorPublicKey,
    #[case] spending_hw_xpub: DescriptorPublicKey,
    #[case] network: Network,
    #[case] is_debug_flag_enabled: bool,
    #[case] expected_create_status: StatusCode,
    #[case] expected_auth_status: StatusCode,
    #[case] expected_contains_auth_challenge: bool,
    #[case] should_refresh_auth: bool,
) {
    let feature_flag_override = if is_debug_flag_enabled {
        vec![(
            "f8e-debug-authentication-calls".to_owned(),
            "true".to_owned(),
        )]
    } else {
        vec![]
    }
    .into_iter()
    .collect::<HashMap<String, String>>();
    let overrides = GenServiceOverrides::new().feature_flags(feature_flag_override);
    let (mut context, bootstrap) = gen_services_with_overrides(overrides).await;
    let client = TestClient::new(bootstrap.router).await;

    let keys = create_new_authkeys(&mut context);
    let recovery_privkey = keys.recovery.secret_key;
    // First, make an account
    let request = CreateAccountRequest::Full {
        auth: FullAccountAuthKeysPayload {
            app: keys.app.public_key,
            hardware: keys.hw.public_key,
            recovery: Some(keys.recovery.public_key),
        },
        spending: SpendingKeysetRequest {
            network,
            app: spending_app_xpub,
            hardware: spending_hw_xpub,
        },
        is_test_account: true,
    };
    let actual_response = client.create_account(&mut context, &request).await;
    assert_eq!(
        actual_response.status_code, expected_create_status,
        "{}",
        actual_response.body_string
    );

    // now try to initiate auth with the hw key
    let request = AuthenticationRequest {
        auth_request_key: AuthRequestKey::RecoveryPubkey(keys.recovery.public_key),
    };
    let actual_response = client.authenticate(&request).await;
    assert_eq!(
        actual_response.status_code, expected_auth_status,
        "{}",
        actual_response.body_string
    );
    let response_body = actual_response.body;
    assert!(response_body.as_ref().is_some());
    if expected_contains_auth_challenge {
        assert_eq!(
            hex::decode(response_body.as_ref().unwrap().challenge.clone())
                .unwrap()
                .len(),
            64 // our dummy challenge is 64 bytes long
        );
    }

    if expected_auth_status == StatusCode::OK {
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
            actual_response.status_code, expected_auth_status,
            "{}",
            actual_response.body_string
        );

        if should_refresh_auth {
            let refresh_token = actual_response.body.unwrap().refresh_token;
            let request = GetTokensRequest {
                challenge: None,
                refresh_token: Some(refresh_token),
            };
            let actual_response = client.get_tokens(&request).await;
            assert_eq!(
                actual_response.status_code, expected_auth_status,
                "{}",
                actual_response.body_string
            );
        }
    }
}
