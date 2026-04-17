use account::service::tests::create_private_spend_keyset;
use axum::response::IntoResponse;
use bdk_utils::bdk::bitcoin::Network;
use comms_verification::TEST_CODE;
use errors::ApiError;
use http::StatusCode;
use http_body_util::BodyExt;
use onboarding::{
    account_validation::error::AccountValidationError,
    routes::{
        AccountActivateTouchpointRequest, AccountAddTouchpointRequest,
        AccountVerifyTouchpointRequest, CompleteOnboardingRequest, CompleteOnboardingRequestV2,
        RotateSpendingKeysetRequest,
    },
    routes_v2::{CreateAccountRequestV2, UpgradeAccountRequestV2},
};
use recovery::entities::{RecoveryDestination, RecoveryStatus};
use rstest::rstest;
use time::Duration;
use types::{
    account::{
        bitcoin::Network as AccountNetwork,
        entities::{
            v2::{
                FullAccountAuthKeysInputV2, SpendingKeysetInputV2,
                UpgradeLiteAccountAuthKeysInputV2,
            },
            DescriptorBackup, DescriptorBackupsSet, Factor, HardwareType,
        },
    },
    privileged_action::router::generic::PrivilegedActionRequest,
};

use crate::tests::{
    gen_services,
    lib::{
        create_email_touchpoint, create_full_account, create_full_account_v2, create_lite_account,
        create_new_authkeys, create_onboarded_w3_account, create_pubkey,
        generate_delay_and_notify_recovery, rotate_auth_keys_with_hardware_type,
    },
    requests::{axum::TestClient, Response},
    TestContext,
};
use account::service::tests::TestAuthenticationKeys;

#[tokio::test]
async fn create_account_v2_test_with_idempotency() {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let (spending_app_pub, spending_hw_pub) = (create_pubkey(), create_pubkey());
    let keys = create_new_authkeys(&mut context);
    let request = CreateAccountRequestV2 {
        auth: FullAccountAuthKeysInputV2 {
            app_pub: keys.app.public_key,
            hardware_pub: keys.hw.public_key,
            recovery_pub: keys.recovery.public_key,
            hardware_type: HardwareType::default(),
        },
        spend: SpendingKeysetInputV2 {
            network: Network::Signet,
            app_pub: spending_app_pub,
            hardware_pub: spending_hw_pub,
        },
        is_test_account: true,
    };
    let actual_response = client.create_account_v2(&mut context, &request).await;
    assert_eq!(actual_response.status_code, StatusCode::OK);
    let first_create_response = actual_response.body.unwrap();

    assert!(!first_create_response.server_pub_integrity_sig.is_empty());

    let actual_response = client.create_account_v2(&mut context, &request).await;
    assert_eq!(actual_response.status_code, StatusCode::OK);
    assert_eq!(first_create_response, actual_response.body.unwrap());

    // Complete onboarding
    let actual_response = client
        .complete_onboarding(
            &first_create_response.account_id.to_string(),
            &CompleteOnboardingRequest {},
        )
        .await;
    assert_eq!(actual_response.status_code, StatusCode::CONFLICT);

    let actual_response = client
        .update_descriptor_backups(
            &first_create_response.account_id.to_string(),
            &DescriptorBackupsSet {
                wrapped_ssek: vec![],
                descriptor_backups: vec![DescriptorBackup::Private {
                    keyset_id: first_create_response.keyset_id,
                    sealed_descriptor: "".to_string(),
                    sealed_server_root_xpub: "".to_string(),
                }],
            },
            Some(&keys),
        )
        .await;
    assert_eq!(actual_response.status_code, StatusCode::OK);

    let actual_response = client
        .complete_onboarding(
            &first_create_response.account_id.to_string(),
            &CompleteOnboardingRequest {},
        )
        .await;
    assert_eq!(actual_response.status_code, StatusCode::OK);
}

#[tokio::test]
async fn upgrade_account_v2_test_with_idempotency() {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let account = &create_lite_account(&mut context, &bootstrap.services, None, true).await;

    let (spending_app_pub, spending_hw_pub) = (create_pubkey(), create_pubkey());
    let keys = create_new_authkeys(&mut context);
    let request = UpgradeAccountRequestV2 {
        auth: UpgradeLiteAccountAuthKeysInputV2 {
            app_pub: keys.app.public_key,
            hardware_pub: keys.hw.public_key,
            hardware_type: HardwareType::default(),
        },
        spend: SpendingKeysetInputV2 {
            network: Network::Signet,
            app_pub: spending_app_pub,
            hardware_pub: spending_hw_pub,
        },
    };
    let actual_response = client
        .upgrade_account_v2(&mut context, &account.id.to_string(), &request)
        .await;
    assert_eq!(actual_response.status_code, StatusCode::OK);
    let first_upgrade_response = actual_response.body.unwrap();

    assert!(!first_upgrade_response.server_pub_integrity_sig.is_empty());

    let actual_response = client
        .upgrade_account_v2(&mut context, &account.id.to_string(), &request)
        .await;
    assert_eq!(actual_response.status_code, StatusCode::OK);
    assert_eq!(first_upgrade_response, actual_response.body.unwrap());
}

#[tokio::test]
async fn create_keyset_v2_test_with_idempotency() {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let (spending_app_pub, spending_hw_pub) = (create_pubkey(), create_pubkey());
    let keys = create_new_authkeys(&mut context);
    let request = CreateAccountRequestV2 {
        auth: FullAccountAuthKeysInputV2 {
            app_pub: keys.app.public_key,
            hardware_pub: keys.hw.public_key,
            recovery_pub: keys.recovery.public_key,
            hardware_type: HardwareType::default(),
        },
        spend: SpendingKeysetInputV2 {
            network: Network::Signet,
            app_pub: spending_app_pub,
            hardware_pub: spending_hw_pub,
        },
        is_test_account: true,
    };
    let actual_response = client.create_account_v2(&mut context, &request).await;
    assert_eq!(actual_response.status_code, StatusCode::OK);
    let create_response = actual_response.body.unwrap();

    let (spending_app_pub, spending_hw_pub) = (create_pubkey(), create_pubkey());
    let actual_response = client
        .create_keyset_v2(
            &create_response.account_id.to_string(),
            &SpendingKeysetInputV2 {
                network: Network::Signet,
                app_pub: spending_app_pub,
                hardware_pub: spending_hw_pub,
            },
        )
        .await;
    assert_eq!(actual_response.status_code, StatusCode::OK);
    let first_create_response = actual_response.body.unwrap();

    let actual_response = client
        .create_keyset_v2(
            &create_response.account_id.to_string(),
            &SpendingKeysetInputV2 {
                network: Network::Signet,
                app_pub: spending_app_pub,
                hardware_pub: spending_hw_pub,
            },
        )
        .await;
    assert_eq!(actual_response.status_code, StatusCode::OK);
    assert_eq!(first_create_response, actual_response.body.unwrap());

    // Rotate
    let actual_response = client
        .rotate_to_spending_keyset(
            &create_response.account_id.to_string(),
            &create_response.keyset_id.to_string(),
            &RotateSpendingKeysetRequest {},
            &keys,
        )
        .await;
    assert_eq!(actual_response.status_code, StatusCode::CONFLICT);

    let actual_response = client
        .update_descriptor_backups(
            &create_response.account_id.to_string(),
            &DescriptorBackupsSet {
                wrapped_ssek: vec![],
                descriptor_backups: vec![DescriptorBackup::Private {
                    keyset_id: create_response.keyset_id.clone(),
                    sealed_descriptor: "".to_string(),
                    sealed_server_root_xpub: "".to_string(),
                }],
            },
            Some(&keys),
        )
        .await;
    assert_eq!(actual_response.status_code, StatusCode::OK);

    let actual_response = client
        .rotate_to_spending_keyset(
            &create_response.account_id.to_string(),
            &create_response.keyset_id.to_string(),
            &RotateSpendingKeysetRequest {},
            &keys,
        )
        .await;
    assert_eq!(actual_response.status_code, StatusCode::OK);
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

async fn create_account_v2_key_validation_test(
    key_reuses: Vec<CreateAccountKeyReuse>,
    expected_error: Option<ApiError>,
) {
    let (mut context, bootstrap) = gen_services().await;
    let fixed_cur_time = bootstrap.services.recovery_service.cur_time();
    let client = TestClient::new(bootstrap.router).await;

    let other_account = &create_full_account_v2(
        &mut context,
        &bootstrap.services,
        types::account::bitcoin::Network::BitcoinSignet,
        None,
    )
    .await;

    let keys = create_new_authkeys(&mut context);
    let other_recovery = &&generate_delay_and_notify_recovery(
        other_account.clone().id,
        RecoveryDestination {
            source_auth_keys_id: other_account.common_fields.active_auth_keys_id.clone(),
            app_auth_pubkey: keys.app.public_key,
            hardware_auth_pubkey: keys.hw.public_key,
            recovery_auth_pubkey: Some(keys.recovery.public_key),
            hardware_type: HardwareType::default(),
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
    let account_app_pubkey = if key_reuses.contains(&CreateAccountKeyReuse::OtherAccountApp) {
        other_account.application_auth_pubkey.unwrap()
    } else if key_reuses.contains(&CreateAccountKeyReuse::OtherRecoveryApp) {
        other_recovery.destination_app_auth_pubkey.unwrap()
    } else {
        new_keys.app.public_key
    };

    let account_hardware_pubkey = if key_reuses.contains(&CreateAccountKeyReuse::OtherAccountHw) {
        other_account.hardware_auth_pubkey
    } else if key_reuses.contains(&CreateAccountKeyReuse::OtherRecoveryHw) {
        other_recovery.destination_hardware_auth_pubkey.unwrap()
    } else {
        new_keys.hw.public_key
    };

    let account_recovery_pubkey =
        if key_reuses.contains(&CreateAccountKeyReuse::OtherAccountRecovery) {
            other_account.common_fields.recovery_auth_pubkey.unwrap()
        } else if key_reuses.contains(&CreateAccountKeyReuse::OtherRecoveryRecovery) {
            other_recovery.destination_recovery_auth_pubkey.unwrap()
        } else {
            new_keys.recovery.public_key
        };

    let spending_keyset = if key_reuses.contains(&CreateAccountKeyReuse::OtherAccountSpending) {
        other_account.active_spending_keyset().unwrap().clone()
    } else {
        create_private_spend_keyset(types::account::bitcoin::Network::BitcoinSignet)
    }
    .optional_private_multi_sig()
    .unwrap()
    .clone();

    let response = client
        .create_account_v2(
            &mut context,
            &CreateAccountRequestV2 {
                auth: FullAccountAuthKeysInputV2 {
                    app_pub: account_app_pubkey,
                    hardware_pub: account_hardware_pubkey,
                    recovery_pub: account_recovery_pubkey,
                    hardware_type: HardwareType::default(),
                },
                spend: SpendingKeysetInputV2 {
                    network: Network::Signet,
                    app_pub: spending_keyset.app_pub,
                    hardware_pub: spending_keyset.hardware_pub,
                },
                is_test_account: true,
            },
        )
        .await;

    if let Some(expected_error) = expected_error {
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

#[rstest]
#[case::create_reuse_other_account_app(
    vec![CreateAccountKeyReuse::OtherAccountApp],
    Some(AccountValidationError::AppAuthPubkeyReuseAccount.into())
)]
#[case::create_reuse_other_recovery_app(
    vec![CreateAccountKeyReuse::OtherRecoveryApp],
    Some(AccountValidationError::AppAuthPubkeyReuseRecovery.into())
)]
#[case::create_reuse_other_account_hw(
    vec![CreateAccountKeyReuse::OtherAccountHw],
    Some(AccountValidationError::HwAuthPubkeyReuseAccount.into())
)]
#[case::create_reuse_other_recovery_hw(
    vec![CreateAccountKeyReuse::OtherRecoveryHw],
    Some(AccountValidationError::HwAuthPubkeyReuseRecovery.into())
)]
#[case::create_reuse_other_account_recovery(
    vec![CreateAccountKeyReuse::OtherAccountRecovery],
    Some(AccountValidationError::RecoveryAuthPubkeyReuseAccount.into())
)]
#[case::create_reuse_other_recovery_recovery(
    vec![CreateAccountKeyReuse::OtherRecoveryRecovery],
    Some(AccountValidationError::RecoveryAuthPubkeyReuseRecovery.into())
)]
#[case::create_reuse_other_account_auth(
    vec![CreateAccountKeyReuse::OtherAccountApp, CreateAccountKeyReuse::OtherAccountHw, CreateAccountKeyReuse::OtherAccountRecovery],
    Some(AccountValidationError::AppAuthPubkeyReuseAccount.into())
)]
#[case::create_reuse_other_account_auth_and_spending(
    vec![CreateAccountKeyReuse::OtherAccountApp, CreateAccountKeyReuse::OtherAccountHw, CreateAccountKeyReuse::OtherAccountRecovery, CreateAccountKeyReuse::OtherAccountSpending],
    None
)]
#[tokio::test]
async fn test_create_account_key_validation(
    #[case] key_reuses: Vec<CreateAccountKeyReuse>,
    #[case] expected_error: Option<ApiError>,
) {
    create_account_v2_key_validation_test(key_reuses, expected_error).await
}

#[tokio::test]
#[ignore] // Requires WSM server with sign-public-keys endpoint deployed
async fn test_complete_onboarding_v2_success() {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    // Create account with private keyset
    let (spending_app_pub, spending_hw_pub) = (create_pubkey(), create_pubkey());
    let keys = create_new_authkeys(&mut context);
    let request = CreateAccountRequestV2 {
        auth: FullAccountAuthKeysInputV2 {
            app_pub: keys.app.public_key,
            hardware_pub: keys.hw.public_key,
            recovery_pub: keys.recovery.public_key,
            hardware_type: HardwareType::default(),
        },
        spend: SpendingKeysetInputV2 {
            network: Network::Signet,
            app_pub: spending_app_pub,
            hardware_pub: spending_hw_pub,
        },
        is_test_account: true,
    };
    let create_response = client.create_account_v2(&mut context, &request).await;
    assert_eq!(create_response.status_code, StatusCode::OK);
    let account = create_response.body.unwrap();

    // Add and activate email touchpoint using service layer
    create_email_touchpoint(&bootstrap.services, &account.account_id, true).await;

    // Add descriptor backup
    let backup_response = client
        .update_descriptor_backups(
            &account.account_id.to_string(),
            &DescriptorBackupsSet {
                wrapped_ssek: vec![],
                descriptor_backups: vec![DescriptorBackup::Private {
                    keyset_id: account.keyset_id.clone(),
                    sealed_descriptor: "sealed_descriptor".to_string(),
                    sealed_server_root_xpub: "sealed_xpub".to_string(),
                }],
            },
            Some(&keys),
        )
        .await;
    assert_eq!(backup_response.status_code, StatusCode::OK);

    // Complete onboarding v2
    let onboarding_response = client
        .complete_onboarding_v2(
            &account.account_id.to_string(),
            &onboarding::routes::CompleteOnboardingRequestV2 {},
            Some(&keys),
        )
        .await;
    assert_eq!(onboarding_response.status_code, StatusCode::OK);

    let body = onboarding_response.body.unwrap();

    // Verify all auth keys are returned
    assert!(!body.app_auth_pub.is_empty());
    assert!(!body.hardware_auth_pub.is_empty());
    assert_eq!(body.app_auth_pub, keys.app.public_key.to_string());
    assert_eq!(body.hardware_auth_pub, keys.hw.public_key.to_string());

    // Verify all spending keys are returned
    assert!(!body.app_spending_pub.is_empty());
    assert!(!body.hardware_spending_pub.is_empty());
    assert!(!body.server_spending_pub.is_empty());
    assert_eq!(body.app_spending_pub, spending_app_pub.to_string());
    assert_eq!(body.hardware_spending_pub, spending_hw_pub.to_string());

    // Verify signature is returned and valid hex
    assert!(!body.signature.is_empty());
    assert_eq!(body.signature.len(), 128);
    assert!(body.signature.chars().all(|c| c.is_ascii_hexdigit()));

    // Test idempotency - calling again should succeed
    let onboarding_response2 = client
        .complete_onboarding_v2(
            &account.account_id.to_string(),
            &onboarding::routes::CompleteOnboardingRequestV2 {},
            Some(&keys),
        )
        .await;
    assert_eq!(onboarding_response2.status_code, StatusCode::OK);

    let body2 = onboarding_response2.body.unwrap();
    // Keys should be the same, signature may differ (ECDSA is non-deterministic)
    assert_eq!(body.app_auth_pub, body2.app_auth_pub);
    assert_eq!(body.hardware_auth_pub, body2.hardware_auth_pub);
    assert_eq!(body.app_spending_pub, body2.app_spending_pub);
    assert_eq!(body.hardware_spending_pub, body2.hardware_spending_pub);
    assert_eq!(body.server_spending_pub, body2.server_spending_pub);
}

#[tokio::test]
async fn test_complete_onboarding_v2_missing_descriptor_backup() {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    // Create account with private keyset
    let (spending_app_pub, spending_hw_pub) = (create_pubkey(), create_pubkey());
    let keys = create_new_authkeys(&mut context);
    let request = CreateAccountRequestV2 {
        auth: FullAccountAuthKeysInputV2 {
            app_pub: keys.app.public_key,
            hardware_pub: keys.hw.public_key,
            recovery_pub: keys.recovery.public_key,
            hardware_type: HardwareType::default(),
        },
        spend: SpendingKeysetInputV2 {
            network: Network::Signet,
            app_pub: spending_app_pub,
            hardware_pub: spending_hw_pub,
        },
        is_test_account: true,
    };
    let create_response = client.create_account_v2(&mut context, &request).await;
    assert_eq!(create_response.status_code, StatusCode::OK);
    let account = create_response.body.unwrap();

    // Add and activate email touchpoint using service layer
    create_email_touchpoint(&bootstrap.services, &account.account_id, true).await;

    // Try to complete onboarding without descriptor backup
    let onboarding_response = client
        .complete_onboarding_v2(
            &account.account_id.to_string(),
            &onboarding::routes::CompleteOnboardingRequestV2 {},
            Some(&keys),
        )
        .await;

    // Should fail with bad request
    assert_eq!(onboarding_response.status_code, StatusCode::BAD_REQUEST);
    let error_body = onboarding_response.body_as_string().await;
    assert!(error_body.contains("descriptor backup"));
}

#[tokio::test]
#[ignore] // Requires WSM server with sign-public-keys endpoint deployed
async fn test_complete_onboarding_v2_test_account_missing_email_allowed() {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    // Create TEST account with private keyset
    let (spending_app_pub, spending_hw_pub) = (create_pubkey(), create_pubkey());
    let keys = create_new_authkeys(&mut context);
    let request = CreateAccountRequestV2 {
        auth: FullAccountAuthKeysInputV2 {
            app_pub: keys.app.public_key,
            hardware_pub: keys.hw.public_key,
            recovery_pub: keys.recovery.public_key,
            hardware_type: HardwareType::default(),
        },
        spend: SpendingKeysetInputV2 {
            network: Network::Signet,
            app_pub: spending_app_pub,
            hardware_pub: spending_hw_pub,
        },
        is_test_account: true,
    };
    let create_response = client.create_account_v2(&mut context, &request).await;
    assert_eq!(create_response.status_code, StatusCode::OK);
    let account = create_response.body.unwrap();

    // Add descriptor backup
    let backup_response = client
        .update_descriptor_backups(
            &account.account_id.to_string(),
            &DescriptorBackupsSet {
                wrapped_ssek: vec![],
                descriptor_backups: vec![DescriptorBackup::Private {
                    keyset_id: account.keyset_id.clone(),
                    sealed_descriptor: "sealed_descriptor".to_string(),
                    sealed_server_root_xpub: "sealed_xpub".to_string(),
                }],
            },
            Some(&keys),
        )
        .await;
    assert_eq!(backup_response.status_code, StatusCode::OK);

    // Complete onboarding without email - should SUCCEED for test accounts
    let onboarding_response = client
        .complete_onboarding_v2(
            &account.account_id.to_string(),
            &onboarding::routes::CompleteOnboardingRequestV2 {},
            Some(&keys),
        )
        .await;

    // Should succeed for test accounts even without email
    assert_eq!(onboarding_response.status_code, StatusCode::OK);
    let body = onboarding_response.body.unwrap();

    // Verify all keys are returned
    assert!(!body.app_auth_pub.is_empty());
    assert!(!body.hardware_auth_pub.is_empty());
    assert!(!body.app_spending_pub.is_empty());
    assert!(!body.hardware_spending_pub.is_empty());
    assert!(!body.server_spending_pub.is_empty());

    // Verify signature is returned and valid hex
    assert!(!body.signature.is_empty());
    assert_eq!(body.signature.len(), 128);
    assert!(body.signature.chars().all(|c| c.is_ascii_hexdigit()));
}

#[tokio::test]
async fn test_complete_onboarding_v2_production_account_missing_email_fails() {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    // Create PRODUCTION account with private keyset
    let (spending_app_pub, spending_hw_pub) = (create_pubkey(), create_pubkey());
    let keys = create_new_authkeys(&mut context);
    let request = CreateAccountRequestV2 {
        auth: FullAccountAuthKeysInputV2 {
            app_pub: keys.app.public_key,
            hardware_pub: keys.hw.public_key,
            recovery_pub: keys.recovery.public_key,
            hardware_type: HardwareType::default(),
        },
        spend: SpendingKeysetInputV2 {
            network: Network::Signet,
            app_pub: spending_app_pub,
            hardware_pub: spending_hw_pub,
        },
        is_test_account: false, // Production account
    };
    let create_response = client.create_account_v2(&mut context, &request).await;
    assert_eq!(create_response.status_code, StatusCode::OK);
    let account = create_response.body.unwrap();

    // Add descriptor backup
    let backup_response = client
        .update_descriptor_backups(
            &account.account_id.to_string(),
            &DescriptorBackupsSet {
                wrapped_ssek: vec![],
                descriptor_backups: vec![DescriptorBackup::Private {
                    keyset_id: account.keyset_id.clone(),
                    sealed_descriptor: "sealed_descriptor".to_string(),
                    sealed_server_root_xpub: "sealed_xpub".to_string(),
                }],
            },
            Some(&keys),
        )
        .await;
    assert_eq!(backup_response.status_code, StatusCode::OK);

    // Try to complete onboarding without email
    let onboarding_response = client
        .complete_onboarding_v2(
            &account.account_id.to_string(),
            &onboarding::routes::CompleteOnboardingRequestV2 {},
            Some(&keys),
        )
        .await;

    // Should fail with bad request for production accounts
    assert_eq!(onboarding_response.status_code, StatusCode::BAD_REQUEST);
    let error_body = onboarding_response.body_as_string().await;
    assert!(error_body.contains("email"));
}

#[tokio::test]
#[ignore] // Requires WSM server with sign-public-keys endpoint deployed
async fn test_complete_onboarding_v2_legacy_keyset_success() {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    // Create a full account with legacy keyset (using v1 which creates legacy keysets)
    let account = create_full_account(
        &mut context,
        &bootstrap.services,
        AccountNetwork::BitcoinSignet,
        None,
    )
    .await;

    // Add and activate email touchpoint using service layer
    let keys = context.get_authentication_keys_for_account_id(&account.id);
    create_email_touchpoint(&bootstrap.services, &account.id, true).await;

    // Complete onboarding with legacy keyset (no descriptor backup required for legacy)
    let onboarding_response = client
        .complete_onboarding_v2(
            &account.id.to_string(),
            &onboarding::routes::CompleteOnboardingRequestV2 {},
            keys.as_ref(),
        )
        .await;

    // Should succeed - legacy keysets are now supported
    assert_eq!(onboarding_response.status_code, StatusCode::OK);

    let body = onboarding_response.body.unwrap();

    // Verify all keys are returned
    assert!(!body.app_auth_pub.is_empty());
    assert!(!body.hardware_auth_pub.is_empty());
    assert!(!body.app_spending_pub.is_empty());
    assert!(!body.hardware_spending_pub.is_empty());
    assert!(!body.server_spending_pub.is_empty());

    // Verify signature is returned and valid hex
    assert!(!body.signature.is_empty());
    assert_eq!(body.signature.len(), 128);
    assert!(body.signature.chars().all(|c| c.is_ascii_hexdigit()));
}

#[tokio::test]
async fn complete_onboarding_v2_requires_w3_hardware_type() {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    // Create a W3 account
    let (spending_app_pub, spending_hw_pub) = (create_pubkey(), create_pubkey());
    let keys = create_new_authkeys(&mut context);
    let request = CreateAccountRequestV2 {
        auth: FullAccountAuthKeysInputV2 {
            app_pub: keys.app.public_key,
            hardware_pub: keys.hw.public_key,
            recovery_pub: keys.recovery.public_key,
            hardware_type: HardwareType::W3,
        },
        spend: SpendingKeysetInputV2 {
            network: Network::Signet,
            app_pub: spending_app_pub,
            hardware_pub: spending_hw_pub,
        },
        is_test_account: true,
    };

    let create_response = client.create_account_v2(&mut context, &request).await;
    assert_eq!(create_response.status_code, StatusCode::OK);
    let account = create_response.body.unwrap();

    // Add descriptor backups (required for W3 onboarding completion)
    let descriptor_response = client
        .update_descriptor_backups(
            &account.account_id.to_string(),
            &DescriptorBackupsSet {
                wrapped_ssek: vec![],
                descriptor_backups: vec![DescriptorBackup::Private {
                    keyset_id: account.keyset_id.clone(),
                    sealed_descriptor: "test".to_string(),
                    sealed_server_root_xpub: "test".to_string(),
                }],
            },
            Some(&keys),
        )
        .await;
    assert_eq!(descriptor_response.status_code, StatusCode::OK);

    // Complete onboarding v2 should succeed for W3
    let onboarding_response = client
        .complete_onboarding_v2(
            &account.account_id.to_string(),
            &CompleteOnboardingRequestV2 {},
            Some(&keys),
        )
        .await;
    assert_eq!(onboarding_response.status_code, StatusCode::OK);
}

#[tokio::test]
async fn complete_onboarding_v2_rejects_w1_hardware_type() {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    // Create a W1 account
    let (spending_app_pub, spending_hw_pub) = (create_pubkey(), create_pubkey());
    let keys = create_new_authkeys(&mut context);
    let request = CreateAccountRequestV2 {
        auth: FullAccountAuthKeysInputV2 {
            app_pub: keys.app.public_key,
            hardware_pub: keys.hw.public_key,
            recovery_pub: keys.recovery.public_key,
            hardware_type: HardwareType::W1,
        },
        spend: SpendingKeysetInputV2 {
            network: Network::Signet,
            app_pub: spending_app_pub,
            hardware_pub: spending_hw_pub,
        },
        is_test_account: true,
    };

    let create_response = client.create_account_v2(&mut context, &request).await;
    assert_eq!(create_response.status_code, StatusCode::OK);
    let account = create_response.body.unwrap();

    // Add descriptor backups
    let descriptor_response = client
        .update_descriptor_backups(
            &account.account_id.to_string(),
            &DescriptorBackupsSet {
                wrapped_ssek: vec![],
                descriptor_backups: vec![DescriptorBackup::Private {
                    keyset_id: account.keyset_id.clone(),
                    sealed_descriptor: "test".to_string(),
                    sealed_server_root_xpub: "test".to_string(),
                }],
            },
            Some(&keys),
        )
        .await;
    assert_eq!(descriptor_response.status_code, StatusCode::OK);

    // Complete onboarding v2 should FAIL for W1
    let onboarding_response = client
        .complete_onboarding_v2(
            &account.account_id.to_string(),
            &CompleteOnboardingRequestV2 {},
            Some(&keys),
        )
        .await;
    assert_eq!(onboarding_response.status_code, StatusCode::FORBIDDEN);
}

#[tokio::test]
async fn complete_onboarding_v2_rejects_default_hardware_type() {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    // Create an account with default hardware_type (W1)
    let (spending_app_pub, spending_hw_pub) = (create_pubkey(), create_pubkey());
    let keys = create_new_authkeys(&mut context);
    let request = CreateAccountRequestV2 {
        auth: FullAccountAuthKeysInputV2 {
            app_pub: keys.app.public_key,
            hardware_pub: keys.hw.public_key,
            recovery_pub: keys.recovery.public_key,
            hardware_type: HardwareType::default(),
        },
        spend: SpendingKeysetInputV2 {
            network: Network::Signet,
            app_pub: spending_app_pub,
            hardware_pub: spending_hw_pub,
        },
        is_test_account: true,
    };

    let create_response = client.create_account_v2(&mut context, &request).await;
    assert_eq!(create_response.status_code, StatusCode::OK);
    let account = create_response.body.unwrap();

    // Add descriptor backups
    let descriptor_response = client
        .update_descriptor_backups(
            &account.account_id.to_string(),
            &DescriptorBackupsSet {
                wrapped_ssek: vec![],
                descriptor_backups: vec![DescriptorBackup::Private {
                    keyset_id: account.keyset_id.clone(),
                    sealed_descriptor: "test".to_string(),
                    sealed_server_root_xpub: "test".to_string(),
                }],
            },
            Some(&keys),
        )
        .await;
    assert_eq!(descriptor_response.status_code, StatusCode::OK);

    // Complete onboarding v2 should FAIL for default hardware_type (W1)
    let onboarding_response = client
        .complete_onboarding_v2(
            &account.account_id.to_string(),
            &CompleteOnboardingRequestV2 {},
            Some(&keys),
        )
        .await;
    assert_eq!(onboarding_response.status_code, StatusCode::FORBIDDEN);
}

#[tokio::test]
async fn w3_rejects_key_claims_auth_for_touchpoint_activation() {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    // Create a W3 account (NOT yet onboarded)
    let (spending_app_pub, spending_hw_pub) = (create_pubkey(), create_pubkey());
    let keys = create_new_authkeys(&mut context);
    let request = CreateAccountRequestV2 {
        auth: FullAccountAuthKeysInputV2 {
            app_pub: keys.app.public_key,
            hardware_pub: keys.hw.public_key,
            recovery_pub: keys.recovery.public_key,
            hardware_type: HardwareType::W3,
        },
        spend: SpendingKeysetInputV2 {
            network: Network::Signet,
            app_pub: spending_app_pub,
            hardware_pub: spending_hw_pub,
        },
        is_test_account: true,
    };

    let create_response = client.create_account_v2(&mut context, &request).await;
    assert_eq!(create_response.status_code, StatusCode::OK);
    let account = create_response.body.unwrap();

    // Add a touchpoint
    let email = "test@example.com";
    let add_touchpoint_response = client
        .add_touchpoint(
            &account.account_id.to_string(),
            &AccountAddTouchpointRequest::Email {
                email_address: email.to_string(),
            },
            Some(&keys),
        )
        .await;
    assert_eq!(add_touchpoint_response.status_code, StatusCode::OK);
    let touchpoint_id = add_touchpoint_response.body.unwrap().touchpoint_id;

    // Verify touchpoint
    let _ = client
        .verify_touchpoint(
            &account.account_id.to_string(),
            &touchpoint_id.to_string(),
            &AccountVerifyTouchpointRequest {
                verification_code: TEST_CODE.to_string(),
            },
        )
        .await;

    // W3 accounts MUST use ActionProof — KeyClaims auth should be rejected
    let activate_response = client
        .activate_touchpoint(
            &account.account_id.to_string(),
            &touchpoint_id.to_string(),
            &PrivilegedActionRequest::Initiate(AccountActivateTouchpointRequest {}),
            true, // app_signed = true (KeyClaims)
            true, // hw_signed = true (KeyClaims)
            &keys,
        )
        .await;
    assert_eq!(
        activate_response.status_code,
        StatusCode::FORBIDDEN,
        "W3 should reject KeyClaims auth — must use ActionProof"
    );
}

#[tokio::test]
async fn w3_requires_action_proof_signatures_for_touchpoint_during_onboarding() {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    // Create a W3 account (NOT yet onboarded)
    let (spending_app_pub, spending_hw_pub) = (create_pubkey(), create_pubkey());
    let keys = create_new_authkeys(&mut context);
    let request = CreateAccountRequestV2 {
        auth: FullAccountAuthKeysInputV2 {
            app_pub: keys.app.public_key,
            hardware_pub: keys.hw.public_key,
            recovery_pub: keys.recovery.public_key,
            hardware_type: HardwareType::W3,
        },
        spend: SpendingKeysetInputV2 {
            network: Network::Signet,
            app_pub: spending_app_pub,
            hardware_pub: spending_hw_pub,
        },
        is_test_account: true,
    };

    let create_response = client.create_account_v2(&mut context, &request).await;
    assert_eq!(create_response.status_code, StatusCode::OK);
    let account = create_response.body.unwrap();

    // Add a touchpoint
    let email = "test@example.com";
    let add_touchpoint_response = client
        .add_touchpoint(
            &account.account_id.to_string(),
            &AccountAddTouchpointRequest::Email {
                email_address: email.to_string(),
            },
            Some(&keys),
        )
        .await;
    assert_eq!(add_touchpoint_response.status_code, StatusCode::OK);
    let touchpoint_id = add_touchpoint_response.body.unwrap().touchpoint_id;

    // Verify touchpoint
    let _ = client
        .verify_touchpoint(
            &account.account_id.to_string(),
            &touchpoint_id.to_string(),
            &AccountVerifyTouchpointRequest {
                verification_code: TEST_CODE.to_string(),
            },
        )
        .await;

    // Try ActionProof WITHOUT signatures — should FAIL for W3 (BothFactors required)
    let activate_response = client
        .activate_touchpoint_with_action_proof(
            &account.account_id.to_string(),
            &touchpoint_id.to_string(),
            &PrivilegedActionRequest::Initiate(AccountActivateTouchpointRequest {}),
            action_proof::Action::SetRecoveryEmail,
            Some(email),
            &keys,
            false, // sign_with_app = false
            false, // sign_with_hw = false
        )
        .await;
    assert_eq!(
        activate_response.status_code,
        StatusCode::FORBIDDEN,
        "W3 should require ActionProof signatures even during onboarding"
    );

    // Try ActionProof WITH both signatures — should SUCCEED
    let activate_response = client
        .activate_touchpoint_with_action_proof(
            &account.account_id.to_string(),
            &touchpoint_id.to_string(),
            &PrivilegedActionRequest::Initiate(AccountActivateTouchpointRequest {}),
            action_proof::Action::SetRecoveryEmail,
            Some(email),
            &keys,
            true, // sign_with_app = true
            true, // sign_with_hw = true
        )
        .await;
    assert_eq!(
        activate_response.status_code,
        StatusCode::OK,
        "W3 should succeed with ActionProof and both signatures"
    );
}

#[tokio::test]
async fn w1_allows_skip_signatures_for_touchpoint_during_onboarding() {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    // Create a W1 account (NOT yet onboarded)
    let (spending_app_pub, spending_hw_pub) = (create_pubkey(), create_pubkey());
    let keys = create_new_authkeys(&mut context);
    let request = CreateAccountRequestV2 {
        auth: FullAccountAuthKeysInputV2 {
            app_pub: keys.app.public_key,
            hardware_pub: keys.hw.public_key,
            recovery_pub: keys.recovery.public_key,
            hardware_type: HardwareType::W1,
        },
        spend: SpendingKeysetInputV2 {
            network: Network::Signet,
            app_pub: spending_app_pub,
            hardware_pub: spending_hw_pub,
        },
        is_test_account: true,
    };

    let create_response = client.create_account_v2(&mut context, &request).await;
    assert_eq!(create_response.status_code, StatusCode::OK);
    let account = create_response.body.unwrap();

    // Add a touchpoint
    let add_touchpoint_response = client
        .add_touchpoint(
            &account.account_id.to_string(),
            &AccountAddTouchpointRequest::Email {
                email_address: "test@example.com".to_string(),
            },
            Some(&keys),
        )
        .await;
    assert_eq!(add_touchpoint_response.status_code, StatusCode::OK);
    let touchpoint_id = add_touchpoint_response.body.unwrap().touchpoint_id;

    // Verify touchpoint
    let _ = client
        .verify_touchpoint(
            &account.account_id.to_string(),
            &touchpoint_id.to_string(),
            &AccountVerifyTouchpointRequest {
                verification_code: TEST_CODE.to_string(),
            },
        )
        .await;

    // Try to activate WITHOUT signatures - should SUCCEED for W1 during onboarding
    let activate_response = client
        .activate_touchpoint(
            &account.account_id.to_string(),
            &touchpoint_id.to_string(),
            &PrivilegedActionRequest::Initiate(AccountActivateTouchpointRequest {}),
            false, // app_signed = false
            false, // hw_signed = false
            &keys,
        )
        .await;
    assert_eq!(
        activate_response.status_code,
        StatusCode::OK,
        "W1 should allow skipping signatures during onboarding"
    );
}

// ---- W3 ActionProof tests for migrated routes ----

#[tokio::test]
async fn w3_create_keyset_v2_with_action_proof_succeeds() {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let (account_id, _keys) = create_onboarded_w3_account(&mut context, &client).await;

    let keyset_request = SpendingKeysetInputV2 {
        network: Network::Signet,
        app_pub: create_pubkey(),
        hardware_pub: create_pubkey(),
    };

    let response = client.create_keyset_v2(&account_id, &keyset_request).await;
    assert_eq!(
        response.status_code,
        StatusCode::OK,
        "W3 create_keyset_v2 should succeed"
    );
}

#[tokio::test]
async fn w3_rotate_spending_keyset_with_action_proof_succeeds() {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let (account_id, keys) = create_onboarded_w3_account(&mut context, &client).await;

    // Create a second W3 keyset to rotate to
    let keyset_request = SpendingKeysetInputV2 {
        network: Network::Signet,
        app_pub: create_pubkey(),
        hardware_pub: create_pubkey(),
    };
    let keyset_response = client.create_keyset_v2(&account_id, &keyset_request).await;
    assert_eq!(keyset_response.status_code, StatusCode::OK);
    let new_keyset_id = keyset_response.body.unwrap().keyset_id;

    // Upload descriptor backup for the new keyset (must be superset of existing backups)
    // First, get the existing keyset_id from the account
    let account_status = client.get_account_status(&account_id).await;
    let existing_keyset_id = account_status.body.unwrap().keyset_id;

    let backup_response = client
        .update_descriptor_backups_with_action_proof(
            &account_id,
            &DescriptorBackupsSet {
                wrapped_ssek: vec![],
                descriptor_backups: vec![
                    DescriptorBackup::Private {
                        keyset_id: existing_keyset_id,
                        sealed_descriptor: "test".to_string(),
                        sealed_server_root_xpub: "test".to_string(),
                    },
                    DescriptorBackup::Private {
                        keyset_id: new_keyset_id.clone(),
                        sealed_descriptor: "test2".to_string(),
                        sealed_server_root_xpub: "test2".to_string(),
                    },
                ],
            },
            &keys,
            true,
            true,
        )
        .await;
    assert_eq!(backup_response.status_code, StatusCode::OK);

    // Rotate to the new keyset using ActionProof
    let response = client
        .rotate_to_spending_keyset_with_action_proof(
            &account_id,
            &new_keyset_id.to_string(),
            &RotateSpendingKeysetRequest {},
            &keys,
            true,
            true,
        )
        .await;
    assert_eq!(
        response.status_code,
        StatusCode::OK,
        "W3 rotate_spending_keyset with ActionProof should succeed"
    );
}

#[tokio::test]
async fn w1_to_w3_rotate_spending_keyset_rejects_keyclaims() {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    // Create a W1 account, then rotate auth keys to W3
    let keys = create_new_authkeys(&mut context);
    let request = CreateAccountRequestV2 {
        auth: FullAccountAuthKeysInputV2 {
            app_pub: keys.app.public_key,
            hardware_pub: keys.hw.public_key,
            recovery_pub: keys.recovery.public_key,
            hardware_type: HardwareType::W1,
        },
        spend: SpendingKeysetInputV2 {
            network: Network::Signet,
            app_pub: create_pubkey(),
            hardware_pub: create_pubkey(),
        },
        is_test_account: true,
    };
    let create_response = client.create_account_v2(&mut context, &request).await;
    assert_eq!(create_response.status_code, StatusCode::OK);
    let account = create_response.body.unwrap();
    let account_id = account.account_id.to_string();

    let desc_response = client
        .update_descriptor_backups(
            &account_id,
            &DescriptorBackupsSet {
                wrapped_ssek: vec![],
                descriptor_backups: vec![DescriptorBackup::Private {
                    keyset_id: account.keyset_id.clone(),
                    sealed_descriptor: "test".to_string(),
                    sealed_server_root_xpub: "test".to_string(),
                }],
            },
            Some(&keys),
        )
        .await;
    assert_eq!(desc_response.status_code, StatusCode::OK);

    let onboarding_response = client
        .complete_onboarding(&account_id, &CompleteOnboardingRequest {})
        .await;
    assert_eq!(onboarding_response.status_code, StatusCode::OK);

    // Rotate auth keys to W3 — this switches the account's auth strategy to ActionProof
    let keys = rotate_auth_keys_with_hardware_type(
        &mut context,
        &client,
        &account_id,
        &keys,
        HardwareType::W3,
    )
    .await;

    // Create a destination keyset
    let keyset_request = SpendingKeysetInputV2 {
        network: Network::Signet,
        app_pub: create_pubkey(),
        hardware_pub: create_pubkey(),
    };
    let create_response = client.create_keyset_v2(&account_id, &keyset_request).await;
    assert_eq!(create_response.status_code, StatusCode::OK);
    let new_keyset_id = create_response.body.unwrap().keyset_id;

    // KeyClaims auth should be rejected because auth keys are now W3
    let response = client
        .rotate_to_spending_keyset(
            &account_id,
            &new_keyset_id.to_string(),
            &RotateSpendingKeysetRequest {},
            &keys,
        )
        .await;
    assert_eq!(
        response.status_code,
        StatusCode::FORBIDDEN,
        "KeyClaims should be rejected when auth keys are W3"
    );
}

#[tokio::test]
async fn w3_delete_account_with_action_proof_succeeds() {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    // Create a non-onboarded W3 account (only non-onboarded accounts can be deleted)
    let keys = create_new_authkeys(&mut context);
    let request = CreateAccountRequestV2 {
        auth: FullAccountAuthKeysInputV2 {
            app_pub: keys.app.public_key,
            hardware_pub: keys.hw.public_key,
            recovery_pub: keys.recovery.public_key,
            hardware_type: HardwareType::W3,
        },
        spend: SpendingKeysetInputV2 {
            network: Network::Signet,
            app_pub: create_pubkey(),
            hardware_pub: create_pubkey(),
        },
        is_test_account: true,
    };
    let create_response = client.create_account_v2(&mut context, &request).await;
    assert_eq!(create_response.status_code, StatusCode::OK);
    let account_id = create_response.body.unwrap().account_id.to_string();

    let response = client
        .delete_account_with_action_proof(&account_id, &keys, true, true)
        .await;
    assert_eq!(
        response.status_code,
        StatusCode::OK,
        "W3 delete_account with ActionProof should succeed"
    );
}

// ---- Descriptor backup: pre-onboarding and post-onboarding variants for W1 and W3 ----

/// Helper: create a non-onboarded account via the v2 API.
/// Returns (account_id_string, keyset_id, keys).
async fn create_non_onboarded_v2_account(
    context: &mut TestContext,
    client: &TestClient,
    hardware_type: HardwareType,
) -> (
    String,
    types::account::identifiers::KeysetId,
    TestAuthenticationKeys,
) {
    let keys = create_new_authkeys(context);
    let request = CreateAccountRequestV2 {
        auth: FullAccountAuthKeysInputV2 {
            app_pub: keys.app.public_key,
            hardware_pub: keys.hw.public_key,
            recovery_pub: keys.recovery.public_key,
            hardware_type,
        },
        spend: SpendingKeysetInputV2 {
            network: Network::Signet,
            app_pub: create_pubkey(),
            hardware_pub: create_pubkey(),
        },
        is_test_account: true,
    };
    let create_response = client.create_account_v2(context, &request).await;
    assert_eq!(create_response.status_code, StatusCode::OK);
    let account = create_response.body.unwrap();
    (account.account_id.to_string(), account.keyset_id, keys)
}

/// Helper: onboard a v2 account by uploading its descriptor backup and completing onboarding.
/// Uses the correct complete-onboarding endpoint based on hardware type.
async fn onboard_v2_account(
    client: &TestClient,
    account_id: &str,
    keyset_id: &types::account::identifiers::KeysetId,
    keys: &TestAuthenticationKeys,
    hardware_type: HardwareType,
) {
    let desc_response = client
        .update_descriptor_backups(
            account_id,
            &DescriptorBackupsSet {
                wrapped_ssek: vec![],
                descriptor_backups: vec![DescriptorBackup::Private {
                    keyset_id: keyset_id.clone(),
                    sealed_descriptor: "test".to_string(),
                    sealed_server_root_xpub: "test".to_string(),
                }],
            },
            Some(keys),
        )
        .await;
    assert_eq!(desc_response.status_code, StatusCode::OK);

    match hardware_type {
        HardwareType::W3 => {
            let onboard_response = client
                .complete_onboarding_v2(account_id, &CompleteOnboardingRequestV2 {}, Some(keys))
                .await;
            assert_eq!(onboard_response.status_code, StatusCode::OK);
        }
        HardwareType::W1 => {
            let onboard_response = client
                .complete_onboarding(account_id, &CompleteOnboardingRequest {})
                .await;
            assert_eq!(onboard_response.status_code, StatusCode::OK);
        }
    }
}

// -- W1 pre-onboarding: KeyClaims, JwtOnly --

#[tokio::test]
async fn w1_update_descriptor_backups_pre_onboarding_keyclaims_succeeds() {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let (account_id, keyset_id, _keys) =
        create_non_onboarded_v2_account(&mut context, &client, HardwareType::W1).await;

    // Pre-onboarding: JwtOnly, no signatures needed
    let response = client
        .update_descriptor_backups(
            &account_id,
            &DescriptorBackupsSet {
                wrapped_ssek: vec![],
                descriptor_backups: vec![DescriptorBackup::Private {
                    keyset_id,
                    sealed_descriptor: "test".to_string(),
                    sealed_server_root_xpub: "test".to_string(),
                }],
            },
            None, // no keys = no signatures
        )
        .await;
    assert_eq!(
        response.status_code,
        StatusCode::OK,
        "W1 pre-onboarding descriptor backup should succeed with JwtOnly"
    );
}

// -- W1 post-onboarding: KeyClaims, BothFactors --

#[tokio::test]
async fn w1_update_descriptor_backups_post_onboarding_keyclaims_succeeds() {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let (account_id, keyset_id, keys) =
        create_non_onboarded_v2_account(&mut context, &client, HardwareType::W1).await;
    onboard_v2_account(&client, &account_id, &keyset_id, &keys, HardwareType::W1).await;

    // Post-onboarding: BothFactors required
    let response = client
        .update_descriptor_backups(
            &account_id,
            &DescriptorBackupsSet {
                wrapped_ssek: vec![],
                descriptor_backups: vec![DescriptorBackup::Private {
                    keyset_id,
                    sealed_descriptor: "updated".to_string(),
                    sealed_server_root_xpub: "updated".to_string(),
                }],
            },
            Some(&keys), // both app + hw signatures
        )
        .await;
    assert_eq!(
        response.status_code,
        StatusCode::OK,
        "W1 post-onboarding descriptor backup should succeed with BothFactors KeyClaims"
    );
}

// -- W3 pre-onboarding: ActionProof, JwtOnly --

#[tokio::test]
async fn w3_update_descriptor_backups_pre_onboarding_action_proof_succeeds() {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let (account_id, keyset_id, keys) =
        create_non_onboarded_v2_account(&mut context, &client, HardwareType::W3).await;

    // Pre-onboarding W3: JwtOnly proof, ActionProof with signatures still accepted
    let response = client
        .update_descriptor_backups_with_action_proof(
            &account_id,
            &DescriptorBackupsSet {
                wrapped_ssek: vec![],
                descriptor_backups: vec![DescriptorBackup::Private {
                    keyset_id,
                    sealed_descriptor: "test".to_string(),
                    sealed_server_root_xpub: "test".to_string(),
                }],
            },
            &keys,
            true,
            true,
        )
        .await;
    assert_eq!(
        response.status_code,
        StatusCode::OK,
        "W3 pre-onboarding descriptor backup with ActionProof should succeed"
    );
}

// -- W3 pre-onboarding: KeyClaims still works (JwtOnly = no sig check) --

#[tokio::test]
async fn w3_update_descriptor_backups_pre_onboarding_keyclaims_succeeds() {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let (account_id, keyset_id, _keys) =
        create_non_onboarded_v2_account(&mut context, &client, HardwareType::W3).await;

    // Pre-onboarding W3 with KeyClaims: JwtOnly means no sigs are checked,
    // so even though W3 ignores KeyClaims sigs, the unsigned context passes JwtOnly.
    let response = client
        .update_descriptor_backups(
            &account_id,
            &DescriptorBackupsSet {
                wrapped_ssek: vec![],
                descriptor_backups: vec![DescriptorBackup::Private {
                    keyset_id,
                    sealed_descriptor: "test".to_string(),
                    sealed_server_root_xpub: "test".to_string(),
                }],
            },
            None, // no keys
        )
        .await;
    assert_eq!(
        response.status_code,
        StatusCode::OK,
        "W3 pre-onboarding descriptor backup with KeyClaims should succeed (JwtOnly)"
    );
}

// -- W3 post-onboarding: ActionProof, BothFactors --

#[tokio::test]
async fn w3_update_descriptor_backups_post_onboarding_action_proof_succeeds() {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let (account_id, keys) = create_onboarded_w3_account(&mut context, &client).await;
    let keyset_id = client
        .get_account_status(&account_id)
        .await
        .body
        .unwrap()
        .keyset_id;

    // Post-onboarding: BothFactors via ActionProof
    let response = client
        .update_descriptor_backups_with_action_proof(
            &account_id,
            &DescriptorBackupsSet {
                wrapped_ssek: vec![],
                descriptor_backups: vec![DescriptorBackup::Private {
                    keyset_id,
                    sealed_descriptor: "updated".to_string(),
                    sealed_server_root_xpub: "updated".to_string(),
                }],
            },
            &keys,
            true,
            true,
        )
        .await;
    assert_eq!(
        response.status_code,
        StatusCode::OK,
        "W3 post-onboarding descriptor backup with ActionProof should succeed"
    );
}

// -- W3 post-onboarding: KeyClaims rejected (BothFactors can't be met) --

#[tokio::test]
async fn w3_update_descriptor_backups_post_onboarding_rejects_keyclaims() {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let (account_id, keys) = create_onboarded_w3_account(&mut context, &client).await;
    let keyset_id = client
        .get_account_status(&account_id)
        .await
        .body
        .unwrap()
        .keyset_id;

    // Post-onboarding W3 with KeyClaims: BothFactors required but W3 ignores KeyClaims sigs
    let response = client
        .update_descriptor_backups(
            &account_id,
            &DescriptorBackupsSet {
                wrapped_ssek: vec![],
                descriptor_backups: vec![DescriptorBackup::Private {
                    keyset_id,
                    sealed_descriptor: "updated".to_string(),
                    sealed_server_root_xpub: "updated".to_string(),
                }],
            },
            Some(&keys), // KeyClaims sigs — ignored for W3
        )
        .await;
    assert_eq!(
        response.status_code,
        StatusCode::FORBIDDEN,
        "W3 post-onboarding descriptor backup with KeyClaims should be rejected"
    );
}
