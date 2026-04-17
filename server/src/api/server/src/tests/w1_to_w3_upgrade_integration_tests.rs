use std::str::FromStr;

use bdk_utils::bdk::bitcoin::Network;
use bdk_utils::bdk::miniscript::DescriptorPublicKey;
use http::StatusCode;
use onboarding::routes::{
    CompleteOnboardingRequest, CreateAccountRequest, RotateSpendingKeysetRequest,
};
use onboarding::routes_v2::CreateAccountRequestV2;
use types::account::entities::{
    v2::{FullAccountAuthKeysInputV2, SpendingKeysetInputV2},
    DescriptorBackup, DescriptorBackupsSet, FullAccountAuthKeysInput, HardwareType,
    SpendingKeysetInput,
};

use crate::tests::{
    gen_services,
    lib::{create_new_authkeys, create_pubkey, rotate_auth_keys_with_hardware_type},
    requests::axum::TestClient,
};

/// W1 legacy (LegacyMultiSig keyset) → W3 upgrade:
/// 1. Create account via non-v2 endpoint (legacy spending keyset)
/// 2. Complete onboarding
/// 3. Rotate auth keys to W3 (switches auth strategy from KeyClaims to ActionProof)
/// 4. Create W3 destination keyset + upload descriptor with ActionProof + rotate spending keyset
#[tokio::test]
async fn test_w1_legacy_to_w3_upgrade() {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    // Create a legacy (W1) account via non-v2 endpoint
    let keys = create_new_authkeys(&mut context);
    let spending_app_dpub = DescriptorPublicKey::from_str("[74ce1142/84'/1'/0']tpubD6NzVbkrYhZ4XFo7hggmFF9qDqwrR9aqZv6j2Sgp1N5aVyxyMXxQG14grtRa3ob8ddZqxbd2hbPU7dEXvPRDRuQJ3NsMaGDaZXkLEewdthy/0/*").unwrap();
    let spending_hw_dpub = DescriptorPublicKey::from_str("[9e61ede9/84'/1'/0']tpubD6NzVbkrYhZ4Xwyrc51ZUDmxHYdTBpmTqTwSB6vr93T3Rt72nPzx2kjTV8VeWJW741HvVGvRyPSHZBgA5AEGD8Eib3sMwazMEuaQf1ioGBo/0/*").unwrap();

    let create_response = client
        .create_account(
            &mut context,
            &CreateAccountRequest::Full {
                auth: FullAccountAuthKeysInput {
                    app: keys.app.public_key,
                    hardware: keys.hw.public_key,
                    recovery: Some(keys.recovery.public_key),
                    hardware_type: HardwareType::default(),
                },
                spending: SpendingKeysetInput {
                    network: Network::Signet,
                    app: spending_app_dpub,
                    hardware: spending_hw_dpub,
                },
                is_test_account: true,
            },
        )
        .await;
    assert_eq!(create_response.status_code, StatusCode::OK);
    let account_id = create_response.body.unwrap().account_id.to_string();

    // Complete onboarding (legacy keyset doesn't require descriptor backup)
    let onboarding_response = client
        .complete_onboarding(&account_id, &CompleteOnboardingRequest {})
        .await;
    assert_eq!(onboarding_response.status_code, StatusCode::OK);

    // Rotate auth keys to W3 (KeyClaims accepted since active auth keys are still W1)
    let keys = rotate_auth_keys_with_hardware_type(
        &mut context,
        &client,
        &account_id,
        &keys,
        HardwareType::W3,
    )
    .await;

    // Create a W3 destination keyset
    let keyset_request = SpendingKeysetInputV2 {
        network: Network::Signet,
        app_pub: create_pubkey(),
        hardware_pub: create_pubkey(),
    };
    let keyset_response = client.create_keyset_v2(&account_id, &keyset_request).await;
    assert_eq!(keyset_response.status_code, StatusCode::OK);
    let new_keyset_id = keyset_response.body.unwrap().keyset_id;

    // Upload descriptor backup for the new keyset.
    // Auth keys are now W3, so ActionProof is required.
    let backup_response = client
        .update_descriptor_backups_with_action_proof(
            &account_id,
            &DescriptorBackupsSet {
                wrapped_ssek: vec![],
                descriptor_backups: vec![DescriptorBackup::Private {
                    keyset_id: new_keyset_id.clone(),
                    sealed_descriptor: "test".to_string(),
                    sealed_server_root_xpub: "test".to_string(),
                }],
            },
            &keys,
            true,
            true,
        )
        .await;
    assert_eq!(backup_response.status_code, StatusCode::OK);

    // Rotate spending keyset to W3 with ActionProof
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
        "W1 legacy to W3 spending keyset rotation should succeed: {}",
        response.body_string
    );
}

/// W1 private (PrivateMultiSig keyset with HardwareType::W1) → W3 upgrade:
/// 1. Create account via v2 endpoint with W1 hardware type
/// 2. Onboard
/// 3. Rotate auth keys to W3 (switches auth strategy from KeyClaims to ActionProof)
/// 4. Create W3 destination keyset + rotate spending keyset with ActionProof
#[tokio::test]
async fn test_w1_private_to_w3_upgrade() {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    // Create a W1 account via v2 endpoint
    let keys = create_new_authkeys(&mut context);
    let create_response = client
        .create_account_v2(
            &mut context,
            &CreateAccountRequestV2 {
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
            },
        )
        .await;
    assert_eq!(create_response.status_code, StatusCode::OK);
    let account = create_response.body.unwrap();
    let account_id = account.account_id.to_string();
    let initial_keyset_id = account.keyset_id;

    // Upload descriptor backups and complete onboarding
    let desc_response = client
        .update_descriptor_backups(
            &account_id,
            &DescriptorBackupsSet {
                wrapped_ssek: vec![],
                descriptor_backups: vec![DescriptorBackup::Private {
                    keyset_id: initial_keyset_id.clone(),
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

    // Rotate auth keys to W3 (KeyClaims accepted since active auth keys are still W1)
    let keys = rotate_auth_keys_with_hardware_type(
        &mut context,
        &client,
        &account_id,
        &keys,
        HardwareType::W3,
    )
    .await;

    // Create a W3 destination keyset
    let keyset_request = SpendingKeysetInputV2 {
        network: Network::Signet,
        app_pub: create_pubkey(),
        hardware_pub: create_pubkey(),
    };
    let keyset_response = client.create_keyset_v2(&account_id, &keyset_request).await;
    assert_eq!(keyset_response.status_code, StatusCode::OK);
    let new_keyset_id = keyset_response.body.unwrap().keyset_id;

    // Upload descriptor backups for both keysets.
    // Auth keys are now W3, so ActionProof is required.
    let backup_response = client
        .update_descriptor_backups_with_action_proof(
            &account_id,
            &DescriptorBackupsSet {
                wrapped_ssek: vec![],
                descriptor_backups: vec![
                    DescriptorBackup::Private {
                        keyset_id: initial_keyset_id,
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

    // Rotate spending keyset to W3 with ActionProof
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
        "W1 private to W3 spending keyset rotation should succeed: {}",
        response.body_string
    );
}
