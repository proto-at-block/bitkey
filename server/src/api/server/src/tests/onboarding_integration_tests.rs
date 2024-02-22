use std::collections::HashSet;
use std::str::FromStr;
use std::vec;

use axum::response::IntoResponse;
use bdk_utils::bdk::bitcoin::secp256k1::PublicKey;
use errors::ApiError;
use http::StatusCode;
use http_body_util::BodyExt;

use onboarding::account_validation::error::AccountValidationError;
use recovery::entities::{RecoveryDestination, RecoveryStatus};
use time::Duration;
use types::notification::{NotificationChannel, NotificationsPreferences};
use ulid::Ulid;

use account::entities::{
    Factor, FullAccountAuthKeysPayload, LiteAccountAuthKeysPayload, Network as AccountNetwork,
    SpendingKeysetRequest, Touchpoint, TouchpointPlatform, UpgradeLiteAccountAuthKeysPayload,
};
use account::service::FetchAccountInput;
use bdk_utils::bdk::bitcoin::Network;
use bdk_utils::bdk::miniscript::DescriptorPublicKey;
use comms_verification::TEST_CODE;
use external_identifier::ExternalIdentifier;
use onboarding::routes::{
    AccountActivateTouchpointRequest, AccountAddDeviceTokenRequest, AccountAddTouchpointRequest,
    AccountVerifyTouchpointRequest, CompleteOnboardingRequest, CreateAccountRequest,
    UpgradeAccountRequest,
};
use types::account::identifiers::TouchpointId;

use crate::tests;
use crate::tests::gen_services;
use crate::tests::lib::{create_account, create_descriptor_keys, create_pubkey};
use crate::tests::requests::axum::TestClient;
use crate::tests::requests::{CognitoAuthentication, Response};

use super::lib::{create_lite_account, create_spend_keyset, generate_delay_and_notify_recovery};

struct OnboardingTestVector {
    include_recovery_auth_pubkey: bool,
    spending_app_xpub: DescriptorPublicKey,
    spending_hw_xpub: DescriptorPublicKey,
    network: Network,
    expected_derivation_path: &'static str,
    expected_status: StatusCode,
}

async fn onboarding_test(vector: OnboardingTestVector) {
    let bootstrap = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let request = CreateAccountRequest::Full {
        auth: FullAccountAuthKeysPayload {
            app: create_pubkey(),
            hardware: create_pubkey(),
            recovery: if vector.include_recovery_auth_pubkey {
                Some(create_pubkey())
            } else {
                None
            },
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
        actual_response.status_code, vector.expected_status,
        "{}",
        actual_response.body_string
    );

    if let Some(actual_body) = actual_response.body {
        let keyset = actual_body.keyset.expect("Account should have a keyset");
        assert!(
            keyset
                .spending
                .to_string()
                .contains(vector.expected_derivation_path),
            "{}",
            keyset.spending.to_string()
        );
        assert!(keyset.spending.to_string().ends_with("/*"));
    }
}

tests! {
    runner = onboarding_test,
    test_onboarding_valid_keys_without_recovery_authkey_on_testnet: OnboardingTestVector {
        include_recovery_auth_pubkey: false,
        spending_app_xpub: DescriptorPublicKey::from_str("[74ce1142/84'/1'/0']tpubD6NzVbkrYhZ4XFo7hggmFF9qDqwrR9aqZv6j2Sgp1N5aVyxyMXxQG14grtRa3ob8ddZqxbd2hbPU7dEXvPRDRuQJ3NsMaGDaZXkLEewdthy/0/*").unwrap(),
        spending_hw_xpub: DescriptorPublicKey::from_str("[9e61ede9/84'/1'/0']tpubD6NzVbkrYhZ4Xwyrc51ZUDmxHYdTBpmTqTwSB6vr93T3Rt72nPzx2kjTV8VeWJW741HvVGvRyPSHZBgA5AEGD8Eib3sMwazMEuaQf1ioGBo/0/*").unwrap(),
        network: Network::Testnet,
        expected_derivation_path: "/84'/1'/0'",
        expected_status: StatusCode::OK,
    },
    test_onboarding_valid_keys_with_recovery_authkey_on_testnet: OnboardingTestVector {
        include_recovery_auth_pubkey: true,
        spending_app_xpub: DescriptorPublicKey::from_str("[74ce1142/84'/1'/0']tpubD6NzVbkrYhZ4XFo7hggmFF9qDqwrR9aqZv6j2Sgp1N5aVyxyMXxQG14grtRa3ob8ddZqxbd2hbPU7dEXvPRDRuQJ3NsMaGDaZXkLEewdthy/0/*").unwrap(),
        spending_hw_xpub: DescriptorPublicKey::from_str("[9e61ede9/84'/1'/0']tpubD6NzVbkrYhZ4Xwyrc51ZUDmxHYdTBpmTqTwSB6vr93T3Rt72nPzx2kjTV8VeWJW741HvVGvRyPSHZBgA5AEGD8Eib3sMwazMEuaQf1ioGBo/0/*").unwrap(),
        network: Network::Testnet,
        expected_derivation_path: "/84'/1'/0'",
        expected_status: StatusCode::OK,
    },
}

struct AddDeviceTokenTestVector {
    request: AccountAddDeviceTokenRequest,
    expected_status: StatusCode,
}

async fn add_device_token_test(vector: AddDeviceTokenTestVector) {
    let bootstrap = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;
    let account = create_account(
        &bootstrap.services,
        account::entities::Network::BitcoinSignet,
        None,
    )
    .await;

    let actual_response = client
        .add_device_token(&account.id.to_string(), &vector.request)
        .await;
    assert_eq!(
        actual_response.status_code, vector.expected_status,
        "{}",
        actual_response.body_string
    );
    assert_eq!(
        actual_response.status_code,
        client
            .add_device_token(&account.id.to_string(), &vector.request)
            .await
            .status_code,
    );

    if actual_response.body.is_some() {
        let account = bootstrap
            .services
            .account_service
            .fetch_full_account(FetchAccountInput {
                account_id: &account.id,
            })
            .await
            .unwrap();
        assert!(account
            .common_fields
            .touchpoints
            .iter()
            .find(|t| {
                if let Touchpoint::Push {
                    platform: _,
                    arn: _,
                    device_token,
                } = t
                {
                    *device_token == vector.request.device_token
                } else {
                    false
                }
            })
            .is_some());
        assert_eq!(account.common_fields.touchpoints.len(), 1);
    }
}

tests! {
    runner = add_device_token_test,
    test_add_device_token_to_testnet_wallet: AddDeviceTokenTestVector {
        request: AccountAddDeviceTokenRequest {
            device_token: "device-token".to_owned(),
            platform: TouchpointPlatform::ApnsTeam,
        },
        expected_status: StatusCode::OK,
    },
}

enum TouchpointLifecycleTestStep {
    AddPhoneTouchpoint {
        phone_number: String,
        expected_status: StatusCode,
        expected_num_touchpoints: usize,
    },
    AddEmailTouchpoint {
        email_address: String,
        expected_status: StatusCode,
        expected_num_touchpoints: usize,
    },
    VerifyTouchpoint {
        use_last_seen_touchpoint_id: bool,
        use_real_verification_code: bool,
        expected_status: StatusCode,
        expected_num_touchpoints: usize,
    },
    ActivateTouchpoint {
        use_last_seen_touchpoint_id: bool,
        expected_status: StatusCode,
        expected_num_touchpoints: usize,
        app_signed: bool,
        hw_signed: bool,
    },
}

trait TouchpointTestGetters {
    fn get_email_address(&self) -> String;
    fn get_phone_number(&self) -> String;
    fn get_active(&self) -> bool;
}

impl TouchpointTestGetters for Touchpoint {
    fn get_email_address(&self) -> String {
        match self {
            Touchpoint::Email { email_address, .. } => Some(email_address.to_owned()),
            _ => None,
        }
        .unwrap()
    }

    fn get_phone_number(&self) -> String {
        match self {
            Touchpoint::Phone { phone_number, .. } => Some(phone_number.to_owned()),
            _ => None,
        }
        .unwrap()
    }

    fn get_active(&self) -> bool {
        match self {
            Touchpoint::Email { active, .. } => Some(*active),
            Touchpoint::Phone { active, .. } => Some(*active),
            _ => None,
        }
        .unwrap()
    }
}

struct TouchpointLifecycleTestVector {
    onboarding_complete: bool,
    steps: Vec<TouchpointLifecycleTestStep>,
}

async fn touchpoint_lifecycle_test(vector: TouchpointLifecycleTestVector) {
    let bootstrap = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;
    let account = create_account(
        &bootstrap.services,
        account::entities::Network::BitcoinSignet,
        None,
    )
    .await;

    if vector.onboarding_complete {
        assert_eq!(
            client
                .complete_onboarding(&account.id.to_string(), &CompleteOnboardingRequest {})
                .await
                .status_code,
            200,
        );
    }

    let mut last_seen_touchpoint_id = TouchpointId::new(Ulid::default()).unwrap();

    for step in vector.steps {
        match step {
            TouchpointLifecycleTestStep::AddPhoneTouchpoint { .. }
            | TouchpointLifecycleTestStep::AddEmailTouchpoint { .. } => {
                let (req, expected_status, expected_num_touchpoints) = match step {
                    TouchpointLifecycleTestStep::AddPhoneTouchpoint {
                        phone_number,
                        expected_status,
                        expected_num_touchpoints,
                    } => (
                        AccountAddTouchpointRequest::Phone {
                            phone_number: phone_number.clone(),
                        },
                        expected_status,
                        expected_num_touchpoints,
                    ),
                    TouchpointLifecycleTestStep::AddEmailTouchpoint {
                        email_address,
                        expected_status,
                        expected_num_touchpoints,
                    } => (
                        AccountAddTouchpointRequest::Email {
                            email_address: email_address.clone(),
                        },
                        expected_status,
                        expected_num_touchpoints,
                    ),
                    _ => panic!("This is impossible"),
                };

                let actual_response = client.add_touchpoint(&account.id.to_string(), &req).await;

                assert_eq!(actual_response.status_code, expected_status,);

                let account = bootstrap
                    .services
                    .account_service
                    .fetch_full_account(FetchAccountInput {
                        account_id: &account.id,
                    })
                    .await
                    .unwrap();
                assert_eq!(
                    account.common_fields.touchpoints.len(),
                    expected_num_touchpoints
                );

                if actual_response.status_code == StatusCode::OK {
                    let touchpoint_id = actual_response.body.unwrap().touchpoint_id;

                    let account = bootstrap
                        .services
                        .account_service
                        .fetch_account(FetchAccountInput {
                            account_id: &account.id,
                        })
                        .await
                        .unwrap();
                    let touchpoint = account.get_touchpoint_by_id(touchpoint_id.clone());

                    assert!(
                        touchpoint.is_some()
                            && !touchpoint.unwrap().get_active()
                            && match req {
                                AccountAddTouchpointRequest::Email { email_address } => {
                                    touchpoint.unwrap().get_email_address() == email_address
                                }
                                AccountAddTouchpointRequest::Phone { phone_number } => {
                                    touchpoint.unwrap().get_phone_number() == phone_number
                                }
                            },
                    );

                    last_seen_touchpoint_id = touchpoint_id;
                }
            }
            TouchpointLifecycleTestStep::VerifyTouchpoint {
                use_last_seen_touchpoint_id,
                use_real_verification_code: use_last_seen_verification_code,
                expected_status,
                expected_num_touchpoints,
            } => {
                let touchpoint_id = if use_last_seen_touchpoint_id {
                    last_seen_touchpoint_id.clone()
                } else {
                    TouchpointId::new(Ulid::default()).unwrap()
                };

                let req = AccountVerifyTouchpointRequest {
                    verification_code: if use_last_seen_verification_code {
                        TEST_CODE.to_owned()
                    } else {
                        "fake".to_owned()
                    },
                };
                let actual_response = client
                    .verify_touchpoint(&account.id.to_string(), &touchpoint_id.to_string(), &req)
                    .await;

                assert_eq!(actual_response.status_code, expected_status,);

                let account = bootstrap
                    .services
                    .account_service
                    .fetch_full_account(FetchAccountInput {
                        account_id: &account.id,
                    })
                    .await
                    .unwrap();
                assert_eq!(
                    account.common_fields.touchpoints.len(),
                    expected_num_touchpoints
                );

                if actual_response.status_code == StatusCode::OK {
                    let account = bootstrap
                        .services
                        .account_service
                        .fetch_account(FetchAccountInput {
                            account_id: &account.id,
                        })
                        .await
                        .unwrap();
                    let touchpoint = account.get_touchpoint_by_id(touchpoint_id);

                    assert!(touchpoint.is_some() && !touchpoint.unwrap().get_active());
                }
            }
            TouchpointLifecycleTestStep::ActivateTouchpoint {
                use_last_seen_touchpoint_id,
                expected_status,
                expected_num_touchpoints,
                app_signed,
                hw_signed,
            } => {
                let touchpoint_id = if use_last_seen_touchpoint_id {
                    last_seen_touchpoint_id.clone()
                } else {
                    TouchpointId::new(Ulid::default()).unwrap()
                };

                let req = AccountActivateTouchpointRequest {};
                let actual_response = client
                    .activate_touchpoint(
                        &account.id.to_string(),
                        &touchpoint_id.to_string(),
                        &req,
                        app_signed,
                        hw_signed,
                    )
                    .await;

                assert_eq!(actual_response.status_code, expected_status,);

                let account = bootstrap
                    .services
                    .account_service
                    .fetch_full_account(FetchAccountInput {
                        account_id: &account.id,
                    })
                    .await
                    .unwrap();
                assert_eq!(
                    account.common_fields.touchpoints.len(),
                    expected_num_touchpoints
                );

                if actual_response.status_code == StatusCode::OK {
                    let account = bootstrap
                        .services
                        .account_service
                        .fetch_account(FetchAccountInput {
                            account_id: &account.id,
                        })
                        .await
                        .unwrap();
                    let touchpoint = account.get_touchpoint_by_id(touchpoint_id);

                    assert!(touchpoint.is_some() && touchpoint.unwrap().get_active());
                }
            }
        }
    }
}

tests! {
    runner = touchpoint_lifecycle_test,
    test_touchpoint_lifecycle: TouchpointLifecycleTestVector {
        onboarding_complete: false,
        steps: vec![
            TouchpointLifecycleTestStep::AddPhoneTouchpoint {
                // Add an invalid phone number fails
                phone_number: "15555555555".to_owned(),
                expected_status: StatusCode::BAD_REQUEST,
                expected_num_touchpoints: 0,
            },
            TouchpointLifecycleTestStep::AddPhoneTouchpoint {
                // Add an unsupported country code fails
                phone_number: "+4402055555555".to_owned(),
                expected_status: StatusCode::BAD_REQUEST,
                expected_num_touchpoints: 0,
            },
            TouchpointLifecycleTestStep::AddPhoneTouchpoint {
                // Add a valid phone number succeeds
                phone_number: "+15555555555".to_owned(),
                expected_status: StatusCode::OK,
                expected_num_touchpoints: 1,
            },
            TouchpointLifecycleTestStep::AddPhoneTouchpoint {
                // Re-add a pending valid phone number succeeds (e.g. abort before verification and start with same number)
                phone_number: "+15555555555".to_owned(),
                expected_status: StatusCode::OK,
                expected_num_touchpoints: 1,
            },
            TouchpointLifecycleTestStep::AddPhoneTouchpoint {
                // Add a fresh valid phone number replaces previous pending phone number (e.g. abort before verification and start with new number)
                phone_number: "+15555555556".to_owned(),
                expected_status: StatusCode::OK,
                expected_num_touchpoints: 1,
            },
            TouchpointLifecycleTestStep::ActivateTouchpoint {
                // Activate unverified touchpoint id fails
                use_last_seen_touchpoint_id: true,
                expected_status: StatusCode::BAD_REQUEST,
                expected_num_touchpoints: 1,
                app_signed: true,
                hw_signed: true,
            },
            TouchpointLifecycleTestStep::VerifyTouchpoint {
                // Verify non-existing touchpoint id fails
                use_last_seen_touchpoint_id: false,
                use_real_verification_code: true,
                expected_status: StatusCode::NOT_FOUND,
                expected_num_touchpoints: 1,
            },
            TouchpointLifecycleTestStep::VerifyTouchpoint {
                // Verify wrong verification code fails
                use_last_seen_touchpoint_id: true,
                use_real_verification_code: false,
                expected_status: StatusCode::BAD_REQUEST,
                expected_num_touchpoints: 1,
            },
            TouchpointLifecycleTestStep::VerifyTouchpoint {
                // Verify with correct verification code succeeds
                use_last_seen_touchpoint_id: true,
                use_real_verification_code: true,
                expected_status: StatusCode::OK,
                expected_num_touchpoints: 1,
            },
            TouchpointLifecycleTestStep::VerifyTouchpoint {
                // Verify already verified phone number fails
                use_last_seen_touchpoint_id: true,
                use_real_verification_code: true,
                expected_status: StatusCode::BAD_REQUEST,
                expected_num_touchpoints: 1,
            },
            TouchpointLifecycleTestStep::AddPhoneTouchpoint {
                // Re-add already verified phone number succeeds (e.g. abort after verification and start with same number)
                phone_number: "+15555555556".to_owned(),
                expected_status: StatusCode::OK,
                expected_num_touchpoints: 1,
            },
            TouchpointLifecycleTestStep::VerifyTouchpoint {
                // Verify with correct verification code succeeds
                use_last_seen_touchpoint_id: true,
                use_real_verification_code: true,
                expected_status: StatusCode::OK,
                expected_num_touchpoints: 1,
            },
            TouchpointLifecycleTestStep::AddPhoneTouchpoint {
                // Add a fresh valid phone number replaces previous verified phone number (e.g. abort after verification and start with new number)
                phone_number: "+15555555557".to_owned(),
                expected_status: StatusCode::OK,
                expected_num_touchpoints: 1,
            },
            TouchpointLifecycleTestStep::VerifyTouchpoint {
                // Verify with correct verification code succeeds
                use_last_seen_touchpoint_id: true,
                use_real_verification_code: true,
                expected_status: StatusCode::OK,
                expected_num_touchpoints: 1,
            },
            TouchpointLifecycleTestStep::ActivateTouchpoint {
                // Activate non-existing touchpoint id fails
                use_last_seen_touchpoint_id: false,
                expected_status: StatusCode::NOT_FOUND,
                expected_num_touchpoints: 1,
                app_signed: false,
                hw_signed: false,
            },
            TouchpointLifecycleTestStep::ActivateTouchpoint {
                // Activate verified touchpoint succeeds
                use_last_seen_touchpoint_id: true,
                expected_status: StatusCode::OK,
                expected_num_touchpoints: 1,
                app_signed: false,
                hw_signed: false,
            },
            TouchpointLifecycleTestStep::AddPhoneTouchpoint {
                // Add a fresh valid phone number after activation succeeds (e.g. get ready to replace phone number)
                phone_number: "+15555555558".to_owned(),
                expected_status: StatusCode::OK,
                expected_num_touchpoints: 2,
            },
            TouchpointLifecycleTestStep::VerifyTouchpoint {
                // Verify with correct verification code succeeds
                use_last_seen_touchpoint_id: true,
                use_real_verification_code: true,
                expected_status: StatusCode::OK,
                expected_num_touchpoints: 2,
            },
            TouchpointLifecycleTestStep::ActivateTouchpoint {
                // Activate verified touchpoint succeeds and replaces previous active touchpoint
                use_last_seen_touchpoint_id: true,
                expected_status: StatusCode::OK,
                expected_num_touchpoints: 1,
                app_signed: false,
                hw_signed: false,
            },
            TouchpointLifecycleTestStep::AddEmailTouchpoint {
                // Add an invalid email address fails
                email_address: "notanemailaddress".to_owned(),
                expected_status: StatusCode::BAD_REQUEST,
                expected_num_touchpoints: 1,
            },
            TouchpointLifecycleTestStep::AddEmailTouchpoint {
                // Add a valid email address succeeds
                email_address: "fake@email.invalid".to_owned(),
                expected_status: StatusCode::OK,
                expected_num_touchpoints: 2,
            },
            TouchpointLifecycleTestStep::VerifyTouchpoint {
                // Verify pending email address with correct verification code succeeds
                use_last_seen_touchpoint_id: true,
                use_real_verification_code: true,
                expected_status: StatusCode::OK,
                expected_num_touchpoints: 2,
            },
            TouchpointLifecycleTestStep::ActivateTouchpoint {
                // Activate verified touchpoint succeeds
                use_last_seen_touchpoint_id: true,
                expected_status: StatusCode::OK,
                expected_num_touchpoints: 2,
                app_signed: false,
                hw_signed: false,
            },
        ],
    },
    test_onboarding_complete: TouchpointLifecycleTestVector {
        onboarding_complete: true,
        steps: vec![
            TouchpointLifecycleTestStep::AddPhoneTouchpoint {
                phone_number: "+15555555555".to_owned(),
                expected_status: StatusCode::OK,
                expected_num_touchpoints: 1,
            },
            TouchpointLifecycleTestStep::VerifyTouchpoint {
                use_last_seen_touchpoint_id: true,
                use_real_verification_code: true,
                expected_status: StatusCode::OK,
                expected_num_touchpoints: 1,
            },
            TouchpointLifecycleTestStep::ActivateTouchpoint {
                // Activate verified touchpoint without either sig fails
                use_last_seen_touchpoint_id: true,
                expected_status: StatusCode::FORBIDDEN,
                expected_num_touchpoints: 1,
                app_signed: false,
                hw_signed: false,
            },
            TouchpointLifecycleTestStep::ActivateTouchpoint {
                // Activate verified touchpoint without app sig fails
                use_last_seen_touchpoint_id: true,
                expected_status: StatusCode::FORBIDDEN,
                expected_num_touchpoints: 1,
                app_signed: false,
                hw_signed: true,
            },
            TouchpointLifecycleTestStep::ActivateTouchpoint {
                // Activate verified touchpoint without hw sig fails
                use_last_seen_touchpoint_id: true,
                expected_status: StatusCode::FORBIDDEN,
                expected_num_touchpoints: 1,
                app_signed: true,
                hw_signed: false,
            },
            TouchpointLifecycleTestStep::ActivateTouchpoint {
                // Activate verified touchpoint with both sigs succeeds
                use_last_seen_touchpoint_id: true,
                expected_status: StatusCode::OK,
                expected_num_touchpoints: 1,
                app_signed: true,
                hw_signed: true,
            },
        ],
    },
}

#[tokio::test]
async fn test_duplicate_hw_auth_key_fails_onboarding() {
    // If we try to create an account with the same hw auth key twice, that should fail.
    let bootstrap = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let hw_authkey = create_pubkey();

    let first_request = CreateAccountRequest::Full {
        auth: FullAccountAuthKeysPayload {
            app: create_pubkey(),
            hardware: hw_authkey,
            recovery: Some(create_pubkey()),
        },
        spending: SpendingKeysetRequest {
            network: Network::Testnet,
            app: DescriptorPublicKey::from_str("[74ce1142/84'/1'/0']tpubD6NzVbkrYhZ4XFo7hggmFF9qDqwrR9aqZv6j2Sgp1N5aVyxyMXxQG14grtRa3ob8ddZqxbd2hbPU7dEXvPRDRuQJ3NsMaGDaZXkLEewdthy/0/*").unwrap(),
            hardware: DescriptorPublicKey::from_str("[74ce1142/84'/1'/0']tpubD6NzVbkrYhZ4XFo7hggmFF9qDqwrR9aqZv6j2Sgp1N5aVyxyMXxQG14grtRa3ob8ddZqxbd2hbPU7dEXvPRDRuQJ3NsMaGDaZXkLEewdthy/0/*").unwrap(),
        },
        is_test_account: true,
    };
    let actual_response = client.create_account(&first_request).await;
    assert_eq!(actual_response.status_code, StatusCode::OK,);

    let second_request = CreateAccountRequest::Full {
        auth: FullAccountAuthKeysPayload {
            app: create_pubkey(),
            hardware: hw_authkey,
            recovery: Some(create_pubkey()),
        },
        spending: SpendingKeysetRequest {
            network: Network::Testnet,
            app: DescriptorPublicKey::from_str("[74ce1142/84'/1'/0']tpubD6NzVbkrYhZ4XFo7hggmFF9qDqwrR9aqZv6j2Sgp1N5aVyxyMXxQG14grtRa3ob8ddZqxbd2hbPU7dEXvPRDRuQJ3NsMaGDaZXkLEewdthy/0/*").unwrap(),
            hardware: DescriptorPublicKey::from_str("[74ce1142/84'/1'/0']tpubD6NzVbkrYhZ4XFo7hggmFF9qDqwrR9aqZv6j2Sgp1N5aVyxyMXxQG14grtRa3ob8ddZqxbd2hbPU7dEXvPRDRuQJ3NsMaGDaZXkLEewdthy/0/*").unwrap(),
        },
        is_test_account: true,
    };
    let actual_response = client.create_account(&second_request).await;
    assert_eq!(actual_response.status_code, StatusCode::BAD_REQUEST,);
}

#[tokio::test]
async fn test_duplicate_recovery_auth_key_fails_onboarding() {
    // If we try to create an account with the same hw auth key twice, that should fail.
    let bootstrap = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let recovery_authkey = create_pubkey();

    let first_request = CreateAccountRequest::Full {
        auth: FullAccountAuthKeysPayload {
            app: create_pubkey(),
            hardware: create_pubkey(),
            recovery: Some(recovery_authkey),
        },
        spending: SpendingKeysetRequest {
            network: Network::Testnet,
            app: DescriptorPublicKey::from_str("[74ce1142/84'/1'/0']tpubD6NzVbkrYhZ4XFo7hggmFF9qDqwrR9aqZv6j2Sgp1N5aVyxyMXxQG14grtRa3ob8ddZqxbd2hbPU7dEXvPRDRuQJ3NsMaGDaZXkLEewdthy/0/*").unwrap(),
            hardware: DescriptorPublicKey::from_str("[74ce1142/84'/1'/0']tpubD6NzVbkrYhZ4XFo7hggmFF9qDqwrR9aqZv6j2Sgp1N5aVyxyMXxQG14grtRa3ob8ddZqxbd2hbPU7dEXvPRDRuQJ3NsMaGDaZXkLEewdthy/0/*").unwrap(),
        },
        is_test_account: true,
    };
    let actual_response = client.create_account(&first_request).await;
    assert_eq!(actual_response.status_code, StatusCode::OK,);

    let second_request = CreateAccountRequest::Full {
        auth: FullAccountAuthKeysPayload {
            app: create_pubkey(),
            hardware: create_pubkey(),
            recovery: Some(recovery_authkey),
        },
        spending: SpendingKeysetRequest {
            network: Network::Testnet,
            app: DescriptorPublicKey::from_str("[74ce1142/84'/1'/0']tpubD6NzVbkrYhZ4XFo7hggmFF9qDqwrR9aqZv6j2Sgp1N5aVyxyMXxQG14grtRa3ob8ddZqxbd2hbPU7dEXvPRDRuQJ3NsMaGDaZXkLEewdthy/0/*").unwrap(),
            hardware: DescriptorPublicKey::from_str("[74ce1142/84'/1'/0']tpubD6NzVbkrYhZ4XFo7hggmFF9qDqwrR9aqZv6j2Sgp1N5aVyxyMXxQG14grtRa3ob8ddZqxbd2hbPU7dEXvPRDRuQJ3NsMaGDaZXkLEewdthy/0/*").unwrap(),
        },
        is_test_account: true,
    };
    let actual_response = client.create_account(&second_request).await;
    assert_eq!(actual_response.status_code, StatusCode::BAD_REQUEST,);
}

#[tokio::test]
async fn test_idempotent_account_creation() {
    // If we try to create an account with the same hw auth key twice, that should fail.
    let bootstrap = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let hw_authkey = create_pubkey();
    let app_authkey = create_pubkey();
    let recovery_authkey = Some(create_pubkey());
    let first_request = CreateAccountRequest::Full {
        auth: FullAccountAuthKeysPayload {
            app: app_authkey,
            hardware: hw_authkey,
            recovery: recovery_authkey,
        },
        spending: SpendingKeysetRequest {
            network: Network::Testnet,
            app: DescriptorPublicKey::from_str("[74ce1142/84'/1'/0']tpubD6NzVbkrYhZ4XFo7hggmFF9qDqwrR9aqZv6j2Sgp1N5aVyxyMXxQG14grtRa3ob8ddZqxbd2hbPU7dEXvPRDRuQJ3NsMaGDaZXkLEewdthy/0/*").unwrap(),
            hardware: DescriptorPublicKey::from_str("[74ce1142/84'/1'/0']tpubD6NzVbkrYhZ4XFo7hggmFF9qDqwrR9aqZv6j2Sgp1N5aVyxyMXxQG14grtRa3ob8ddZqxbd2hbPU7dEXvPRDRuQJ3NsMaGDaZXkLEewdthy/0/*").unwrap(),
        },
        is_test_account: true,
    };
    let actual_response = client.create_account(&first_request).await;
    assert_eq!(actual_response.status_code, StatusCode::OK,);

    let second_request = CreateAccountRequest::Full {
        auth: FullAccountAuthKeysPayload {
            app: create_pubkey(),
            hardware: hw_authkey,
            recovery: recovery_authkey,
        },
        spending: SpendingKeysetRequest {
            network: Network::Testnet,
            app: DescriptorPublicKey::from_str("[74ce1142/84'/1'/0']tpubD6NzVbkrYhZ4XFo7hggmFF9qDqwrR9aqZv6j2Sgp1N5aVyxyMXxQG14grtRa3ob8ddZqxbd2hbPU7dEXvPRDRuQJ3NsMaGDaZXkLEewdthy/0/*").unwrap(),
            hardware: DescriptorPublicKey::from_str("[74ce1142/84'/1'/0']tpubD6NzVbkrYhZ4XFo7hggmFF9qDqwrR9aqZv6j2Sgp1N5aVyxyMXxQG14grtRa3ob8ddZqxbd2hbPU7dEXvPRDRuQJ3NsMaGDaZXkLEewdthy/0/*").unwrap(),
        },
        is_test_account: true,
    };
    let actual_response = client.create_account(&second_request).await;
    assert_eq!(actual_response.status_code, StatusCode::BAD_REQUEST,);
}

struct IdempotentCreateAccountTestVector {
    same_app_pubkey: bool,
    same_hardware_pubkey: bool,
    same_recovery_pubkey: bool,
    expected_create_status: StatusCode,
    expected_same_response: bool,
}

async fn idempotent_create_account_test(vector: IdempotentCreateAccountTestVector) {
    let bootstrap = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let hw_pubkey = create_pubkey();
    let app_pubkey = create_pubkey();
    let recovery_pubkey = Some(create_pubkey());
    let first_request = CreateAccountRequest::Full {
        auth: FullAccountAuthKeysPayload {
            app: app_pubkey,
            hardware: hw_pubkey,
            recovery: recovery_pubkey
        },
        spending: SpendingKeysetRequest {
            network: Network::Testnet,
            app: DescriptorPublicKey::from_str("[74ce1142/84'/1'/0']tpubD6NzVbkrYhZ4XFo7hggmFF9qDqwrR9aqZv6j2Sgp1N5aVyxyMXxQG14grtRa3ob8ddZqxbd2hbPU7dEXvPRDRuQJ3NsMaGDaZXkLEewdthy/0/*").unwrap(),
            hardware: DescriptorPublicKey::from_str("[74ce1142/84'/1'/0']tpubD6NzVbkrYhZ4XFo7hggmFF9qDqwrR9aqZv6j2Sgp1N5aVyxyMXxQG14grtRa3ob8ddZqxbd2hbPU7dEXvPRDRuQJ3NsMaGDaZXkLEewdthy/0/*").unwrap(),
        },
        is_test_account: true,
    };
    let actual_response = client.create_account(&first_request).await;
    assert_eq!(actual_response.status_code, StatusCode::OK);
    let first_create_response = actual_response.body.unwrap();

    let hw_pubkey = if vector.same_hardware_pubkey {
        hw_pubkey
    } else {
        create_pubkey()
    };
    let app_pubkey = if vector.same_app_pubkey {
        app_pubkey
    } else {
        create_pubkey()
    };
    let recovery_pubkey = if vector.same_recovery_pubkey {
        recovery_pubkey
    } else {
        Some(create_pubkey())
    };

    let second_request = CreateAccountRequest::Full {
        auth: FullAccountAuthKeysPayload {
            app: app_pubkey,
            hardware: hw_pubkey,
            recovery: recovery_pubkey,
        },
        spending: SpendingKeysetRequest {
            network: Network::Testnet,
            app: DescriptorPublicKey::from_str("[74ce1142/84'/1'/0']tpubD6NzVbkrYhZ4XFo7hggmFF9qDqwrR9aqZv6j2Sgp1N5aVyxyMXxQG14grtRa3ob8ddZqxbd2hbPU7dEXvPRDRuQJ3NsMaGDaZXkLEewdthy/0/*").unwrap(),
            hardware: DescriptorPublicKey::from_str("[74ce1142/84'/1'/0']tpubD6NzVbkrYhZ4XFo7hggmFF9qDqwrR9aqZv6j2Sgp1N5aVyxyMXxQG14grtRa3ob8ddZqxbd2hbPU7dEXvPRDRuQJ3NsMaGDaZXkLEewdthy/0/*").unwrap(),
        },
        is_test_account: true,
    };
    let actual_response = client.create_account(&second_request).await;
    assert_eq!(actual_response.status_code, vector.expected_create_status);
    if actual_response.status_code == StatusCode::OK {
        let second_create_response = actual_response.body.unwrap();
        if vector.expected_same_response {
            assert_eq!(first_create_response, second_create_response);
        } else {
            assert_ne!(first_create_response, second_create_response);
        }
    }
}

tests! {
    runner = idempotent_create_account_test,
    test_idempotent_create_account: IdempotentCreateAccountTestVector {
        same_app_pubkey: true,
        same_hardware_pubkey: true,
        same_recovery_pubkey: true,
        expected_create_status: StatusCode::OK,
        expected_same_response: true,
    },
}

struct CreateTestAccountWithNetworkTestVector {
    network: Network,
    test_account: bool,
    expected_status: StatusCode,
}

async fn create_test_account_with_network(vector: CreateTestAccountWithNetworkTestVector) {
    let bootstrap = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let hw_authkey = create_pubkey();
    let app_authkey = create_pubkey();
    let recovery_authkey = Some(create_pubkey());

    let network = vector.network.into();
    let (_, app_dpub) = create_descriptor_keys(network);
    let (_, hardware_dpub) = create_descriptor_keys(network);
    let first_request = CreateAccountRequest::Full {
        auth: FullAccountAuthKeysPayload {
            app: app_authkey,
            hardware: hw_authkey,
            recovery: recovery_authkey,
        },
        spending: SpendingKeysetRequest {
            network: vector.network,
            app: app_dpub,
            hardware: hardware_dpub,
        },
        is_test_account: vector.test_account,
    };
    let actual_response = client.create_account(&first_request).await;
    assert_eq!(actual_response.status_code, vector.expected_status);
}

tests! {
    runner = create_test_account_with_network,
    test_create_test_account_with_bitcoin_network: CreateTestAccountWithNetworkTestVector {
        network: Network::Bitcoin,
        test_account: true,
        expected_status: StatusCode::BAD_REQUEST,
    },
    test_create_test_account_with_signet_network: CreateTestAccountWithNetworkTestVector {
        network: Network::Signet,
        test_account: true,
        expected_status: StatusCode::OK,
    },
    test_create_full_account_with_bitcoin_network: CreateTestAccountWithNetworkTestVector {
        network: Network::Bitcoin,
        test_account: false,
        expected_status: StatusCode::OK,
    },
}

#[derive(Debug, PartialEq)]
enum CreateAccountKeyReuse {
    OtherAccountApp,
    OtherAccountHw,
    OtherAccountRecovery,
    OtherRecoveryApp,
    OtherRecoveryHw,
    OtherRecoveryRecovery,
    OtherAccountSpending,
}

#[derive(Debug)]
struct CreateAccountKeyValidationTestVector {
    key_reuses: Vec<CreateAccountKeyReuse>,
    expected_error: Option<ApiError>,
}

async fn create_account_key_validation_test(vector: CreateAccountKeyValidationTestVector) {
    let bootstrap = gen_services().await;
    let fixed_cur_time = bootstrap.services.recovery_service.cur_time();
    let client = TestClient::new(bootstrap.router).await;

    let other_account =
        &create_account(&bootstrap.services, AccountNetwork::BitcoinSignet, None).await;

    let other_recovery = &generate_delay_and_notify_recovery(
        other_account.clone().id,
        RecoveryDestination {
            source_auth_keys_id: other_account.common_fields.active_auth_keys_id.clone(),
            app_auth_pubkey: create_pubkey(),
            hardware_auth_pubkey: create_pubkey(),
            recovery_auth_pubkey: Some(create_pubkey()),
        },
        fixed_cur_time + Duration::days(7),
        RecoveryStatus::Pending,
        Factor::Hw,
    );
    bootstrap
        .services
        .recovery_service
        .create(other_recovery)
        .await
        .unwrap();

    let account_app_pubkey = if vector
        .key_reuses
        .contains(&CreateAccountKeyReuse::OtherAccountApp)
    {
        other_account.application_auth_pubkey.unwrap()
    } else if vector
        .key_reuses
        .contains(&CreateAccountKeyReuse::OtherRecoveryApp)
    {
        other_recovery.destination_app_auth_pubkey.unwrap()
    } else {
        create_pubkey()
    };

    let account_hardware_pubkey = if vector
        .key_reuses
        .contains(&CreateAccountKeyReuse::OtherAccountHw)
    {
        other_account.hardware_auth_pubkey
    } else if vector
        .key_reuses
        .contains(&CreateAccountKeyReuse::OtherRecoveryHw)
    {
        other_recovery.destination_hardware_auth_pubkey.unwrap()
    } else {
        create_pubkey()
    };

    let account_recovery_pubkey = if vector
        .key_reuses
        .contains(&CreateAccountKeyReuse::OtherAccountRecovery)
    {
        other_account.common_fields.recovery_auth_pubkey.unwrap()
    } else if vector
        .key_reuses
        .contains(&CreateAccountKeyReuse::OtherRecoveryRecovery)
    {
        other_recovery.destination_recovery_auth_pubkey.unwrap()
    } else {
        create_pubkey()
    };

    let spending_keyset = if vector
        .key_reuses
        .contains(&CreateAccountKeyReuse::OtherAccountSpending)
    {
        other_account.active_spending_keyset().unwrap().clone()
    } else {
        create_spend_keyset(AccountNetwork::BitcoinSignet).0
    };

    let response = client
        .create_account(&CreateAccountRequest::Full {
            auth: FullAccountAuthKeysPayload {
                app: account_app_pubkey,
                hardware: account_hardware_pubkey,
                recovery: Some(account_recovery_pubkey),
            },
            spending: SpendingKeysetRequest {
                network: Network::Signet,
                app: spending_keyset.app_dpub,
                hardware: spending_keyset.hardware_dpub,
            },
            is_test_account: true,
        })
        .await;

    if let Some(expected_error) = vector.expected_error {
        let expected_response = expected_error.into_response();
        assert_eq!(
            Response {
                status_code: expected_response.status(),
                body: None,
                body_string: String::from_utf8(
                    expected_response
                        .collect()
                        .await
                        .unwrap()
                        .to_bytes()
                        .to_vec()
                )
                .unwrap(),
            },
            response,
        );
    } else {
        assert_eq!(StatusCode::OK, response.status_code);
    }
}

tests! {
    runner = create_account_key_validation_test,
    create_reuse_other_account_app: CreateAccountKeyValidationTestVector {
        key_reuses: vec![CreateAccountKeyReuse::OtherAccountApp],
        expected_error: Some(AccountValidationError::AppAuthPubkeyReuseAccount.into()),
    },
    create_reuse_other_recovery_app: CreateAccountKeyValidationTestVector {
        key_reuses: vec![CreateAccountKeyReuse::OtherRecoveryApp],
        expected_error: Some(AccountValidationError::AppAuthPubkeyReuseRecovery.into()),
    },
    create_reuse_other_account_hw: CreateAccountKeyValidationTestVector {
        key_reuses: vec![CreateAccountKeyReuse::OtherAccountHw],
        expected_error: Some(AccountValidationError::HwAuthPubkeyReuseAccount.into()),
    },
    create_reuse_other_recovery_hw: CreateAccountKeyValidationTestVector {
        key_reuses: vec![CreateAccountKeyReuse::OtherRecoveryHw],
        expected_error: Some(AccountValidationError::HwAuthPubkeyReuseRecovery.into()),
    },
    create_reuse_other_account_recovery: CreateAccountKeyValidationTestVector {
        key_reuses: vec![CreateAccountKeyReuse::OtherAccountRecovery],
        expected_error: Some(AccountValidationError::RecoveryAuthPubkeyReuseAccount.into()),
    },
    create_reuse_other_recovery_recovery: CreateAccountKeyValidationTestVector {
        key_reuses: vec![CreateAccountKeyReuse::OtherRecoveryRecovery],
        expected_error: Some(AccountValidationError::RecoveryAuthPubkeyReuseRecovery.into()),
    },
    create_reuse_other_account_auth: CreateAccountKeyValidationTestVector {
        key_reuses: vec![CreateAccountKeyReuse::OtherAccountApp, CreateAccountKeyReuse::OtherAccountHw, CreateAccountKeyReuse::OtherAccountRecovery],
        expected_error: Some(AccountValidationError::AppAuthPubkeyReuseAccount.into()),
    },
    create_reuse_other_account_auth_and_spending: CreateAccountKeyValidationTestVector {
        key_reuses: vec![CreateAccountKeyReuse::OtherAccountApp, CreateAccountKeyReuse::OtherAccountHw, CreateAccountKeyReuse::OtherAccountRecovery, CreateAccountKeyReuse::OtherAccountSpending],
        expected_error: None,
    },
}

struct IdempotentCreateLiteAccountTestVector {
    initial_recovery_pubkey: PublicKey,
    override_recovery_pubkey: Option<PublicKey>,
    expected_create_status: StatusCode,
    expected_same_response: bool,
}

async fn idempotent_create_lite_account_test(vector: IdempotentCreateLiteAccountTestVector) {
    let bootstrap = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let mut recovery_pubkey = vector.initial_recovery_pubkey;
    let first_request = CreateAccountRequest::Lite {
        auth: LiteAccountAuthKeysPayload {
            recovery: recovery_pubkey,
        },
        is_test_account: true,
    };
    let actual_response = client.create_account(&first_request).await;
    assert_eq!(actual_response.status_code, StatusCode::OK);
    let first_create_response = actual_response.body.unwrap();
    recovery_pubkey = if let Some(pubkey) = vector.override_recovery_pubkey {
        pubkey
    } else {
        recovery_pubkey
    };

    let second_request = CreateAccountRequest::Lite {
        auth: LiteAccountAuthKeysPayload {
            recovery: recovery_pubkey,
        },
        is_test_account: true,
    };
    let actual_response = client.create_account(&second_request).await;
    assert_eq!(actual_response.status_code, vector.expected_create_status);
    if actual_response.status_code == StatusCode::OK {
        let second_create_response = actual_response.body.unwrap();
        if vector.expected_same_response {
            assert_eq!(first_create_response, second_create_response);
        } else {
            assert_ne!(first_create_response, second_create_response);
        }
    }
}

tests! {
    runner = idempotent_create_lite_account_test,
    test_idempotent_create_lite_account_with_same_recovery_pubkey: IdempotentCreateLiteAccountTestVector {
        initial_recovery_pubkey: create_pubkey(),
        override_recovery_pubkey: None,
        expected_create_status: StatusCode::OK,
        expected_same_response: true,
    },
}

#[derive(Debug, PartialEq)]
enum UpgradeAccountKeyReuse {
    OtherAccountApp,
    OtherAccountHw,
    OtherRecoveryApp,
    OtherRecoveryHw,
}

#[derive(Debug)]
struct UpgradeAccountTestVector {
    key_reuses: Vec<UpgradeAccountKeyReuse>,
    expected_error: Option<ApiError>,
}

async fn upgrade_account_test(vector: UpgradeAccountTestVector) {
    let bootstrap = gen_services().await;
    let fixed_cur_time = bootstrap.services.recovery_service.cur_time();
    let client = TestClient::new(bootstrap.router).await;

    let account = &create_lite_account(&bootstrap.services, None, true).await;

    let other_account =
        &create_account(&bootstrap.services, AccountNetwork::BitcoinSignet, None).await;

    let other_recovery = &generate_delay_and_notify_recovery(
        other_account.clone().id,
        RecoveryDestination {
            source_auth_keys_id: other_account.common_fields.active_auth_keys_id.clone(),
            app_auth_pubkey: create_pubkey(),
            hardware_auth_pubkey: create_pubkey(),
            recovery_auth_pubkey: Some(create_pubkey()),
        },
        fixed_cur_time + Duration::days(7),
        RecoveryStatus::Pending,
        Factor::Hw,
    );
    bootstrap
        .services
        .recovery_service
        .create(other_recovery)
        .await
        .unwrap();

    let account_app_pubkey = if vector
        .key_reuses
        .contains(&UpgradeAccountKeyReuse::OtherAccountApp)
    {
        other_account.application_auth_pubkey.unwrap()
    } else if vector
        .key_reuses
        .contains(&UpgradeAccountKeyReuse::OtherRecoveryApp)
    {
        other_recovery.destination_app_auth_pubkey.unwrap()
    } else {
        create_pubkey()
    };

    let account_hardware_pubkey = if vector
        .key_reuses
        .contains(&UpgradeAccountKeyReuse::OtherAccountHw)
    {
        other_account.hardware_auth_pubkey
    } else if vector
        .key_reuses
        .contains(&UpgradeAccountKeyReuse::OtherRecoveryHw)
    {
        other_recovery.destination_hardware_auth_pubkey.unwrap()
    } else {
        create_pubkey()
    };

    let spending_keyset = create_spend_keyset(AccountNetwork::BitcoinSignet).0;

    let response = client
        .upgrade_account(
            &account.id.to_string(),
            &UpgradeAccountRequest {
                auth: UpgradeLiteAccountAuthKeysPayload {
                    app: account_app_pubkey,
                    hardware: account_hardware_pubkey,
                },
                spending: SpendingKeysetRequest {
                    network: Network::Signet,
                    app: spending_keyset.app_dpub,
                    hardware: spending_keyset.hardware_dpub,
                },
            },
        )
        .await;

    if let Some(expected_error) = vector.expected_error {
        let expected_response = expected_error.into_response();
        assert_eq!(
            Response {
                status_code: expected_response.status(),
                body: None,
                body_string: String::from_utf8(
                    expected_response
                        .collect()
                        .await
                        .unwrap()
                        .to_bytes()
                        .to_vec()
                )
                .unwrap(),
            },
            response,
        );
    } else {
        assert_eq!(StatusCode::OK, response.status_code);
    }
}

tests! {
    runner = upgrade_account_test,
    upgrade_reuse_other_account_app: UpgradeAccountTestVector {
        key_reuses: vec![UpgradeAccountKeyReuse::OtherAccountApp],
        expected_error: Some(AccountValidationError::AppAuthPubkeyReuseAccount.into()),
    },
    upgrade_reuse_other_recovery_app: UpgradeAccountTestVector {
        key_reuses: vec![UpgradeAccountKeyReuse::OtherRecoveryApp],
        expected_error: Some(AccountValidationError::AppAuthPubkeyReuseRecovery.into()),
    },
    upgrade_reuse_other_account_hw: UpgradeAccountTestVector {
        key_reuses: vec![UpgradeAccountKeyReuse::OtherAccountHw],
        expected_error: Some(AccountValidationError::HwAuthPubkeyReuseAccount.into()),
    },
    upgrade_reuse_other_recovery_hw: UpgradeAccountTestVector {
        key_reuses: vec![UpgradeAccountKeyReuse::OtherRecoveryHw],
        expected_error: Some(AccountValidationError::HwAuthPubkeyReuseRecovery.into()),
    },
    upgrade_reuse_other_account_auth: UpgradeAccountTestVector {
        key_reuses: vec![UpgradeAccountKeyReuse::OtherAccountApp, UpgradeAccountKeyReuse::OtherAccountHw],
        expected_error: Some(AccountValidationError::AppAuthPubkeyReuseAccount.into()),
    },
    upgrade_success: UpgradeAccountTestVector {
        key_reuses: vec![],
        expected_error: None,
    },
}

#[tokio::test]
async fn test_upgrade_account_idempotency() {
    let bootstrap = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let account = &create_lite_account(&bootstrap.services, None, true).await;

    let account_app_pubkey = create_pubkey();
    let account_hardware_pubkey = create_pubkey();
    let spending_keyset = create_spend_keyset(AccountNetwork::BitcoinSignet).0;

    let response = client
        .upgrade_account(
            &account.id.to_string(),
            &UpgradeAccountRequest {
                auth: UpgradeLiteAccountAuthKeysPayload {
                    app: account_app_pubkey,
                    hardware: account_hardware_pubkey,
                },
                spending: SpendingKeysetRequest {
                    network: Network::Signet,
                    app: spending_keyset.app_dpub.clone(),
                    hardware: spending_keyset.hardware_dpub.clone(),
                },
            },
        )
        .await;

    assert_eq!(StatusCode::OK, response.status_code);

    let response_body = response.body.unwrap();

    let response = client
        .upgrade_account(
            &account.id.to_string(),
            &UpgradeAccountRequest {
                auth: UpgradeLiteAccountAuthKeysPayload {
                    app: account_app_pubkey,
                    hardware: account_hardware_pubkey,
                },
                spending: SpendingKeysetRequest {
                    network: Network::Signet,
                    app: spending_keyset.app_dpub,
                    hardware: spending_keyset.hardware_dpub,
                },
            },
        )
        .await;

    assert_eq!(StatusCode::OK, response.status_code);
    assert_eq!(response_body, response.body.unwrap());
}

#[tokio::test]
async fn test_delete_account() {
    let bootstrap = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let lite_account = &create_lite_account(&bootstrap.services, None, true).await;
    let unonboarded_account =
        &create_account(&bootstrap.services, AccountNetwork::BitcoinSignet, None).await;
    let onboarded_account =
        &create_account(&bootstrap.services, AccountNetwork::BitcoinSignet, None).await;

    client
        .complete_onboarding(
            &onboarded_account.id.to_string(),
            &CompleteOnboardingRequest {},
        )
        .await;

    // Can't delete lite account
    let response = client
        .delete_account(
            &lite_account.id.to_string(),
            &CognitoAuthentication::Recovery,
        )
        .await;
    assert_eq!(StatusCode::UNAUTHORIZED, response.status_code);

    // Can't delete account that has completed onboarding
    let response = client
        .delete_account(
            &onboarded_account.id.to_string(),
            &CognitoAuthentication::Wallet {
                is_app_signed: true,
                is_hardware_signed: true,
            },
        )
        .await;
    assert_eq!(StatusCode::CONFLICT, response.status_code);

    // Can't delete account without hw sig
    let response = client
        .delete_account(
            &unonboarded_account.id.to_string(),
            &CognitoAuthentication::Wallet {
                is_app_signed: true,
                is_hardware_signed: false,
            },
        )
        .await;
    assert_eq!(StatusCode::FORBIDDEN, response.status_code);

    // Successful delete
    let response = client
        .delete_account(
            &unonboarded_account.id.to_string(),
            &CognitoAuthentication::Wallet {
                is_app_signed: true,
                is_hardware_signed: true,
            },
        )
        .await;
    assert_eq!(StatusCode::OK, response.status_code);

    // 404 after delete
    let response = client
        .delete_account(
            &unonboarded_account.id.to_string(),
            &CognitoAuthentication::Wallet {
                is_app_signed: true,
                is_hardware_signed: true,
            },
        )
        .await;
    assert_eq!(StatusCode::NOT_FOUND, response.status_code);
}

#[tokio::test]
async fn test_notifications_preferences() {
    let bootstrap = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let account = create_account(&bootstrap.services, Network::Signet.into(), None).await;

    let get_response = client
        .get_notifications_preferences(&account.id.to_string())
        .await;
    assert_eq!(get_response.status_code, StatusCode::OK);
    assert_eq!(
        get_response.body.unwrap(),
        NotificationsPreferences::default()
    );

    let add_phone_response = client
        .add_touchpoint(
            &account.id.to_string(),
            &AccountAddTouchpointRequest::Phone {
                phone_number: "+15555555555".to_string(),
            },
        )
        .await;
    assert_eq!(add_phone_response.status_code, StatusCode::OK);

    let touchpoint_id = add_phone_response.body.unwrap().touchpoint_id.to_string();
    let verify_phone_response = client
        .verify_touchpoint(
            &account.id.to_string(),
            &touchpoint_id,
            &AccountVerifyTouchpointRequest {
                verification_code: "123456".to_string(),
            },
        )
        .await;
    assert_eq!(verify_phone_response.status_code, StatusCode::OK);

    let activate_phone_response = client
        .activate_touchpoint(
            &account.id.to_string(),
            &touchpoint_id,
            &AccountActivateTouchpointRequest {},
            false,
            false,
        )
        .await;
    assert_eq!(activate_phone_response.status_code, StatusCode::OK);

    let get_response = client
        .get_notifications_preferences(&account.id.to_string())
        .await;
    assert_eq!(get_response.status_code, StatusCode::OK);
    assert_eq!(
        get_response.body.unwrap(),
        NotificationsPreferences {
            account_security: HashSet::from([NotificationChannel::Sms]),
            ..Default::default()
        }
    );

    let add_device_token_response = client
        .add_device_token(
            &account.id.to_string(),
            &AccountAddDeviceTokenRequest {
                device_token: "".to_string(),
                platform: TouchpointPlatform::ApnsTeamAlpha,
            },
        )
        .await;
    assert_eq!(add_device_token_response.status_code, StatusCode::OK);

    let get_response = client
        .get_notifications_preferences(&account.id.to_string())
        .await;
    assert_eq!(get_response.status_code, StatusCode::OK);
    assert_eq!(
        get_response.body.unwrap(),
        NotificationsPreferences {
            account_security: HashSet::from([NotificationChannel::Sms, NotificationChannel::Push]),
            ..Default::default()
        }
    );
}
