use account::entities::{
    FullAccountAuthKeys, FullAccountAuthKeysPayload, Network, SpendingKeyset, SpendingKeysetRequest,
};
use account::service::FetchAccountInput;
use http::StatusCode;
use http_server::middlewares::wsm;
use onboarding::routes::{
    AccountKeyset, CreateAccountRequest, CreateKeysetRequest, RotateSpendingKeysetRequest,
};
use types::account::identifiers::KeysetId;
use wsm_rust_client::SigningService;
use wsm_rust_client::{TEST_XPUB_SPEND, TEST_XPUB_SPEND_ORIGIN};

use crate::tests;
use crate::tests::gen_services;
use crate::tests::lib::{
    create_descriptor_keys, create_inactive_spending_keyset_for_account, get_static_test_authkeys,
};
use crate::tests::requests::axum::TestClient;

#[tokio::test]
async fn test_account_keyset_lifecycle() {
    let bootstrap = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;
    let service = bootstrap.services.account_service;

    let network = Network::BitcoinTest;
    let (auth_app, auth_hw, auth_recovery) = get_static_test_authkeys();
    let (_, active_spend_app) = create_descriptor_keys(network);
    let (_, active_spend_hw) = create_descriptor_keys(network);

    let response = client
        .create_account(&CreateAccountRequest::Full {
            auth: FullAccountAuthKeysPayload {
                app: auth_app,
                hardware: auth_hw,
                recovery: Some(auth_recovery),
            },
            spending: SpendingKeysetRequest {
                network: network.into(),
                app: active_spend_app.clone(),
                hardware: active_spend_hw.clone(),
            },
            is_test_account: true,
        })
        .await;
    assert_eq!(
        response.status_code,
        StatusCode::OK,
        "{}",
        response.body_string
    );
    let create_account_response = response.body.unwrap();
    let account_id = create_account_response.account_id;
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
            app_pubkey: auth_app,
            hardware_pubkey: auth_hw,
            recovery_pubkey: Some(auth_recovery),
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
    let bootstrap = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let mut network = Network::BitcoinTest;
    let (auth_app, auth_hw, auth_recovery) = get_static_test_authkeys();
    let (_, active_spend_app) = create_descriptor_keys(network);
    let (_, active_spend_hw) = create_descriptor_keys(network);

    let response = client
        .create_account(&CreateAccountRequest::Full {
            auth: FullAccountAuthKeysPayload {
                app: auth_app,
                hardware: auth_hw,
                recovery: Some(auth_recovery),
            },
            spending: SpendingKeysetRequest {
                network: network.into(),
                app: active_spend_app.clone(),
                hardware: active_spend_hw.clone(),
            },
            is_test_account: true,
        })
        .await;
    assert_eq!(
        response.status_code,
        StatusCode::OK,
        "{}",
        response.body_string
    );
    let create_account_response = response.body.unwrap();
    let account_id = create_account_response.account_id;

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
    let bootstrap = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;
    let service = bootstrap.services.account_service;

    let network = Network::BitcoinTest;
    let (auth_app, auth_hw, auth_recovery) = get_static_test_authkeys();
    let (_, spend_app) = create_descriptor_keys(network);
    let (_, spend_hw) = create_descriptor_keys(network);

    let response = client
        .create_account(&CreateAccountRequest::Full {
            auth: FullAccountAuthKeysPayload {
                app: auth_app,
                hardware: auth_hw,
                recovery: Some(auth_recovery),
            },
            spending: SpendingKeysetRequest {
                network: network.into(),
                app: spend_app.clone(),
                hardware: spend_hw.clone(),
            },
            is_test_account: true,
        })
        .await;
    assert_eq!(
        response.status_code,
        StatusCode::OK,
        "{}",
        response.body_string
    );
    let create_account_response = response.body.unwrap();
    let account_id = create_account_response.account_id;
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
    let bootstrap = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let network = Network::BitcoinTest;
    let (auth_app, auth_hw, auth_recovery) = get_static_test_authkeys();
    let (_, active_spend_app) = create_descriptor_keys(network);
    let (_, active_spend_hw) = create_descriptor_keys(network);

    let response = client
        .create_account(&CreateAccountRequest::Full {
            auth: FullAccountAuthKeysPayload {
                app: auth_app,
                hardware: auth_hw,
                recovery: Some(auth_recovery),
            },
            spending: SpendingKeysetRequest {
                network: network.into(),
                app: active_spend_app.clone(),
                hardware: active_spend_hw.clone(),
            },
            is_test_account: true,
        })
        .await;
    assert_eq!(
        response.status_code,
        StatusCode::OK,
        "{}",
        response.body_string
    );
    let create_account_response = response.body.unwrap();
    let account_id = create_account_response.account_id;

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

    let _ = wsm_service
        .client
        .sign_psbt(
            &inactive_keyset_id.to_string(),
            &format!("wpkh({TEST_XPUB_SPEND_ORIGIN}{TEST_XPUB_SPEND}/0/*)").to_string(),
            &format!("wpkh({TEST_XPUB_SPEND_ORIGIN}{TEST_XPUB_SPEND}/1/*)").to_string(),
            "cHNidP8BAIkBAAAAARba0uJxgoOu4Qb4Hl2O4iC/zZhhEJZ7+5HuZDe8gkB5AQAAAAD/////AugDAAAAAAAAIgAgF5/lDEQhJZCBD9n6jaI46jvtUEg38/2j1s1PTw0lkcbugQEAAAAAACIAIBef5QxEISWQgQ/Z+o2iOOo77VBIN/P9o9bNT08NJZHGAAAAAAABAOoCAAAAAAEB97UeXCkIkrURS0D1VEse6bslADCfk6muDzWMawqsSkoAAAAAAP7///8CTW7uAwAAAAAWABT3EVvw7PVw4dEmLqWe/v9ETcBTtKCGAQAAAAAAIgAgF5/lDEQhJZCBD9n6jaI46jvtUEg38/2j1s1PTw0lkcYCRzBEAiBswJbFVv3ixdepzHonCMI1BujKEjxMHQ2qKmhVjVkiMAIgdcn1gzW+S4utbYQlfMHdVlpmK4T6onLbN+QCda1UVsYBIQJQtXaqHMYW0tBOmIEwjeBpTORXNrsO4FMWhqCf8feXXClTIgABASughgEAAAAAACIAIBef5QxEISWQgQ/Z+o2iOOo77VBIN/P9o9bNT08NJZHGAQVpUiECF0P0bwdqX4NvwdYkr9Vxkao2/0yB1rcqgHW1tXkVvlYhA4j/DyKUDUrb8kg9K4UAclJV/1Vgs/De/yOcz9L6e1AYIQPSBYIG9nN3JQbL65BnavWnmjgjoYn/Z6rmvHogngpbI1OuIgYCF0P0bwdqX4NvwdYkr9Vxkao2/0yB1rcqgHW1tXkVvlYEIgnl9CIGA4j/DyKUDUrb8kg9K4UAclJV/1Vgs/De/yOcz9L6e1AYBJhPJu0iBgPSBYIG9nN3JQbL65BnavWnmjgjoYn/Z6rmvHogngpbIwR2AHgNACICAhdD9G8Hal+Db8HWJK/VcZGqNv9Mgda3KoB1tbV5Fb5WBCIJ5fQiAgOI/w8ilA1K2/JIPSuFAHJSVf9VYLPw3v8jnM/S+ntQGASYTybtIgID0gWCBvZzdyUGy+uQZ2r1p5o4I6GJ/2eq5rx6IJ4KWyMEdgB4DQAiAgIXQ/RvB2pfg2/B1iSv1XGRqjb/TIHWtyqAdbW1eRW+VgQiCeX0IgIDiP8PIpQNStvySD0rhQByUlX/VWCz8N7/I5zP0vp7UBgEmE8m7SICA9IFggb2c3clBsvrkGdq9aeaOCOhif9nqua8eiCeClsjBHYAeA0A"
        )
        .await
        .expect("Successful signing");
}

#[tokio::test]
async fn test_fetch_account_keysets() {
    let bootstrap = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let network = Network::BitcoinTest;
    let (auth_app, auth_hw, auth_recovery) = get_static_test_authkeys();
    let (_active_config_hw, _, _) = get_static_test_authkeys();
    let (_, active_spend_app) = create_descriptor_keys(network);
    let (_, active_spend_hw) = create_descriptor_keys(network);

    let response = client
        .create_account(&CreateAccountRequest::Full {
            auth: FullAccountAuthKeysPayload {
                app: auth_app,
                hardware: auth_hw,
                recovery: Some(auth_recovery),
            },
            spending: SpendingKeysetRequest {
                network: network.into(),
                app: active_spend_app.clone(),
                hardware: active_spend_hw.clone(),
            },
            is_test_account: true,
        })
        .await;
    assert_eq!(
        response.status_code,
        StatusCode::OK,
        "{}",
        response.body_string
    );
    let create_account_response = response.body.unwrap();
    let account_id = create_account_response.account_id;
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
struct RotateSpendingKeysetTestVector {
    override_keyset_id: Option<String>,
    expected_status: StatusCode,
}

async fn rotate_spending_keyset_test(vector: RotateSpendingKeysetTestVector) {
    let bootstrap = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let network = Network::BitcoinTest;
    let (auth_app, auth_hw, auth_recover) = get_static_test_authkeys();
    let (_, active_spend_app) = create_descriptor_keys(network);
    let (_, active_spend_hw) = create_descriptor_keys(network);

    // First, make an account
    let request = CreateAccountRequest::Full {
        auth: FullAccountAuthKeysPayload {
            app: auth_app,
            hardware: auth_hw,
            recovery: Some(auth_recover),
        },
        spending: SpendingKeysetRequest {
            network: network.into(),
            app: active_spend_app.clone(),
            hardware: active_spend_hw.clone(),
        },
        is_test_account: true,
    };

    let actual_response = client.create_account(&request).await;
    assert_eq!(
        actual_response.status_code,
        StatusCode::OK,
        "{}",
        actual_response.body_string
    );
    let account_id = actual_response.body.unwrap().account_id;
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
        create_inactive_spending_keyset_for_account(&client, &account_id, network).await;
    let response = client
        .rotate_to_spending_keyset(
            &account_id.to_string(),
            &vector.override_keyset_id.unwrap_or(keyset_id.to_string()),
            &RotateSpendingKeysetRequest {},
        )
        .await;
    assert_eq!(
        response.status_code, vector.expected_status,
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

tests! {
    runner = rotate_spending_keyset_test,
    test_successfully_rotate_spending_keyset: RotateSpendingKeysetTestVector {
        override_keyset_id: None,
        expected_status: StatusCode::OK,
    },
    test_invalid_keyset_id_rotate_spending_keyset: RotateSpendingKeysetTestVector {
        override_keyset_id: Some("this_should_fail".to_string()),
        expected_status: StatusCode::BAD_REQUEST,
    },
    test_inexistent_keyset_id_rotate_spending_keyset: RotateSpendingKeysetTestVector {
        override_keyset_id: Some(KeysetId::gen().unwrap().to_string()),
        expected_status: StatusCode::BAD_REQUEST,
    },
}
