use std::str::FromStr;

use http::StatusCode;
use rstest::rstest;

use account::service::{tests::create_descriptor_keys, FetchAccountInput};
use bdk_utils::bdk::keys::DescriptorPublicKey;
use http_server::middlewares::wsm;
use onboarding::routes::{
    AccountKeyset, CreateAccountRequest, CreateKeysetRequest, RotateSpendingKeysetRequest,
};
use types::account::bitcoin::Network;
use types::account::entities::{FullAccountAuthKeysPayload, SpendingKeysetRequest};
use types::account::identifiers::KeysetId;
use types::account::keys::FullAccountAuthKeys;
use types::account::spending::SpendingKeyset;
use wsm_rust_client::SigningService;
use wsm_rust_client::{TEST_XPUB_SPEND, TEST_XPUB_SPEND_ORIGIN};

use crate::tests::gen_services;
use crate::tests::lib::{create_inactive_spending_keyset_for_account, create_new_authkeys};
use crate::tests::requests::axum::TestClient;

#[tokio::test]
async fn test_account_keyset_lifecycle() {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;
    let service = bootstrap.services.account_service;

    let network = Network::BitcoinTest;
    let keys = create_new_authkeys(&mut context);
    let (_, active_spend_app) = create_descriptor_keys(network);
    let (_, active_spend_hw) = create_descriptor_keys(network);

    let response = client
        .create_account(
            &mut context,
            &CreateAccountRequest::Full {
                auth: FullAccountAuthKeysPayload {
                    app: keys.app.public_key,
                    hardware: keys.hw.public_key,
                    recovery: Some(keys.recovery.public_key),
                },
                spending: SpendingKeysetRequest {
                    network: network.into(),
                    app: active_spend_app.clone(),
                    hardware: active_spend_hw.clone(),
                },
                is_test_account: true,
            },
        )
        .await;
    assert_eq!(
        response.status_code,
        StatusCode::OK,
        "{}",
        response.body_string
    );
    let create_account_response = response.body.unwrap();
    let account_id = create_account_response.account_id;
    let keys = context
        .get_authentication_keys_for_account_id(&account_id)
        .expect("Keys not found for account");
    let keyset = create_account_response
        .keyset
        .expect("Account should have keyset");
    let active_keyset_id = keyset.keyset_id;
    let active_spend_server_xpub = keyset.spending;

    let (_, spend_app) = create_descriptor_keys(network);
    let (_, spend_hw) = create_descriptor_keys(network);

    let response = client
        .create_keyset(
            &account_id.to_string(),
            &CreateKeysetRequest {
                spending: SpendingKeysetRequest {
                    network: network.into(),
                    app: spend_app,
                    hardware: spend_hw,
                },
            },
            &keys,
        )
        .await;
    assert_eq!(
        response.status_code,
        StatusCode::OK,
        "{}",
        response.body_string
    );
    let create_keyset_response = response.body.unwrap();
    let inactive_keyset_id = create_keyset_response.keyset_id;

    let retrieved_account_with_inactive_keyset = service
        .fetch_full_account(FetchAccountInput {
            account_id: &account_id,
        })
        .await
        .expect("Retrieved Account");
    assert!(retrieved_account_with_inactive_keyset
        .spending_keysets
        .contains_key(&inactive_keyset_id));

    let auth_key_id = retrieved_account_with_inactive_keyset
        .common_fields
        .active_auth_keys_id;
    let active_auth_key = retrieved_account_with_inactive_keyset
        .auth_keys
        .get(&auth_key_id)
        .unwrap()
        .to_owned();
    assert_eq!(
        active_auth_key,
        FullAccountAuthKeys {
            app_pubkey: keys.app.public_key,
            hardware_pubkey: keys.hw.public_key,
            recovery_pubkey: Some(keys.recovery.public_key),
        }
    );

    assert_eq!(
        retrieved_account_with_inactive_keyset
            .spending_keysets
            .len(),
        2
    );
    assert_eq!(
        retrieved_account_with_inactive_keyset
            .spending_keysets
            .len(),
        2
    );
    assert_eq!(retrieved_account_with_inactive_keyset.auth_keys.len(), 1);

    assert_ne!(
        retrieved_account_with_inactive_keyset.active_keyset_id,
        inactive_keyset_id,
    );

    let response = client.get_account_status(&account_id.to_string()).await;
    assert_eq!(
        response.status_code,
        StatusCode::OK,
        "{}",
        response.body_string
    );
    let account_status_response = response.body.unwrap();
    assert_eq!(account_status_response.keyset_id, active_keyset_id);
    assert_eq!(
        account_status_response.spending,
        SpendingKeyset {
            network,
            app_dpub: active_spend_app,
            hardware_dpub: active_spend_hw,
            server_dpub: active_spend_server_xpub
        }
    );
}

#[tokio::test]
async fn test_account_keyset_switch_networks() {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let mut network = Network::BitcoinTest;
    let keys = create_new_authkeys(&mut context);
    let (_, active_spend_app) = create_descriptor_keys(network);
    let (_, active_spend_hw) = create_descriptor_keys(network);

    let response = client
        .create_account(
            &mut context,
            &CreateAccountRequest::Full {
                auth: FullAccountAuthKeysPayload {
                    app: keys.app.public_key,
                    hardware: keys.hw.public_key,
                    recovery: Some(keys.recovery.public_key),
                },
                spending: SpendingKeysetRequest {
                    network: network.into(),
                    app: active_spend_app.clone(),
                    hardware: active_spend_hw.clone(),
                },
                is_test_account: true,
            },
        )
        .await;
    assert_eq!(
        response.status_code,
        StatusCode::OK,
        "{}",
        response.body_string
    );
    let create_account_response = response.body.unwrap();
    let account_id = create_account_response.account_id;
    let keys = context
        .get_authentication_keys_for_account_id(&account_id)
        .expect("Keys not found for account");

    // Switching networks should fail
    network = Network::BitcoinMain;
    let (_, spend_app) = create_descriptor_keys(network);
    let (_, spend_hw) = create_descriptor_keys(network);

    let response = client
        .create_keyset(
            &account_id.to_string(),
            &CreateKeysetRequest {
                spending: SpendingKeysetRequest {
                    network: network.into(),
                    app: spend_app,
                    hardware: spend_hw,
                },
            },
            &keys,
        )
        .await;
    assert_eq!(
        response.status_code,
        StatusCode::BAD_REQUEST,
        "{}",
        response.body_string
    );
}

#[tokio::test]
async fn test_account_duplicate_spending_keyset() {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;
    let service = bootstrap.services.account_service;

    let network = Network::BitcoinTest;
    let keys = create_new_authkeys(&mut context);
    let (_, spend_app) = create_descriptor_keys(network);
    let (_, spend_hw) = create_descriptor_keys(network);

    let response = client
        .create_account(
            &mut context,
            &CreateAccountRequest::Full {
                auth: FullAccountAuthKeysPayload {
                    app: keys.app.public_key,
                    hardware: keys.hw.public_key,
                    recovery: Some(keys.recovery.public_key),
                },
                spending: SpendingKeysetRequest {
                    network: network.into(),
                    app: spend_app.clone(),
                    hardware: spend_hw.clone(),
                },
                is_test_account: true,
            },
        )
        .await;
    assert_eq!(
        response.status_code,
        StatusCode::OK,
        "{}",
        response.body_string
    );
    let create_account_response = response.body.unwrap();
    let account_id = create_account_response.account_id;
    let keys = context
        .get_authentication_keys_for_account_id(&account_id)
        .expect("Keys not found for account");
    let keyset = create_account_response
        .keyset
        .expect("Account should have a keyset");
    let active_keyset_id = keyset.keyset_id;
    let active_spend_server_xpub = keyset.spending;

    let response = client
        .create_keyset(
            &account_id.to_string(),
            &CreateKeysetRequest {
                spending: SpendingKeysetRequest {
                    network: network.into(),
                    app: spend_app,
                    hardware: spend_hw,
                },
            },
            &keys,
        )
        .await;
    assert_eq!(
        response.status_code,
        StatusCode::OK,
        "{}",
        response.body_string
    );
    let create_keyset_response = response.body.unwrap();
    assert_eq!(create_keyset_response.keyset_id, active_keyset_id);
    assert_eq!(create_keyset_response.spending, active_spend_server_xpub);

    let retrieved_account = service
        .fetch_full_account(FetchAccountInput {
            account_id: &account_id,
        })
        .await
        .expect("Retrieved Account");
    assert_eq!(retrieved_account.spending_keysets.len(), 1);
    assert!(retrieved_account
        .spending_keysets
        .contains_key(&active_keyset_id));
}

#[tokio::test]
async fn test_inactive_keyset_id_exists_in_wsm() {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let network = Network::BitcoinTest;
    let keys = create_new_authkeys(&mut context);
    let (_, active_spend_app) = create_descriptor_keys(network);
    let (_, active_spend_hw) = create_descriptor_keys(network);

    let response = client
        .create_account(
            &mut context,
            &CreateAccountRequest::Full {
                auth: FullAccountAuthKeysPayload {
                    app: keys.app.public_key,
                    hardware: keys.hw.public_key,
                    recovery: Some(keys.recovery.public_key),
                },
                spending: SpendingKeysetRequest {
                    network: network.into(),
                    app: active_spend_app.clone(),
                    hardware: active_spend_hw.clone(),
                },
                is_test_account: true,
            },
        )
        .await;
    assert_eq!(
        response.status_code,
        StatusCode::OK,
        "{}",
        response.body_string
    );
    let create_account_response = response.body.unwrap();
    let account_id = create_account_response.account_id;
    let keys = context
        .get_authentication_keys_for_account_id(&account_id)
        .expect("Keys not found for account");

    // Use hardcoded values since our PSBT down below applies a partial signature from `spend_app`'s associated tprv ([71f40633/84'/1'/0']tprv8gED5H3xs3dJB3jUzE8LHSJVHD4m9oN4RNM137FrnPycWEhNr3qnbipEzJFJrUaHMgneaqhoUT8av6F49PFV5kp1sH77yqVztLWpFDdXuKP/*).
    let spend_app = DescriptorPublicKey::from_str("[71f40633/84'/1'/0']tpubDCvFDh6D1RJy4WmGssnvgqxbrEahK8YxzfwnKdJACfn1Lix9USfNnDS7ASsN5r1XHsrksBa7Kyz7Si6H9KfJTVKgKyv34HXCKrkcRf5K1Cy/*").unwrap();
    let spend_hw = DescriptorPublicKey::from_str("[6181b35f/84'/1'/0']tpubDDcPeFd2AK4RKwtC4SzT5zfCCVPAgNHz2xUUk1wrg9Lgo97scFNiRyUfG5Ebdx89TQhHfXaSiSRae7C1M1FKaHm5JBfDJUPsAHGHS4b1L66/*").unwrap();

    let response = client
        .create_keyset(
            &account_id.to_string(),
            &CreateKeysetRequest {
                spending: SpendingKeysetRequest {
                    network: network.into(),
                    app: spend_app.clone(),
                    hardware: spend_hw.clone(),
                },
            },
            &keys,
        )
        .await;
    assert_eq!(
        response.status_code,
        StatusCode::OK,
        "{}",
        response.body_string
    );
    let create_keyset_response = response.body.unwrap();
    let inactive_keyset_id = create_keyset_response.keyset_id;

    // This is to ensure the root key id provided is the one in the WSM
    let wsm_service = http_server::config::extract::<wsm::Config>("test".into())
        .unwrap()
        .to_client()
        .unwrap();

    let wallet_ext_descriptor = format!(
        "wsh(sortedmulti(2,{},{},{}))",
        spend_app.clone(),
        spend_hw.clone(),
        format!("{TEST_XPUB_SPEND_ORIGIN}{TEST_XPUB_SPEND}/0/*")
    );
    let wallet_change_descriptor = format!(
        "wsh(sortedmulti(2,{},{},{}))",
        spend_app,
        spend_hw,
        format!("{TEST_XPUB_SPEND_ORIGIN}{TEST_XPUB_SPEND}/1/*")
    );

    let _ = wsm_service
        .client
        .sign_psbt(
            &inactive_keyset_id.to_string(),
            &wallet_ext_descriptor,
            &wallet_change_descriptor,
            "cHNidP8BAF4BAAAAAaVEkD7VReyQhBpLX4NSc2RLxj7AHxxS8ojs5LgtNGvjAAAAAAD9////AWYpAQAAAAAAIgAgl2DoPF1XYOKlj+2LirbckFbZSevxA5AfQGxMxHZ0wFVDCgAAAAEAtQIAAAAAAQEAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAP////8EAt8JAP////8CBSoBAAAAAAAiACB2m1S3qeqjdbJ0Q8LdNshcLKlNlVSkMDEf5wkFKxLQwQAAAAAAAAAAJmokqiGp7eL2HD9x0d79P6mZ36NpU3VcaQaJeZlitIvr2DaXToz5ASAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABASsFKgEAAAAAACIAIHabVLep6qN1snRDwt02yFwsqU2VVKQwMR/nCQUrEtDBIgICumvp3iByH+T9N4zXjK+dB3G5ftHaTVyx9GppwF11++tHMEQCIFATLNWFad4y+tiC6CImRKrSbA3SvnkWmX5DRTDC6oZ7AiBQMPARIt5ZJ+F70CI6g8fIgQnRdtf7fTTKJpKbmuPmYAEBBWlSIQIptfHA8EI7pOcS7Vb5ZNvEF8kYF1OQGOqlyfy0kRslEiECumvp3iByH+T9N4zXjK+dB3G5ftHaTVyx9GppwF11++shAzHYYKakLd2sSDyDsHoAZa3h1G8g48Uu/Dz3oCZY2CO/U64iBgIptfHA8EI7pOcS7Vb5ZNvEF8kYF1OQGOqlyfy0kRslEhjDReHpVAAAgAEAAIAAAACAAAAAAAAAAAAiBgK6a+neIHIf5P03jNeMr50Hcbl+0dpNXLH0amnAXXX76xRx9AYzVAAAgAEAAIAAAACAAAAAACIGAzHYYKakLd2sSDyDsHoAZa3h1G8g48Uu/Dz3oCZY2CO/FGGBs19UAACAAQAAgAAAAIAAAAAAAAEBaVIhAjYlKeJQOtIdnbdOGZINq9NkciT7udaaae74sbPQ0dnaIQJPGXZ6Kvh8V4H/C8Cs1/tI64gNpFHklsSgbry9qWe57iEDWBRBaLTG5ObDosCh24HYLlIYKWHj4D+SINM7kgUavz5TriICAjYlKeJQOtIdnbdOGZINq9NkciT7udaaae74sbPQ0dnaFHH0BjNUAACAAQAAgAAAAIABAAAAIgICTxl2eir4fFeB/wvArNf7SOuIDaRR5JbEoG68valnue4UYYGzX1QAAIABAACAAAAAgAEAAAAiAgNYFEFotMbk5sOiwKHbgdguUhgpYePgP5Ig0zuSBRq/PhjDReHpVAAAgAEAAIAAAACAAAAAAAEAAAAA"
        )
        .await
        .expect("Successful signing");
}

#[tokio::test]
async fn test_fetch_account_keysets() {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let network = Network::BitcoinTest;
    let keys = create_new_authkeys(&mut context);
    let (_, active_spend_app) = create_descriptor_keys(network);
    let (_, active_spend_hw) = create_descriptor_keys(network);

    let response = client
        .create_account(
            &mut context,
            &CreateAccountRequest::Full {
                auth: FullAccountAuthKeysPayload {
                    app: keys.app.public_key,
                    hardware: keys.hw.public_key,
                    recovery: Some(keys.recovery.public_key),
                },
                spending: SpendingKeysetRequest {
                    network: network.into(),
                    app: active_spend_app.clone(),
                    hardware: active_spend_hw.clone(),
                },
                is_test_account: true,
            },
        )
        .await;
    assert_eq!(
        response.status_code,
        StatusCode::OK,
        "{}",
        response.body_string
    );
    let create_account_response = response.body.unwrap();
    let account_id = create_account_response.account_id;
    let keys = context
        .get_authentication_keys_for_account_id(&account_id)
        .expect("Keys not found for account");
    let keyset = create_account_response
        .keyset
        .expect("Account should have a keyset");
    let active_keyset_id = keyset.keyset_id;
    let active_server_spend = keyset.spending;

    let (_, inactive_spend_app) = create_descriptor_keys(network);
    let (_, inactive_spend_hw) = create_descriptor_keys(network);

    let response = client
        .create_keyset(
            &account_id.to_string(),
            &CreateKeysetRequest {
                spending: SpendingKeysetRequest {
                    network: network.into(),
                    app: inactive_spend_app.clone(),
                    hardware: inactive_spend_hw.clone(),
                },
            },
            &keys,
        )
        .await;
    assert_eq!(
        response.status_code,
        StatusCode::OK,
        "{}",
        response.body_string
    );
    let create_keyset_response = response.body.unwrap();
    let inactive_keyset_id = create_keyset_response.keyset_id;
    let inactive_server_spend = create_keyset_response.spending;

    let expected_keysets = vec![
        AccountKeyset {
            keyset_id: active_keyset_id,
            network: network.into(),
            app_dpub: active_spend_app,
            hardware_dpub: active_spend_hw,
            server_dpub: active_server_spend,
        },
        AccountKeyset {
            keyset_id: inactive_keyset_id,
            network: network.into(),
            app_dpub: inactive_spend_app,
            hardware_dpub: inactive_spend_hw,
            server_dpub: inactive_server_spend,
        },
    ];

    let response = client.get_account_keysets(&account_id.to_string()).await;
    assert_eq!(
        response.status_code,
        StatusCode::OK,
        "{}",
        response.body_string
    );
    let account_keysets_response = response.body.unwrap();
    assert_eq!(account_keysets_response.keysets, expected_keysets);
}
#[rstest]
#[case::success(None, StatusCode::OK)]
#[case::invalid_keyset_id(Some("this_should_fail".to_string()), StatusCode::BAD_REQUEST)]
#[case::inexistent_keyset_id(Some(KeysetId::gen().unwrap().to_string()), StatusCode::BAD_REQUEST)]
#[tokio::test]
async fn test_rotate_spending_keyset(
    #[case] override_keyset_id: Option<String>,
    #[case] expected_status: StatusCode,
) {
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let network = Network::BitcoinTest;
    let keys = create_new_authkeys(&mut context);
    let (_, active_spend_app) = create_descriptor_keys(network);
    let (_, active_spend_hw) = create_descriptor_keys(network);

    // First, make an account
    let request = CreateAccountRequest::Full {
        auth: FullAccountAuthKeysPayload {
            app: keys.app.public_key,
            hardware: keys.hw.public_key,
            recovery: Some(keys.hw.public_key),
        },
        spending: SpendingKeysetRequest {
            network: network.into(),
            app: active_spend_app.clone(),
            hardware: active_spend_hw.clone(),
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
    let account = bootstrap
        .services
        .account_service
        .fetch_full_account(FetchAccountInput {
            account_id: &account_id,
        })
        .await
        .unwrap();
    let active_keyset_id = account.active_keyset_id;

    let keyset_id =
        create_inactive_spending_keyset_for_account(&context, &client, &account_id, network).await;
    let response = client
        .rotate_to_spending_keyset(
            &account_id.to_string(),
            &override_keyset_id.unwrap_or(keyset_id.to_string()),
            &RotateSpendingKeysetRequest {},
            &keys,
        )
        .await;
    assert_eq!(
        response.status_code, expected_status,
        "{}",
        response.body_string
    );

    let account = bootstrap
        .services
        .account_service
        .fetch_full_account(FetchAccountInput {
            account_id: &account_id,
        })
        .await
        .unwrap();

    if response.status_code == StatusCode::OK {
        assert_ne!(account.active_keyset_id, active_keyset_id);
        account
            .active_spending_keyset()
            .expect("Spending keyset should be present");
    } else {
        assert_eq!(account.active_keyset_id, active_keyset_id);
        account
            .active_spending_keyset()
            .expect("Spending keyset should be present");
    }
}
