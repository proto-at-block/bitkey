use std::sync::Arc;
use std::vec;
use std::{env, str::FromStr};

use account::service::{
    tests::{create_descriptor_keys, create_spend_keyset, TestAuthenticationKeys, TestKeypair},
    FetchAccountInput,
};
use authn_authz::routes::{
    AuthRequestKey, AuthenticationRequest, ChallengeResponseParameters, GetTokensRequest,
};
use axum::response::IntoResponse;
use bdk_utils::bdk::bitcoin::key::Secp256k1;
use bdk_utils::bdk::bitcoin::secp256k1::Message;
use bdk_utils::bdk::bitcoin::Network;
use bdk_utils::bdk::miniscript::DescriptorPublicKey;
use bdk_utils::{
    bdk::{
        bitcoin::{hashes::sha256, secp256k1, Amount},
        blockchain::{rpc::Auth, ConfigurableBlockchain, RpcBlockchain, RpcConfig},
        wallet::{wallet_name_from_descriptor, AddressIndex},
        KeychainKind,
    },
    treasury_fund_address,
};
use comms_verification::TEST_CODE;
use errors::ApiError;
use external_identifier::ExternalIdentifier;
use http::StatusCode;
use http_body_util::BodyExt;
use onboarding::account_validation::error::AccountValidationError;
use onboarding::routes::{
    AccountActivateTouchpointRequest, AccountAddDeviceTokenRequest, AccountAddTouchpointRequest,
    AccountVerifyTouchpointRequest, ActivateSpendingKeyDefinitionRequest,
    CompleteOnboardingRequest, ContinueDistributedKeygenRequest, CreateAccountRequest,
    InititateDistributedKeygenRequest, UpgradeAccountRequest,
};
use recovery::entities::{RecoveryDestination, RecoveryStatus};
use serde_json::{json, Value};
use time::{Duration, OffsetDateTime};
use types::account::bitcoin::Network as AccountNetwork;
use types::account::entities::{
    Factor, FullAccountAuthKeysPayload, LiteAccountAuthKeysPayload, SoftwareAccountAuthKeysPayload,
    SpendingKeysetRequest, Touchpoint, TouchpointPlatform, UpgradeLiteAccountAuthKeysPayload,
};
use types::account::identifiers::TouchpointId;
use types::account::AccountType;
use types::consent::Consent;
use types::privileged_action::router::generic::{
    AuthorizationStrategyInput, AuthorizationStrategyOutput, ContinuePrivilegedActionRequest,
    DelayAndNotifyInput, PrivilegedActionInstanceInput, PrivilegedActionInstanceOutput,
    PrivilegedActionRequest, PrivilegedActionResponse,
};
use types::privileged_action::shared::PrivilegedActionInstanceId;
use ulid::Ulid;

use super::{
    gen_services_with_overrides,
    lib::{
        create_account, create_keypair, create_lite_account, generate_delay_and_notify_recovery,
        OffsetClock,
    },
};
use crate::{
    tests,
    tests::{
        gen_services,
        lib::{create_full_account, create_new_authkeys, create_pubkey},
        requests::{axum::TestClient, CognitoAuthentication, Response},
    },
    GenServiceOverrides,
};

struct OnboardingTestVector {
    include_recovery_auth_pubkey: bool,
    spending_app_xpub: DescriptorPublicKey,
    spending_hw_xpub: DescriptorPublicKey,
    network: Network,
    expected_derivation_path: &'static str,
    expected_status: StatusCode,
}

async fn onboarding_test(vector: OnboardingTestVector) {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let keys = create_new_authkeys(&mut context);
    let request = CreateAccountRequest::Full {
        auth: FullAccountAuthKeysPayload {
            app: keys.app.public_key,
            hardware: keys.hw.public_key,
            recovery: if vector.include_recovery_auth_pubkey {
                Some(keys.recovery.public_key)
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
    let actual_response = client.create_account(&mut context, &request).await;
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
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;
    let account = create_full_account(
        &mut context,
        &bootstrap.services,
        types::account::bitcoin::Network::BitcoinSignet,
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
    InitiateActivateTouchpoint {
        use_last_seen_touchpoint_id: bool,
        expected_status: StatusCode,
        expected_num_touchpoints: usize,
        expected_active_touchpoint: bool,
        app_signed: bool,
        hw_signed: bool,
    },
    CompleteActivateTouchpoint {
        use_last_seen_touchpoint_id: bool,
        use_last_seen_privileged_action_instance: bool,
        expected_status: StatusCode,
        expected_num_touchpoints: usize,
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
    account_type: AccountType,
    steps: Vec<TouchpointLifecycleTestStep>,
}

async fn touchpoint_lifecycle_test(vector: TouchpointLifecycleTestVector) {
    let clock = Arc::new(OffsetClock::new());
    let (mut context, bootstrap) =
        gen_services_with_overrides(GenServiceOverrides::new().clock(clock.clone())).await;
    let client = TestClient::new(bootstrap.router).await;
    let account = create_account(&mut context, &bootstrap.services, vector.account_type).await;
    let keys = context
        .get_authentication_keys_for_account_id(account.get_id())
        .unwrap();

    if vector.onboarding_complete {
        assert_eq!(
            client
                .complete_onboarding(&account.get_id().to_string(), &CompleteOnboardingRequest {},)
                .await
                .status_code,
            200,
        );

        let consents = bootstrap
            .services
            .consent_repository
            .fetch_for_account_id(account.get_id())
            .await
            .unwrap();
        assert_eq!(
            consents
                .iter()
                .filter(|c| matches!(c, Consent::OnboardingTosAcceptance(_)))
                .count(),
            1
        );
    }

    let mut last_seen_touchpoint_id = TouchpointId::new(Ulid::default()).unwrap();
    let mut last_seen_privileged_action_instance: Option<PrivilegedActionInstanceOutput> = None;

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

                let actual_response = client
                    .add_touchpoint(&account.get_id().to_string(), &req)
                    .await;

                assert_eq!(actual_response.status_code, expected_status,);

                let account = bootstrap
                    .services
                    .account_service
                    .fetch_account(FetchAccountInput {
                        account_id: account.get_id(),
                    })
                    .await
                    .unwrap();
                assert_eq!(
                    account.get_common_fields().touchpoints.len(),
                    expected_num_touchpoints
                );

                if actual_response.status_code == StatusCode::OK {
                    let touchpoint_id = actual_response.body.unwrap().touchpoint_id;

                    let account = bootstrap
                        .services
                        .account_service
                        .fetch_account(FetchAccountInput {
                            account_id: account.get_id(),
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
                    .verify_touchpoint(
                        &account.get_id().to_string(),
                        &touchpoint_id.to_string(),
                        &req,
                    )
                    .await;

                assert_eq!(actual_response.status_code, expected_status,);

                let account = bootstrap
                    .services
                    .account_service
                    .fetch_account(FetchAccountInput {
                        account_id: account.get_id(),
                    })
                    .await
                    .unwrap();
                assert_eq!(
                    account.get_common_fields().touchpoints.len(),
                    expected_num_touchpoints
                );

                if actual_response.status_code == StatusCode::OK {
                    let account = bootstrap
                        .services
                        .account_service
                        .fetch_account(FetchAccountInput {
                            account_id: account.get_id(),
                        })
                        .await
                        .unwrap();
                    let touchpoint = account.get_touchpoint_by_id(touchpoint_id);

                    assert!(touchpoint.is_some() && !touchpoint.unwrap().get_active());
                }
            }
            TouchpointLifecycleTestStep::InitiateActivateTouchpoint {
                use_last_seen_touchpoint_id,
                expected_status,
                expected_num_touchpoints,
                expected_active_touchpoint,
                app_signed,
                hw_signed,
            } => {
                let touchpoint_id = if use_last_seen_touchpoint_id {
                    last_seen_touchpoint_id.clone()
                } else {
                    TouchpointId::new(Ulid::default()).unwrap()
                };

                let req = PrivilegedActionRequest::Initiate(AccountActivateTouchpointRequest {});
                let actual_response = client
                    .activate_touchpoint(
                        &account.get_id().to_string(),
                        &touchpoint_id.to_string(),
                        &req,
                        app_signed,
                        hw_signed,
                        &keys,
                    )
                    .await;

                assert_eq!(actual_response.status_code, expected_status,);

                let account = bootstrap
                    .services
                    .account_service
                    .fetch_account(FetchAccountInput {
                        account_id: account.get_id(),
                    })
                    .await
                    .unwrap();
                assert_eq!(
                    account.get_common_fields().touchpoints.len(),
                    expected_num_touchpoints
                );

                if actual_response.status_code == StatusCode::OK {
                    if let Some(PrivilegedActionResponse::Pending(pending_response)) =
                        actual_response.body
                    {
                        last_seen_privileged_action_instance =
                            Some(pending_response.privileged_action_instance);
                    }

                    let account = bootstrap
                        .services
                        .account_service
                        .fetch_account(FetchAccountInput {
                            account_id: account.get_id(),
                        })
                        .await
                        .unwrap();
                    let touchpoint = account.get_touchpoint_by_id(touchpoint_id);

                    assert!(
                        touchpoint.is_some()
                            && (touchpoint.unwrap().get_active() || !expected_active_touchpoint)
                    );
                }
            }
            TouchpointLifecycleTestStep::CompleteActivateTouchpoint {
                use_last_seen_touchpoint_id,
                use_last_seen_privileged_action_instance,
                expected_status,
                expected_num_touchpoints,
            } => {
                let touchpoint_id = if use_last_seen_touchpoint_id {
                    last_seen_touchpoint_id.clone()
                } else {
                    TouchpointId::new(Ulid::default()).unwrap()
                };

                let (mut privileged_action_instance_id, mut completion_token) = (
                    PrivilegedActionInstanceId::gen().unwrap(),
                    "INVALID_COMPLETION_TOKEN".to_owned(),
                );
                if use_last_seen_privileged_action_instance {
                    if let Some(last_seen_privileged_action_instance) =
                        &last_seen_privileged_action_instance
                    {
                        let AuthorizationStrategyOutput::DelayAndNotify(delay_and_notify_output) =
                            &last_seen_privileged_action_instance.authorization_strategy
                        else {
                            panic!("Expected DelayAndNotify authorization strategy");
                        };

                        clock.add_offset(
                            delay_and_notify_output.delay_end_time - OffsetDateTime::now_utc(),
                        );

                        (privileged_action_instance_id, completion_token) = (
                            last_seen_privileged_action_instance.id.clone(),
                            delay_and_notify_output.completion_token.clone(),
                        )
                    }
                }

                let req = PrivilegedActionRequest::Continue(ContinuePrivilegedActionRequest {
                    privileged_action_instance: PrivilegedActionInstanceInput {
                        id: privileged_action_instance_id,
                        authorization_strategy: AuthorizationStrategyInput::DelayAndNotify(
                            DelayAndNotifyInput { completion_token },
                        ),
                    },
                });
                let actual_response = client
                    .activate_touchpoint(
                        &account.get_id().to_string(),
                        &touchpoint_id.to_string(),
                        &req,
                        false,
                        false,
                        &keys,
                    )
                    .await;

                assert_eq!(actual_response.status_code, expected_status,);

                let account = bootstrap
                    .services
                    .account_service
                    .fetch_account(FetchAccountInput {
                        account_id: account.get_id(),
                    })
                    .await
                    .unwrap();
                assert_eq!(
                    account.get_common_fields().touchpoints.len(),
                    expected_num_touchpoints
                );

                if actual_response.status_code == StatusCode::OK {
                    let account = bootstrap
                        .services
                        .account_service
                        .fetch_account(FetchAccountInput {
                            account_id: account.get_id(),
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
        account_type: AccountType::Full,
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
            TouchpointLifecycleTestStep::InitiateActivateTouchpoint {
                // Activate unverified touchpoint id fails
                use_last_seen_touchpoint_id: true,
                expected_status: StatusCode::BAD_REQUEST,
                expected_num_touchpoints: 1,
                expected_active_touchpoint: true,
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
            TouchpointLifecycleTestStep::InitiateActivateTouchpoint {
                // Activate non-existing touchpoint id fails
                use_last_seen_touchpoint_id: false,
                expected_status: StatusCode::BAD_REQUEST,
                expected_num_touchpoints: 1,
                expected_active_touchpoint: true,
                app_signed: false,
                hw_signed: false,
            },
            TouchpointLifecycleTestStep::InitiateActivateTouchpoint {
                // Activate verified touchpoint succeeds
                use_last_seen_touchpoint_id: true,
                expected_status: StatusCode::OK,
                expected_num_touchpoints: 1,
                expected_active_touchpoint: true,
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
            TouchpointLifecycleTestStep::InitiateActivateTouchpoint {
                // Activate verified touchpoint succeeds and replaces previous active touchpoint
                use_last_seen_touchpoint_id: true,
                expected_status: StatusCode::OK,
                expected_num_touchpoints: 1,
                expected_active_touchpoint: true,
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
            TouchpointLifecycleTestStep::InitiateActivateTouchpoint {
                // Activate verified touchpoint succeeds
                use_last_seen_touchpoint_id: true,
                expected_status: StatusCode::OK,
                expected_num_touchpoints: 2,
                expected_active_touchpoint: true,
                app_signed: false,
                hw_signed: false,
            },
            TouchpointLifecycleTestStep::InitiateActivateTouchpoint {
                // Re-activate activated touchpoint fails
                use_last_seen_touchpoint_id: true,
                expected_status: StatusCode::BAD_REQUEST,
                expected_num_touchpoints: 2,
                expected_active_touchpoint: true,
                app_signed: false,
                hw_signed: false,
            },
        ],
    },
    test_onboarding_complete: TouchpointLifecycleTestVector {
        onboarding_complete: true,
        account_type: AccountType::Full,
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
            TouchpointLifecycleTestStep::InitiateActivateTouchpoint {
                // Activate verified touchpoint without either sig fails
                use_last_seen_touchpoint_id: true,
                expected_status: StatusCode::FORBIDDEN,
                expected_num_touchpoints: 1,
                expected_active_touchpoint: true,
                app_signed: false,
                hw_signed: false,
            },
            TouchpointLifecycleTestStep::InitiateActivateTouchpoint {
                // Activate verified touchpoint without app sig fails
                use_last_seen_touchpoint_id: true,
                expected_status: StatusCode::FORBIDDEN,
                expected_num_touchpoints: 1,
                expected_active_touchpoint: true,
                app_signed: false,
                hw_signed: true,
            },
            TouchpointLifecycleTestStep::InitiateActivateTouchpoint {
                // Activate verified touchpoint without hw sig fails
                use_last_seen_touchpoint_id: true,
                expected_status: StatusCode::FORBIDDEN,
                expected_num_touchpoints: 1,
                expected_active_touchpoint: true,
                app_signed: true,
                hw_signed: false,
            },
            TouchpointLifecycleTestStep::InitiateActivateTouchpoint {
                // Activate verified touchpoint with both sigs succeeds
                use_last_seen_touchpoint_id: true,
                expected_status: StatusCode::OK,
                expected_num_touchpoints: 1,
                expected_active_touchpoint: true,
                app_signed: true,
                hw_signed: true,
            },
        ],
    },
    test_software_onboarding_not_complete: TouchpointLifecycleTestVector {
        onboarding_complete: false,
        account_type: AccountType::Software,
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
            TouchpointLifecycleTestStep::InitiateActivateTouchpoint {
                use_last_seen_touchpoint_id: true,
                expected_status: StatusCode::OK,
                expected_num_touchpoints: 1,
                expected_active_touchpoint: true,
                app_signed: false,
                hw_signed: false,
            },
        ],
    },
    test_software_onboarding_complete: TouchpointLifecycleTestVector {
        onboarding_complete: true,
        account_type: AccountType::Software,
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
            TouchpointLifecycleTestStep::InitiateActivateTouchpoint {
                use_last_seen_touchpoint_id: true,
                expected_status: StatusCode::OK,
                expected_num_touchpoints: 1,
                app_signed: false,
                expected_active_touchpoint: false,
                hw_signed: false,
            },
            TouchpointLifecycleTestStep::CompleteActivateTouchpoint {
                use_last_seen_touchpoint_id: true,
                use_last_seen_privileged_action_instance: true,
                expected_status: StatusCode::OK,
                expected_num_touchpoints: 1,
            },
        ],
    },
}

#[tokio::test]
async fn test_duplicate_hw_auth_key_fails_onboarding() {
    // If we try to create an account with the same hw auth key twice, that should fail.
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let keys = create_new_authkeys(&mut context);
    let first_request = CreateAccountRequest::Full {
        auth: FullAccountAuthKeysPayload {
            app: keys.app.public_key,
            hardware: keys.hw.public_key,
            recovery: Some(keys.recovery.public_key),
        },
        spending: SpendingKeysetRequest {
            network: Network::Testnet,
            app: DescriptorPublicKey::from_str("[74ce1142/84'/1'/0']tpubD6NzVbkrYhZ4XFo7hggmFF9qDqwrR9aqZv6j2Sgp1N5aVyxyMXxQG14grtRa3ob8ddZqxbd2hbPU7dEXvPRDRuQJ3NsMaGDaZXkLEewdthy/0/*").unwrap(),
            hardware: DescriptorPublicKey::from_str("[74ce1142/84'/1'/0']tpubD6NzVbkrYhZ4XFo7hggmFF9qDqwrR9aqZv6j2Sgp1N5aVyxyMXxQG14grtRa3ob8ddZqxbd2hbPU7dEXvPRDRuQJ3NsMaGDaZXkLEewdthy/0/*").unwrap(),
        },
        is_test_account: true,
    };
    let actual_response = client.create_account(&mut context, &first_request).await;
    assert_eq!(actual_response.status_code, StatusCode::OK,);

    let second_request = CreateAccountRequest::Full {
        auth: FullAccountAuthKeysPayload {
            app: create_pubkey(),
            hardware: keys.hw.public_key,
            recovery: Some(create_pubkey()),
        },
        spending: SpendingKeysetRequest {
            network: Network::Testnet,
            app: DescriptorPublicKey::from_str("[74ce1142/84'/1'/0']tpubD6NzVbkrYhZ4XFo7hggmFF9qDqwrR9aqZv6j2Sgp1N5aVyxyMXxQG14grtRa3ob8ddZqxbd2hbPU7dEXvPRDRuQJ3NsMaGDaZXkLEewdthy/0/*").unwrap(),
            hardware: DescriptorPublicKey::from_str("[74ce1142/84'/1'/0']tpubD6NzVbkrYhZ4XFo7hggmFF9qDqwrR9aqZv6j2Sgp1N5aVyxyMXxQG14grtRa3ob8ddZqxbd2hbPU7dEXvPRDRuQJ3NsMaGDaZXkLEewdthy/0/*").unwrap(),
        },
        is_test_account: true,
    };
    let actual_response = client.create_account(&mut context, &second_request).await;
    assert_eq!(actual_response.status_code, StatusCode::BAD_REQUEST,);
}

#[tokio::test]
async fn test_duplicate_recovery_auth_key_fails_onboarding() {
    // If we try to create an account with the same hw auth key twice, that should fail.
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let keys = create_new_authkeys(&mut context);
    let first_request = CreateAccountRequest::Full {
        auth: FullAccountAuthKeysPayload {
            app: keys.app.public_key,
            hardware: keys.hw.public_key,
            recovery: Some(keys.recovery.public_key),
        },
        spending: SpendingKeysetRequest {
            network: Network::Testnet,
            app: DescriptorPublicKey::from_str("[74ce1142/84'/1'/0']tpubD6NzVbkrYhZ4XFo7hggmFF9qDqwrR9aqZv6j2Sgp1N5aVyxyMXxQG14grtRa3ob8ddZqxbd2hbPU7dEXvPRDRuQJ3NsMaGDaZXkLEewdthy/0/*").unwrap(),
            hardware: DescriptorPublicKey::from_str("[74ce1142/84'/1'/0']tpubD6NzVbkrYhZ4XFo7hggmFF9qDqwrR9aqZv6j2Sgp1N5aVyxyMXxQG14grtRa3ob8ddZqxbd2hbPU7dEXvPRDRuQJ3NsMaGDaZXkLEewdthy/0/*").unwrap(),
        },
        is_test_account: true,
    };
    let actual_response = client.create_account(&mut context, &first_request).await;
    assert_eq!(actual_response.status_code, StatusCode::OK,);

    let new_keys = create_new_authkeys(&mut context);
    let second_request = CreateAccountRequest::Full {
        auth: FullAccountAuthKeysPayload {
            app: new_keys.app.public_key,
            hardware: new_keys.hw.public_key,
            recovery: Some(keys.recovery.public_key),
        },
        spending: SpendingKeysetRequest {
            network: Network::Testnet,
            app: DescriptorPublicKey::from_str("[74ce1142/84'/1'/0']tpubD6NzVbkrYhZ4XFo7hggmFF9qDqwrR9aqZv6j2Sgp1N5aVyxyMXxQG14grtRa3ob8ddZqxbd2hbPU7dEXvPRDRuQJ3NsMaGDaZXkLEewdthy/0/*").unwrap(),
            hardware: DescriptorPublicKey::from_str("[74ce1142/84'/1'/0']tpubD6NzVbkrYhZ4XFo7hggmFF9qDqwrR9aqZv6j2Sgp1N5aVyxyMXxQG14grtRa3ob8ddZqxbd2hbPU7dEXvPRDRuQJ3NsMaGDaZXkLEewdthy/0/*").unwrap(),
        },
        is_test_account: true,
    };
    let actual_response = client.create_account(&mut context, &second_request).await;
    assert_eq!(actual_response.status_code, StatusCode::BAD_REQUEST,);
}

#[tokio::test]
async fn test_idempotent_account_creation() {
    // If we try to create an account with the same hw auth key twice, that should fail.
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let keys = create_new_authkeys(&mut context);
    let first_request = CreateAccountRequest::Full {
        auth: FullAccountAuthKeysPayload {
            app: keys.app.public_key,
            hardware: keys.hw.public_key,
            recovery: Some(keys.recovery.public_key),
        },
        spending: SpendingKeysetRequest {
            network: Network::Testnet,
            app: DescriptorPublicKey::from_str("[74ce1142/84'/1'/0']tpubD6NzVbkrYhZ4XFo7hggmFF9qDqwrR9aqZv6j2Sgp1N5aVyxyMXxQG14grtRa3ob8ddZqxbd2hbPU7dEXvPRDRuQJ3NsMaGDaZXkLEewdthy/0/*").unwrap(),
            hardware: DescriptorPublicKey::from_str("[74ce1142/84'/1'/0']tpubD6NzVbkrYhZ4XFo7hggmFF9qDqwrR9aqZv6j2Sgp1N5aVyxyMXxQG14grtRa3ob8ddZqxbd2hbPU7dEXvPRDRuQJ3NsMaGDaZXkLEewdthy/0/*").unwrap(),
        },
        is_test_account: true,
    };
    let actual_response = client.create_account(&mut context, &first_request).await;
    assert_eq!(actual_response.status_code, StatusCode::OK,);

    let new_keys = create_new_authkeys(&mut context);
    let second_request = CreateAccountRequest::Full {
        auth: FullAccountAuthKeysPayload {
            app: new_keys.app.public_key,
            hardware: keys.hw.public_key,
            recovery: Some(keys.recovery.public_key),
        },
        spending: SpendingKeysetRequest {
            network: Network::Testnet,
            app: DescriptorPublicKey::from_str("[74ce1142/84'/1'/0']tpubD6NzVbkrYhZ4XFo7hggmFF9qDqwrR9aqZv6j2Sgp1N5aVyxyMXxQG14grtRa3ob8ddZqxbd2hbPU7dEXvPRDRuQJ3NsMaGDaZXkLEewdthy/0/*").unwrap(),
            hardware: DescriptorPublicKey::from_str("[74ce1142/84'/1'/0']tpubD6NzVbkrYhZ4XFo7hggmFF9qDqwrR9aqZv6j2Sgp1N5aVyxyMXxQG14grtRa3ob8ddZqxbd2hbPU7dEXvPRDRuQJ3NsMaGDaZXkLEewdthy/0/*").unwrap(),
        },
        is_test_account: true,
    };
    let actual_response = client.create_account(&mut context, &second_request).await;
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
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let keys = create_new_authkeys(&mut context);
    let first_request = CreateAccountRequest::Full {
        auth: FullAccountAuthKeysPayload {
            app: keys.app.public_key,
            hardware: keys.hw.public_key,
            recovery: Some(keys.recovery.public_key),
        },
        spending: SpendingKeysetRequest {
            network: Network::Testnet,
            app: DescriptorPublicKey::from_str("[74ce1142/84'/1'/0']tpubD6NzVbkrYhZ4XFo7hggmFF9qDqwrR9aqZv6j2Sgp1N5aVyxyMXxQG14grtRa3ob8ddZqxbd2hbPU7dEXvPRDRuQJ3NsMaGDaZXkLEewdthy/0/*").unwrap(),
            hardware: DescriptorPublicKey::from_str("[74ce1142/84'/1'/0']tpubD6NzVbkrYhZ4XFo7hggmFF9qDqwrR9aqZv6j2Sgp1N5aVyxyMXxQG14grtRa3ob8ddZqxbd2hbPU7dEXvPRDRuQJ3NsMaGDaZXkLEewdthy/0/*").unwrap(),
        },
        is_test_account: true,
    };
    let actual_response = client.create_account(&mut context, &first_request).await;
    assert_eq!(actual_response.status_code, StatusCode::OK);
    let first_create_response = actual_response.body.unwrap();

    let new_keys = create_new_authkeys(&mut context);
    let hw_pubkey = if vector.same_hardware_pubkey {
        keys.hw.public_key
    } else {
        new_keys.hw.public_key
    };
    let app_pubkey = if vector.same_app_pubkey {
        keys.app.public_key
    } else {
        new_keys.app.public_key
    };
    let recovery_pubkey = if vector.same_recovery_pubkey {
        Some(keys.recovery.public_key)
    } else {
        Some(new_keys.recovery.public_key)
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
    let actual_response = client.create_account(&mut context, &second_request).await;
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
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let keys = create_new_authkeys(&mut context);
    let network = vector.network.into();
    let (_, app_dpub) = create_descriptor_keys(network);
    let (_, hardware_dpub) = create_descriptor_keys(network);
    let first_request = CreateAccountRequest::Full {
        auth: FullAccountAuthKeysPayload {
            app: keys.app.public_key,
            hardware: keys.hw.public_key,
            recovery: Some(keys.recovery.public_key),
        },
        spending: SpendingKeysetRequest {
            network: vector.network,
            app: app_dpub,
            hardware: hardware_dpub,
        },
        is_test_account: vector.test_account,
    };
    let actual_response = client.create_account(&mut context, &first_request).await;
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
    let (mut context, bootstrap) = gen_services().await;
    let fixed_cur_time = bootstrap.services.recovery_service.cur_time();
    let client = TestClient::new(bootstrap.router).await;

    let other_account = &create_full_account(
        &mut context,
        &bootstrap.services,
        AccountNetwork::BitcoinSignet,
        None,
    )
    .await;

    let keys = create_new_authkeys(&mut context);
    let other_recovery = &generate_delay_and_notify_recovery(
        other_account.clone().id,
        RecoveryDestination {
            source_auth_keys_id: other_account.common_fields.active_auth_keys_id.clone(),
            app_auth_pubkey: keys.app.public_key,
            hardware_auth_pubkey: keys.hw.public_key,
            recovery_auth_pubkey: Some(keys.recovery.public_key),
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

    let new_keys = create_new_authkeys(&mut context);
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
        new_keys.app.public_key
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
        new_keys.hw.public_key
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
        new_keys.recovery.public_key
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
        .create_account(
            &mut context,
            &CreateAccountRequest::Full {
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
    override_recovery_pubkey: bool,
    expected_create_status: StatusCode,
    expected_same_response: bool,
}

async fn idempotent_create_lite_account_test(vector: IdempotentCreateLiteAccountTestVector) {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let keys = create_new_authkeys(&mut context);
    let first_request = CreateAccountRequest::Lite {
        auth: LiteAccountAuthKeysPayload {
            recovery: keys.recovery.public_key,
        },
        is_test_account: true,
    };
    let actual_response = client.create_account(&mut context, &first_request).await;
    assert_eq!(actual_response.status_code, StatusCode::OK);
    let first_create_response = actual_response.body.unwrap();
    let recovery_pubkey = if vector.override_recovery_pubkey {
        create_new_authkeys(&mut context).recovery.public_key
    } else {
        keys.recovery.public_key
    };

    let second_request = CreateAccountRequest::Lite {
        auth: LiteAccountAuthKeysPayload {
            recovery: recovery_pubkey,
        },
        is_test_account: true,
    };
    let actual_response = client.create_account(&mut context, &second_request).await;
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
        override_recovery_pubkey: false,
        expected_create_status: StatusCode::OK,
        expected_same_response: true,
    },
    test_idempotent_create_lite_account_with_different_recovery_pubkey: IdempotentCreateLiteAccountTestVector {
        override_recovery_pubkey: true,
        expected_create_status: StatusCode::OK,
        expected_same_response: false,
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
    let (mut context, bootstrap) = gen_services().await;
    let fixed_cur_time = bootstrap.services.recovery_service.cur_time();
    let client = TestClient::new(bootstrap.router).await;

    let account = &create_lite_account(&mut context, &bootstrap.services, None, true).await;
    let lite_account_recovery_keypair = context
        .get_authentication_keys_for_account_id(&account.id)
        .expect("Authentication keys present for Lite Account")
        .recovery;

    let other_account = &create_full_account(
        &mut context,
        &bootstrap.services,
        AccountNetwork::BitcoinSignet,
        None,
    )
    .await;
    let other_account_auth_keys = context
        .get_authentication_keys_for_account_id(&other_account.id)
        .expect("Authentication keys present for Other Account");

    let other_recovery_auth_keys = create_new_authkeys(&mut context);
    let other_recovery = &generate_delay_and_notify_recovery(
        other_account.clone().id,
        RecoveryDestination {
            source_auth_keys_id: other_account.common_fields.active_auth_keys_id.clone(),
            app_auth_pubkey: other_recovery_auth_keys.app.public_key,
            hardware_auth_pubkey: other_recovery_auth_keys.hw.public_key,
            recovery_auth_pubkey: Some(other_recovery_auth_keys.recovery.public_key),
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

    let account_app_keypair = if vector
        .key_reuses
        .contains(&UpgradeAccountKeyReuse::OtherAccountApp)
    {
        other_account_auth_keys.app
    } else if vector
        .key_reuses
        .contains(&UpgradeAccountKeyReuse::OtherRecoveryApp)
    {
        other_recovery_auth_keys.app
    } else {
        let (new_app_auth_seckey, new_app_auth_pubkey) = create_keypair();
        TestKeypair {
            secret_key: new_app_auth_seckey,
            public_key: new_app_auth_pubkey,
        }
    };

    let account_hardware_keypair = if vector
        .key_reuses
        .contains(&UpgradeAccountKeyReuse::OtherAccountHw)
    {
        other_account_auth_keys.hw
    } else if vector
        .key_reuses
        .contains(&UpgradeAccountKeyReuse::OtherRecoveryHw)
    {
        other_recovery_auth_keys.hw
    } else {
        let (new_hw_auth_seckey, new_hw_auth_pubkey) = create_keypair();
        TestKeypair {
            secret_key: new_hw_auth_seckey,
            public_key: new_hw_auth_pubkey,
        }
    };

    context.add_authentication_keys(TestAuthenticationKeys {
        app: account_app_keypair.clone(),
        hw: account_hardware_keypair.clone(),
        recovery: lite_account_recovery_keypair,
    });
    let spending_keyset = create_spend_keyset(AccountNetwork::BitcoinSignet).0;

    let response = client
        .upgrade_account(
            &mut context,
            &account.id.to_string(),
            &UpgradeAccountRequest {
                auth: UpgradeLiteAccountAuthKeysPayload {
                    app: account_app_keypair.public_key,
                    hardware: account_hardware_keypair.public_key,
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
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let account = &create_lite_account(&mut context, &bootstrap.services, None, true).await;
    let lite_account_keys = context
        .get_authentication_keys_for_account_id(&account.id)
        .expect("Lite account keys not found");

    let new_keys = create_new_authkeys(&mut context);
    context.add_authentication_keys(TestAuthenticationKeys {
        app: new_keys.app.clone(),
        hw: new_keys.hw.clone(),
        recovery: lite_account_keys.recovery,
    });
    let spending_keyset = create_spend_keyset(AccountNetwork::BitcoinSignet).0;

    let response = client
        .upgrade_account(
            &mut context,
            &account.id.to_string(),
            &UpgradeAccountRequest {
                auth: UpgradeLiteAccountAuthKeysPayload {
                    app: new_keys.app.public_key,
                    hardware: new_keys.hw.public_key,
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
            &mut context,
            &account.id.to_string(),
            &UpgradeAccountRequest {
                auth: UpgradeLiteAccountAuthKeysPayload {
                    app: new_keys.app.public_key,
                    hardware: new_keys.hw.public_key,
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
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let lite_account = &create_lite_account(&mut context, &bootstrap.services, None, true).await;
    let lite_account_keys = context
        .get_authentication_keys_for_account_id(&lite_account.id)
        .unwrap();
    let unonboarded_account = &create_full_account(
        &mut context,
        &bootstrap.services,
        AccountNetwork::BitcoinSignet,
        None,
    )
    .await;
    let unonboarded_keys = context
        .get_authentication_keys_for_account_id(&unonboarded_account.id)
        .unwrap();
    let onboarded_account = &create_full_account(
        &mut context,
        &bootstrap.services,
        AccountNetwork::BitcoinSignet,
        None,
    )
    .await;
    let onboarded_keys = context
        .get_authentication_keys_for_account_id(&onboarded_account.id)
        .unwrap();

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
            &lite_account_keys,
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
            &onboarded_keys,
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
            &unonboarded_keys,
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
            &unonboarded_keys,
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
            &unonboarded_keys,
        )
        .await;
    assert_eq!(StatusCode::NOT_FOUND, response.status_code);
}

#[tokio::test]
async fn test_revoked_access_token_add_push_touchpoint() {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let keys = create_new_authkeys(&mut context);
    let ((_, app_xpub), (_, hw_xpub)) = (
        create_descriptor_keys(Network::Signet.into()),
        create_descriptor_keys(Network::Signet.into()),
    );

    // First, make an account
    let request = CreateAccountRequest::Full {
        auth: FullAccountAuthKeysPayload {
            app: keys.app.public_key,
            hardware: keys.hw.public_key,
            recovery: None,
        },
        spending: SpendingKeysetRequest {
            network: Network::Signet,
            app: app_xpub,
            hardware: hw_xpub,
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

    // Authenticate
    let request = AuthenticationRequest {
        auth_request_key: AuthRequestKey::AppPubkey(keys.app.public_key),
    };
    let actual_response = client.authenticate(&request).await;
    assert_eq!(
        actual_response.status_code,
        StatusCode::OK,
        "{}",
        actual_response.body_string
    );
    let auth_resp = actual_response.body.unwrap();
    let challenge = auth_resp.challenge;
    let secp = Secp256k1::new();
    let message = Message::from_hashed_data::<sha256::Hash>(challenge.as_ref());
    let signature = secp.sign_ecdsa(&message, &keys.app.secret_key);
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
        actual_response.status_code,
        StatusCode::OK,
        "{}",
        actual_response.body_string
    );

    let account_id = auth_resp.account_id;
    let access_token = actual_response.body.unwrap().access_token;

    let response = client
        .add_device_token_with_access_token(
            &account_id.to_string(),
            &AccountAddDeviceTokenRequest {
                device_token: "test-token-1".to_string(),
                platform: TouchpointPlatform::ApnsTeamAlpha,
            },
            &access_token,
        )
        .await;
    assert_eq!(StatusCode::OK, response.status_code);

    // Rotate keys, which logs user out
    bootstrap
        .services
        .userpool_service
        .create_or_update_account_users_if_necessary(
            &account_id,
            Some(create_pubkey()),
            Some(create_pubkey()),
            None,
        )
        .await
        .unwrap();

    // Adding new token errors
    let response = client
        .add_device_token_with_access_token(
            &account_id.to_string(),
            &AccountAddDeviceTokenRequest {
                device_token: "test-token-2".to_string(),
                platform: TouchpointPlatform::ApnsTeamAlpha,
            },
            &access_token,
        )
        .await;
    assert_eq!(StatusCode::UNAUTHORIZED, response.status_code);

    // Adding existing token doesn't error
    let response = client
        .add_device_token_with_access_token(
            &account_id.to_string(),
            &AccountAddDeviceTokenRequest {
                device_token: "test-token-1".to_string(),
                platform: TouchpointPlatform::ApnsTeamAlpha,
            },
            &access_token,
        )
        .await;
    assert_eq!(StatusCode::OK, response.status_code);
}

struct IdempotentCreateSoftwareAccountTestVector {
    override_app_pubkey: bool,
    override_recovery_pubkey: bool,
    expected_create_status: StatusCode,
    expected_same_response: bool,
}

async fn idempotent_create_software_account_test(
    vector: IdempotentCreateSoftwareAccountTestVector,
) {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let keys = create_new_authkeys(&mut context);
    let first_request = CreateAccountRequest::Software {
        auth: SoftwareAccountAuthKeysPayload {
            app: keys.app.public_key,
            recovery: keys.recovery.public_key,
        },
        is_test_account: true,
    };
    let actual_response = client.create_account(&mut context, &first_request).await;
    assert_eq!(actual_response.status_code, StatusCode::OK);
    let first_create_response = actual_response.body.unwrap();
    let app_pubkey = if vector.override_app_pubkey {
        create_new_authkeys(&mut context).app.public_key
    } else {
        keys.app.public_key
    };
    let recovery_pubkey = if vector.override_recovery_pubkey {
        create_new_authkeys(&mut context).recovery.public_key
    } else {
        keys.recovery.public_key
    };

    let second_request = CreateAccountRequest::Software {
        auth: SoftwareAccountAuthKeysPayload {
            app: app_pubkey,
            recovery: recovery_pubkey,
        },
        is_test_account: true,
    };
    let actual_response = client.create_account(&mut context, &second_request).await;
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
    runner = idempotent_create_software_account_test,
    test_idempotent_create_software_account_with_same_keys: IdempotentCreateSoftwareAccountTestVector {
        override_app_pubkey: false,
        override_recovery_pubkey: false,
        expected_create_status: StatusCode::OK,
        expected_same_response: true,
    },
    test_idempotent_create_software_account_with_different_app_pubkey: IdempotentCreateSoftwareAccountTestVector {
        override_app_pubkey: true,
        override_recovery_pubkey: false,
        expected_create_status: StatusCode::BAD_REQUEST,
        expected_same_response: false,
    },
    test_idempotent_create_software_account_with_different_recovery_pubkey: IdempotentCreateSoftwareAccountTestVector {
        override_app_pubkey: false,
        override_recovery_pubkey: true,
        expected_create_status: StatusCode::BAD_REQUEST,
        expected_same_response: false,
    },
    test_idempotent_create_software_account_with_different_both_pubkeys: IdempotentCreateSoftwareAccountTestVector {
        override_app_pubkey: true,
        override_recovery_pubkey: true,
        expected_create_status: StatusCode::OK,
        expected_same_response: false,
    },
}

#[tokio::test]
async fn software_onboarding_keygen_activation_test() {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let keys = create_new_authkeys(&mut context);
    let request = CreateAccountRequest::Software {
        auth: SoftwareAccountAuthKeysPayload {
            app: keys.app.public_key,
            recovery: keys.recovery.public_key,
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

    let (app_share_package, sealed_request) = {
        // Fake app
        use base64::{engine::general_purpose::STANDARD as b64, Engine as _};
        use crypto::frost::dkg::app::initiate_dkg;

        let initiate_result = initiate_dkg().unwrap();

        let request = json!(
            {
                "app_share_package": initiate_result.share_package_for_peer,
            }
        );

        let request_bytes = serde_json::to_vec(&request).unwrap();

        (initiate_result, b64.encode(request_bytes))
    };

    let request = InititateDistributedKeygenRequest {
        network: Network::Signet,
        sealed_request,
    };
    let actual_response = client
        .initiate_distributed_keygen(&account_id.to_string(), &request)
        .await;
    assert_eq!(
        actual_response.status_code,
        StatusCode::OK,
        "{}",
        actual_response.body_string
    );

    let body = actual_response.body.unwrap();
    let key_definition_id = body.key_definition_id;

    let sealed_request = {
        // Fake app
        use base64::{engine::general_purpose::STANDARD as b64, Engine as _};
        use crypto::frost::dkg::{app::continue_dkg, SharePackage};
        use crypto::frost::KeyCommitments;

        let response_bytes = b64.decode(body.sealed_response.as_bytes()).unwrap();
        let response = serde_json::from_slice::<Value>(&response_bytes).unwrap();

        let server_share_package_value = response.get("server_share_package").unwrap();
        let server_share_package =
            serde_json::from_value::<SharePackage>(server_share_package_value.clone()).unwrap();

        let server_key_commitments_value = response.get("server_key_commitments").unwrap();
        let server_key_commitments =
            serde_json::from_value::<KeyCommitments>(server_key_commitments_value.clone()).unwrap();

        let continue_result = continue_dkg(
            &app_share_package.share_package,
            &server_share_package,
            &server_key_commitments,
        )
        .unwrap();

        let request = json!(
            {
                "app_key_commitments": continue_result.key_commitments,
            }
        );

        let request_bytes = serde_json::to_vec(&request).unwrap();

        b64.encode(request_bytes)
    };

    let request = ContinueDistributedKeygenRequest { sealed_request };
    let actual_response = client
        .continue_distributed_keygen(
            &account_id.to_string(),
            &key_definition_id.to_string(),
            &request,
        )
        .await;
    assert_eq!(
        actual_response.status_code,
        StatusCode::OK,
        "{}",
        actual_response.body_string
    );

    // TODO: Figure out what to do with get_account_status: we need to figure out how to deal with null keyset without breaking
    // existing consumers of this endpoint
    let actual_response = client.get_account_status(&account_id.to_string()).await;
    assert_eq!(
        actual_response.status_code,
        StatusCode::INTERNAL_SERVER_ERROR,
        "{}",
        actual_response.body_string
    );

    let request = ActivateSpendingKeyDefinitionRequest { key_definition_id };
    let actual_response = client
        .activate_spending_key_definition(&account_id.to_string(), &request)
        .await;
    assert_eq!(
        actual_response.status_code,
        StatusCode::OK,
        "{}",
        actual_response.body_string
    );
}

#[test]
fn test_create_bdk_wallet() {
    let (_, wallet) = create_spend_keyset(types::account::bitcoin::Network::BitcoinRegtest);
    treasury_fund_address(
        &wallet.get_address(AddressIndex::New).unwrap(),
        Amount::from_sat(50_000),
    );

    let rpc_config = RpcConfig {
        url: env::var("REGTEST_ELECTRUM_SERVER_URI").unwrap_or("127.0.0.1:18443".to_string()),
        auth: Auth::UserPass {
            username: "test".to_string(),
            password: "test".to_string(),
        },
        network: Network::Regtest,
        wallet_name: wallet_name_from_descriptor(
            wallet
                .public_descriptor(KeychainKind::External)
                .unwrap()
                .unwrap(),
            Some(
                wallet
                    .public_descriptor(KeychainKind::Internal)
                    .unwrap()
                    .unwrap(),
            ),
            Network::Regtest,
            &secp256k1::Secp256k1::new(),
        )
        .unwrap(),
        sync_params: None,
    };
    let blockchain = RpcBlockchain::from_config(&rpc_config).unwrap();
    wallet.sync(&blockchain, Default::default()).unwrap();

    assert_eq!(wallet.get_balance().unwrap().untrusted_pending, 50_000);
}
