use account::service::tests::create_private_spend_keyset;
use axum::response::IntoResponse;
use bdk_utils::bdk::bitcoin::Network;
use errors::ApiError;
use http::StatusCode;
use http_body_util::BodyExt;
use onboarding::{
    account_validation::error::AccountValidationError,
    routes::{CompleteOnboardingRequest, RotateSpendingKeysetRequest},
    routes_v2::{CreateAccountRequestV2, UpgradeAccountRequestV2},
};
use recovery::entities::{RecoveryDestination, RecoveryStatus};
use rstest::rstest;
use time::Duration;
use types::account::{
    bitcoin::Network as AccountNetwork,
    entities::{
        v2::{
            FullAccountAuthKeysInputV2, SpendingKeysetInputV2, UpgradeLiteAccountAuthKeysInputV2,
        },
        DescriptorBackup, DescriptorBackupsSet, Factor,
    },
};

use crate::tests::{
    gen_services,
    lib::{
        create_email_touchpoint, create_full_account, create_full_account_v2, create_lite_account,
        create_new_authkeys, create_pubkey, generate_delay_and_notify_recovery,
    },
    requests::{axum::TestClient, Response},
};

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
            &keys,
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
            &keys,
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
