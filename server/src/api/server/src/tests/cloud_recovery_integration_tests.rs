use std::str::FromStr;

use http::StatusCode;
use std::default::Default;
use time::{Duration, OffsetDateTime};
use types::account::identifiers::AccountId;

use account::entities::{Factor, FullAccountAuthKeysPayload, Network, SpendingKeysetRequest};
use account::service::FetchAccountInput;
use bdk_utils::bdk::bitcoin::hashes::sha256;
use bdk_utils::bdk::bitcoin::secp256k1::{Message, Secp256k1, SecretKey};
use bdk_utils::bdk::miniscript::DescriptorPublicKey;
use onboarding::routes::CreateAccountRequest;
use recovery::entities::{RecoveryDestination, RecoveryStatus, RecoveryType};
use recovery::routes::{AuthenticationKey, RotateAuthenticationKeysRequest};
use types::authn_authz::cognito::CognitoUser;

use crate::tests;
use crate::tests::lib::{
    create_keypair, create_new_authkeys, create_phone_touchpoint, create_push_touchpoint,
    generate_delay_and_notify_recovery,
};
use crate::tests::{gen_services, requests::axum::TestClient, TestAuthenticationKeys, TestKeypair};

struct RotateAuthenticationKeysTestVector {
    include_initial_recovery_pubkey: bool,
    override_auth_hardware_sk: Option<SecretKey>,
    override_app_signature: Option<String>,
    rotate_recovery_pubkey: bool,
    override_recovery_signature: Option<String>,
    expected_status: StatusCode,
    existing_delay_notify: bool,
    override_with_fake_keyproof_account_id: bool,
}

impl Default for RotateAuthenticationKeysTestVector {
    fn default() -> Self {
        Self {
            include_initial_recovery_pubkey: false,
            override_auth_hardware_sk: None,
            override_app_signature: None,
            rotate_recovery_pubkey: true,
            override_recovery_signature: None,
            expected_status: StatusCode::OK,
            existing_delay_notify: false,
            override_with_fake_keyproof_account_id: false,
        }
    }
}

async fn rotate_authentication_keys_test(vector: RotateAuthenticationKeysTestVector) {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;
    let secp = Secp256k1::new();

    let network = Network::BitcoinSignet;
    let keys = create_new_authkeys(&mut context);
    let spending_app_dpub = DescriptorPublicKey::from_str("[74ce1142/84'/1'/0']tpubD6NzVbkrYhZ4XFo7hggmFF9qDqwrR9aqZv6j2Sgp1N5aVyxyMXxQG14grtRa3ob8ddZqxbd2hbPU7dEXvPRDRuQJ3NsMaGDaZXkLEewdthy/0/*").unwrap();
    let spending_hardware_dpub = DescriptorPublicKey::from_str("[9e61ede9/84'/1'/0']tpubD6NzVbkrYhZ4Xwyrc51ZUDmxHYdTBpmTqTwSB6vr93T3Rt72nPzx2kjTV8VeWJW741HvVGvRyPSHZBgA5AEGD8Eib3sMwazMEuaQf1ioGBo/0/*").unwrap();

    // First, make an account
    let request = CreateAccountRequest::Full {
        auth: FullAccountAuthKeysPayload {
            app: keys.app.public_key,
            hardware: keys.hw.public_key,
            recovery: if vector.include_initial_recovery_pubkey {
                Some(keys.recovery.public_key)
            } else {
                None
            },
        },
        spending: SpendingKeysetRequest {
            network: network.into(),
            app: spending_app_dpub.clone(),
            hardware: spending_hardware_dpub.clone(),
        },
        is_test_account: true,
    };

    let actual_response = client.create_account(&mut context, &request).await;
    assert_eq!(
        actual_response.status_code,
        StatusCode::OK,
        "{}",
        actual_response.body_string
    );
    let account_id = actual_response.body.unwrap().account_id;
    let keys = context
        .get_authentication_keys_for_account_id(&account_id)
        .expect("Keys not found for account");

    // Create some touchpoints to ensure push get cleared if applicable
    create_push_touchpoint(&bootstrap.services, &account_id).await;
    create_phone_touchpoint(&bootstrap.services, &account_id, true).await;

    let active_auth_key_id = bootstrap
        .services
        .account_service
        .fetch_full_account(FetchAccountInput {
            account_id: &account_id,
        })
        .await
        .expect("Account should exists")
        .common_fields
        .active_auth_keys_id;

    if vector.existing_delay_notify {
        let dn_keys = create_new_authkeys(&mut context);
        let existing_delay_notify = generate_delay_and_notify_recovery(
            account_id.clone(),
            RecoveryDestination {
                source_auth_keys_id: active_auth_key_id.clone(),
                app_auth_pubkey: dn_keys.app.public_key,
                hardware_auth_pubkey: dn_keys.hw.public_key,
                recovery_auth_pubkey: None,
            },
            OffsetDateTime::now_utc() + Duration::days(1),
            recovery::entities::RecoveryStatus::Pending,
            Factor::App,
        );
        bootstrap
            .services
            .recovery_service
            .create(&existing_delay_notify)
            .await
            .unwrap();
    }

    let (new_auth_app_seckey, new_auth_app_pubkey) = create_keypair();
    let app_signature = vector.override_app_signature.unwrap_or_else(|| {
        let secp = Secp256k1::new();
        let message = Message::from_hashed_data::<sha256::Hash>(account_id.to_string().as_bytes());
        secp.sign_ecdsa(&message, &new_auth_app_seckey).to_string()
    });
    let new_auth_hardware_seckey = vector
        .override_auth_hardware_sk
        .unwrap_or(keys.hw.secret_key);
    let new_auth_hardware_pubkey = new_auth_hardware_seckey.public_key(&secp);
    let hardware_signature = {
        let secp = Secp256k1::new();
        let message = Message::from_hashed_data::<sha256::Hash>(account_id.to_string().as_bytes());
        secp.sign_ecdsa(&message, &new_auth_hardware_seckey)
            .to_string()
    };

    let (new_auth_recovery_seckey, new_auth_recovery_pubkey) = create_keypair();
    let recovery_signature = vector.override_recovery_signature.unwrap_or_else(|| {
        let secp = Secp256k1::new();
        let message = Message::from_hashed_data::<sha256::Hash>(account_id.to_string().as_bytes());
        secp.sign_ecdsa(&message, &new_auth_recovery_seckey)
            .to_string()
    });

    // Add it to the context as we're going to need to use it for the rotate call
    context.add_authentication_keys(TestAuthenticationKeys {
        app: TestKeypair {
            public_key: new_auth_app_pubkey,
            secret_key: new_auth_app_seckey,
        },
        hw: TestKeypair {
            public_key: new_auth_hardware_pubkey,
            secret_key: new_auth_hardware_seckey,
        },
        recovery: TestKeypair {
            public_key: new_auth_recovery_pubkey,
            secret_key: new_auth_recovery_seckey,
        },
    });

    // If we're testing with a different account, we need to use the keyproof account id
    let keyproof_account_id = if vector.override_with_fake_keyproof_account_id {
        AccountId::gen().expect("Invalid account id")
    } else {
        account_id.clone()
    };

    let response = client
        .rotate_authentication_keys_with_keyproof_account_id(
            &mut context,
            &keyproof_account_id,
            &account_id,
            &RotateAuthenticationKeysRequest {
                application: AuthenticationKey {
                    key: new_auth_app_pubkey,
                    signature: app_signature,
                },
                hardware: AuthenticationKey {
                    key: new_auth_hardware_pubkey,
                    signature: hardware_signature,
                },
                recovery: if vector.rotate_recovery_pubkey {
                    Some(AuthenticationKey {
                        key: new_auth_recovery_pubkey,
                        signature: recovery_signature,
                    })
                } else {
                    None
                },
            },
            &keys,
        )
        .await;

    assert_eq!(
        response.status_code, vector.expected_status,
        "{}",
        response.body_string
    );

    // If keyproof account id is different from test account, we don't bother checking anything else.
    if keyproof_account_id != account_id {
        return;
    }

    let account = bootstrap
        .services
        .account_service
        .fetch_full_account(FetchAccountInput {
            account_id: &account_id,
        })
        .await
        .unwrap();

    if response.status_code == StatusCode::OK {
        assert_ne!(
            account.common_fields.active_auth_keys_id,
            active_auth_key_id
        );
        let auth = account
            .active_auth_keys()
            .expect("Auth keys should be present");
        assert_eq!(auth.app_pubkey, new_auth_app_pubkey);
        assert_eq!(auth.hardware_pubkey, keys.hw.public_key);
        let recovery_cognito_user_exists = bootstrap
            .services
            .userpool_service
            .is_existing_cognito_user(CognitoUser::Recovery(account_id.clone()))
            .await
            .unwrap();
        if vector.rotate_recovery_pubkey {
            assert_eq!(auth.recovery_pubkey, Some(new_auth_recovery_pubkey));
            assert!(recovery_cognito_user_exists);
        } else {
            assert_eq!(auth.recovery_pubkey, None);
            assert!(!recovery_cognito_user_exists);
        }

        if vector.existing_delay_notify {
            let existing_delay_notify = bootstrap
                .services
                .recovery_service
                .fetch_by_status_since(
                    &account_id,
                    RecoveryType::DelayAndNotify,
                    RecoveryStatus::Canceled,
                    bootstrap.services.recovery_service.cur_time() - Duration::days(1),
                )
                .await
                .unwrap();
            assert!(existing_delay_notify.is_some());
        }
        assert_eq!(account.common_fields.touchpoints.len(), 1);
    } else {
        assert_eq!(
            account.common_fields.active_auth_keys_id,
            active_auth_key_id
        );
        let auth = account
            .active_auth_keys()
            .expect("Auth keys should be present");
        assert_eq!(auth.app_pubkey, keys.app.public_key);
        assert_eq!(auth.hardware_pubkey, keys.hw.public_key);
        let recovery_cognito_user_exists = bootstrap
            .services
            .userpool_service
            .is_existing_cognito_user(CognitoUser::Recovery(account_id))
            .await
            .unwrap();
        if vector.include_initial_recovery_pubkey {
            assert_eq!(auth.recovery_pubkey, Some(keys.recovery.public_key));
            assert!(recovery_cognito_user_exists);
        } else {
            assert_eq!(auth.recovery_pubkey, None);
            assert!(!recovery_cognito_user_exists);
        }
        assert_eq!(auth.recovery_pubkey, Some(keys.recovery.public_key));
        assert_eq!(account.common_fields.touchpoints.len(), 2);
    }
}

tests! {
    runner = rotate_authentication_keys_test,
    test_successfully_rotate_app_auth_key: RotateAuthenticationKeysTestVector {
        ..Default::default()
    },
    test_successfully_rotate_app_auth_key_with_recovery_pubkey: RotateAuthenticationKeysTestVector {
        include_initial_recovery_pubkey: true,
        ..Default::default()
    },
    test_rotate_keys_without_preexisting_recovery_auth_key: RotateAuthenticationKeysTestVector {
        rotate_recovery_pubkey: false,
        ..Default::default()
    },
    test_rotate_keys_with_preexisting_recovery_auth_key: RotateAuthenticationKeysTestVector {
        include_initial_recovery_pubkey: true,
        rotate_recovery_pubkey: false,
        expected_status: StatusCode::BAD_REQUEST,
        ..Default::default()
    },
    test_successfully_rotate_app_auth_key_with_existing_delay_notify: RotateAuthenticationKeysTestVector {
        include_initial_recovery_pubkey: true,
        existing_delay_notify: true,
        ..Default::default()
    },
    test_bad_signature_rotate_app_auth_key: RotateAuthenticationKeysTestVector {
        include_initial_recovery_pubkey: true,
        override_app_signature: Some("this_should_fail".to_string()),
        expected_status: StatusCode::BAD_REQUEST,
        ..Default::default()
    },
    test_bad_signature_rotate_recovery_auth_key: RotateAuthenticationKeysTestVector {
        include_initial_recovery_pubkey: true,
        override_recovery_signature: Some("this_should_fail".to_string()),
        expected_status: StatusCode::BAD_REQUEST,
        ..Default::default()
    },
    // For now, we'll only allow you to rotate the application key
    // TODO: Remove this test once we support rotating both keys
    test_rotate_both_keys: RotateAuthenticationKeysTestVector {
        include_initial_recovery_pubkey: true,
        override_auth_hardware_sk: Some(SecretKey::from_str("09d04b6f58117ad43a04f671daf776ff00ca8c97807aea12d432eb500c0e2bde").unwrap()),
        expected_status: StatusCode::BAD_REQUEST,
        ..Default::default()
    },
    test_rotate_keys_with_invalid_keyproof_account_id: RotateAuthenticationKeysTestVector {
        override_with_fake_keyproof_account_id: true,
        expected_status: StatusCode::UNAUTHORIZED,
        ..Default::default()
    },
}
