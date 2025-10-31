use bdk_utils::bdk::bitcoin::Network;
use http::StatusCode;
use onboarding::{
    routes::{CompleteOnboardingRequest, RotateSpendingKeysetRequest},
    routes_v2::{CreateAccountRequestV2, UpgradeAccountRequestV2},
};
use types::account::entities::{
    v2::{FullAccountAuthKeysInputV2, SpendingKeysetInputV2, UpgradeLiteAccountAuthKeysInputV2},
    DescriptorBackup, DescriptorBackupsSet,
};

use crate::tests::{
    gen_services,
    lib::{create_lite_account, create_new_authkeys, create_pubkey},
    requests::axum::TestClient,
};

// create_account edge cases primarily tested in onboarding_integration_tests.rs
// When we roll out v2 onboarding, we should update the primary tests to use the
// v2 code path and move these tests to the v1 code path
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

// upgrade_account edge cases primarily tested in onboarding_integration_tests.rs
// When we roll out v2 upgrading, we should update the primary tests to use the
// v2 code path and move these tests to the v1 code path
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

// create_keyset edge cases primarily tested in account_keysets_integration_tests.rs
// When we roll out v2 keyset creation, we should update the primary tests to use the
// v2 code path and move these tests to the v1 code path
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
