use account::service::tests::{TestAuthenticationKeys, TestKeypair};
use bdk_utils::bdk::bitcoin::secp256k1::ecdsa::Signature;
use bdk_utils::bdk::bitcoin::secp256k1::{PublicKey, SecretKey};
use http::StatusCode;
use rand::rngs::OsRng;
use recovery::routes::reset_fingerprint::ResetFingerprintRequest;
use rstest::rstest;
use serde_json::Value;
use std::str::FromStr;
use time::{Duration, OffsetDateTime};
use types::account::keys::FullAccountAuthKeys;
use types::account::AccountType;
use types::privileged_action::{
    repository::{
        AuthorizationStrategyRecord, DelayAndNotifyRecord, PrivilegedActionInstanceRecord,
        RecordStatus,
    },
    router::generic::{
        AuthorizationStrategyInput, ContinuePrivilegedActionRequest, DelayAndNotifyInput,
        PrivilegedActionInstanceInput, PrivilegedActionRequest, PrivilegedActionResponse,
    },
    shared::{PrivilegedActionInstanceId, PrivilegedActionType},
};

use crate::tests::lib::{create_account, create_full_account, create_pubkey};
use crate::tests::{gen_services, requests::axum::TestClient};

const EXPECTED_HW_AUTH_PUBLIC_KEY: &str =
    "03260c677bf1106ae4ca6baeadd9b1f45d9a50801c33674f3509ff3badadddeb6d";

#[rstest]
#[case::with_existing_instance(true, StatusCode::CONFLICT)]
#[case::without_existing_instance(false, StatusCode::OK)]
#[tokio::test]
async fn test_initiate_reset_fingerprint(
    #[case] has_existing_instance: bool,
    #[case] expected_status: StatusCode,
) {
    // arrange
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;

    let (app_pubkey, hardware_pubkey, recovery_pubkey) = (
        create_pubkey(),
        PublicKey::from_str(EXPECTED_HW_AUTH_PUBLIC_KEY).unwrap(),
        create_pubkey(),
    );
    context.add_authentication_keys(TestAuthenticationKeys {
        app: TestKeypair {
            public_key: app_pubkey,
            secret_key: SecretKey::new(&mut OsRng),
        },
        hw: TestKeypair {
            public_key: hardware_pubkey,
            secret_key: SecretKey::new(&mut OsRng),
        },
        recovery: TestKeypair {
            public_key: recovery_pubkey,
            secret_key: SecretKey::new(&mut OsRng),
        },
    });
    let account = create_full_account(
        &mut context,
        &bootstrap.services,
        types::account::bitcoin::Network::BitcoinSignet,
        Some(FullAccountAuthKeys {
            app_pubkey,
            hardware_pubkey,
            recovery_pubkey: Some(recovery_pubkey),
        }),
    )
    .await;

    let request = PrivilegedActionRequest::Initiate(get_pregenerated_request());

    if has_existing_instance {
        let reset_fingerprint_response = client
            .reset_fingerprint(&account.id.to_string(), &request)
            .await;
        assert_eq!(reset_fingerprint_response.status_code, StatusCode::OK);
    }

    // act
    let reset_fingerprint_response = client
        .reset_fingerprint(&account.id.to_string(), &request)
        .await;

    // assert
    assert_eq!(
        reset_fingerprint_response.status_code, expected_status,
        "{}",
        reset_fingerprint_response.body_string
    );
}

#[tokio::test]
async fn test_initiate_with_invalid_signature() {
    // arrange
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;
    let account = create_account(&mut context, &bootstrap.services, AccountType::Full, true).await;

    // act
    let request = PrivilegedActionRequest::Initiate(get_pregenerated_request());
    let reset_fingerprint_response = client
        .reset_fingerprint(&account.get_id().to_string(), &request)
        .await;

    // assert
    assert_eq!(
        reset_fingerprint_response.status_code,
        StatusCode::UNAUTHORIZED
    );
}

#[rstest]
#[case::delay_window_passed(Duration::days(0), StatusCode::OK)]
#[case::delay_window_not_passed(Duration::days(1), StatusCode::CONFLICT)]
#[tokio::test]
async fn test_complete_reset_fingerprint(
    #[case] delay_end_time_offset: Duration,
    #[case] expected_status: StatusCode,
) {
    // arrange
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;
    let (app_pubkey, hardware_pubkey, recovery_pubkey) = (
        create_pubkey(),
        PublicKey::from_str(EXPECTED_HW_AUTH_PUBLIC_KEY).unwrap(),
        create_pubkey(),
    );
    context.add_authentication_keys(TestAuthenticationKeys {
        app: TestKeypair {
            public_key: app_pubkey,
            secret_key: SecretKey::new(&mut OsRng),
        },
        hw: TestKeypair {
            public_key: hardware_pubkey,
            secret_key: SecretKey::new(&mut OsRng),
        },
        recovery: TestKeypair {
            public_key: recovery_pubkey,
            secret_key: SecretKey::new(&mut OsRng),
        },
    });
    let account = create_full_account(
        &mut context,
        &bootstrap.services,
        types::account::bitcoin::Network::BitcoinSignet,
        Some(FullAccountAuthKeys {
            app_pubkey,
            hardware_pubkey,
            recovery_pubkey: Some(recovery_pubkey),
        }),
    )
    .await;

    let privileged_action_instance_id = PrivilegedActionInstanceId::gen().unwrap();
    let instance = PrivilegedActionInstanceRecord {
        id: privileged_action_instance_id,
        account_id: account.id.to_owned(),
        privileged_action_type: PrivilegedActionType::ResetFingerprint,
        authorization_strategy: AuthorizationStrategyRecord::DelayAndNotify(DelayAndNotifyRecord {
            status: RecordStatus::Pending,
            delay_end_time: OffsetDateTime::now_utc() + delay_end_time_offset,
            cancellation_token: "test".to_string(),
            completion_token: "test".to_string(),
        }),
        created_at: OffsetDateTime::now_utc(),
        updated_at: OffsetDateTime::now_utc(),
        request: PrivilegedActionRequest::Initiate(get_pregenerated_request()),
    };
    bootstrap
        .services
        .privileged_action_repository
        .persist(&instance)
        .await
        .expect("Failed to persist privileged action instance");

    let instance_id = instance.id;
    let completion_token =
        if let AuthorizationStrategyRecord::DelayAndNotify(r) = instance.authorization_strategy {
            r.completion_token
        } else {
            panic!("Expected delay and notify strategy");
        };

    // act
    let complete_reset_fingerprint_response = client
        .reset_fingerprint(
            &account.id.to_string(),
            &PrivilegedActionRequest::Continue(ContinuePrivilegedActionRequest {
                privileged_action_instance: PrivilegedActionInstanceInput {
                    id: instance_id,
                    authorization_strategy: AuthorizationStrategyInput::DelayAndNotify(
                        DelayAndNotifyInput { completion_token },
                    ),
                },
            }),
        )
        .await;

    // assert
    assert_eq!(
        complete_reset_fingerprint_response.status_code, expected_status,
        "{}",
        complete_reset_fingerprint_response.body_string
    );
    if expected_status == StatusCode::OK {
        let body = complete_reset_fingerprint_response.body.unwrap();
        let signature = if let PrivilegedActionResponse::Completed(r) = body {
            r.grant.wsm_signature
        } else {
            panic!("Expected pending response");
        };

        // expected signature for the pregenerated request
        let expected_signature = Signature::from_str("30450221009c2dde3523854c2ada86df6cdcbecda9c627bcc94fbbd59a40a249aedd424c160220262c54750eabf951cbd0fc5085721a2ba4791302405c0afdf6fd7d02d3500c0a").unwrap();
        assert_eq!(signature, expected_signature);
    }
}

#[rstest::rstest]
#[case::test_account(true, StatusCode::OK)]
#[case::non_test_account(false, StatusCode::FORBIDDEN)]
#[tokio::test]
async fn test_update_delay_duration_for_test(
    #[case] is_test_account: bool,
    #[case] expected_status: StatusCode,
) {
    // arrange
    let (mut context, bootstrap) = gen_services().await;
    let client = TestClient::new(bootstrap.router).await;
    let account = create_account(
        &mut context,
        &bootstrap.services,
        AccountType::Full,
        is_test_account,
    )
    .await;

    let privileged_action_instance_id = PrivilegedActionInstanceId::gen().unwrap();
    let instance = PrivilegedActionInstanceRecord {
        id: privileged_action_instance_id,
        account_id: account.get_id().to_owned(),
        privileged_action_type: PrivilegedActionType::ResetFingerprint,
        authorization_strategy: AuthorizationStrategyRecord::DelayAndNotify(DelayAndNotifyRecord {
            status: RecordStatus::Pending,
            delay_end_time: OffsetDateTime::now_utc() + Duration::seconds(1000),
            cancellation_token: "test".to_string(),
            completion_token: "test".to_string(),
        }),
        created_at: OffsetDateTime::now_utc(),
        updated_at: OffsetDateTime::now_utc(),
        request: PrivilegedActionRequest::Initiate(get_pregenerated_request()),
    };
    bootstrap
        .services
        .privileged_action_repository
        .persist(&instance)
        .await
        .expect("Failed to persist privileged action instance");

    let instance_id = instance.id;

    let response = client
        .update_delay_duration_for_test(&account.get_id().to_string(), &instance_id.to_string(), 10)
        .await;
    assert_eq!(
        response.status_code, expected_status,
        "{}",
        response.body_string
    );
    if expected_status == StatusCode::OK {
        let instance: PrivilegedActionInstanceRecord<Value> = bootstrap
            .services
            .privileged_action_repository
            .fetch_by_id(&instance_id)
            .await
            .expect("Failed to fetch privileged action instance");
        let delay_and_notify_record = if let AuthorizationStrategyRecord::DelayAndNotify(r) =
            instance.authorization_strategy
        {
            r
        } else {
            panic!("Expected delay and notify strategy");
        };
        assert_eq!(
            delay_and_notify_record.delay_end_time,
            instance.created_at + Duration::seconds(10)
        );
    }
}

// Pregenerated request with a signature that matches the hw_auth_public_key
fn get_pregenerated_request() -> ResetFingerprintRequest {
    ResetFingerprintRequest {
        version: 1,
        action: 1,
        device_id: "test-device-12345".as_bytes().to_vec(),
        signature: Signature::from_str("3045022100e9941e69c0b738b70ae67f17f99cc4cfa974544cb63fbe05142a13a6b35861f5022056939b16a79fccddc55807a605546586247b6e55ded6ee932415f53e84e4755f").unwrap(),
        challenge: "random-challenge-98765".as_bytes().to_vec(),
        app_signature: Signature::from_str("3045022100e9941e69c0b738b70ae67f17f99cc4cfa974544cb63fbe05142a13a6b35861f5022056939b16a79fccddc55807a605546586247b6e55ded6ee932415f53e84e4755f").unwrap(),
    }
}
