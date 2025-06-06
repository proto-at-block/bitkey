use bdk_utils::bdk::bitcoin::secp256k1::ecdsa::Signature;
use bdk_utils::bdk::bitcoin::secp256k1::PublicKey;
use http::StatusCode;
use recovery::routes::reset_fingerprint::ResetFingerprintRequest;
use rstest::rstest;
use std::str::FromStr;
use time::{Duration, OffsetDateTime};
use types::account::AccountType;
use types::privileged_action::{
    repository::{
        AuthorizationStrategyRecord, DelayAndNotifyRecord, DelayAndNotifyStatus,
        PrivilegedActionInstanceRecord,
    },
    router::generic::{
        AuthorizationStrategyInput, ContinuePrivilegedActionRequest, DelayAndNotifyInput,
        PrivilegedActionInstanceInput, PrivilegedActionRequest, PrivilegedActionResponse,
    },
    shared::{PrivilegedActionInstanceId, PrivilegedActionType},
};

use crate::tests::{gen_services, lib::create_account, requests::axum::TestClient};

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
    let account = create_account(&mut context, &bootstrap.services, AccountType::Full).await;

    let request = PrivilegedActionRequest::Initiate(get_pregenerated_request());

    if has_existing_instance {
        let reset_fingerprint_response = client
            .reset_fingerprint(&account.get_id().to_string(), &request)
            .await;
        assert_eq!(reset_fingerprint_response.status_code, StatusCode::OK);
    }

    // act
    let reset_fingerprint_response = client
        .reset_fingerprint(&account.get_id().to_string(), &request)
        .await;

    // assert
    assert_eq!(
        reset_fingerprint_response.status_code, expected_status,
        "{}",
        reset_fingerprint_response.body_string
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
    let account = create_account(&mut context, &bootstrap.services, AccountType::Full).await;

    let privileged_action_instance_id = PrivilegedActionInstanceId::gen().unwrap();
    let instance = PrivilegedActionInstanceRecord {
        id: privileged_action_instance_id,
        account_id: account.get_id().to_owned(),
        privileged_action_type: PrivilegedActionType::ResetFingerprint,
        authorization_strategy: AuthorizationStrategyRecord::DelayAndNotify(DelayAndNotifyRecord {
            status: DelayAndNotifyStatus::Pending,
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
            &account.get_id().to_string(),
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
            r.grant.signature
        } else {
            panic!("Expected pending response");
        };

        // expected signature for the pregenerated request
        let expected_signature = Signature::from_str("304402205ce8b3ee12324d783c6dcbfb679d178e80c3c37f30c41cd7785ea616d01b61f60220197b7cffd2207f5590c1384f0ea1dea846f0b0448c69b711a353c1a7fb1a6059").unwrap();
        assert_eq!(signature, expected_signature);
    }
}

// Pregenerated request with a signature that matches the hw_auth_public_key
fn get_pregenerated_request() -> ResetFingerprintRequest {
    ResetFingerprintRequest {
        hw_auth_public_key: PublicKey::from_str("03260c677bf1106ae4ca6baeadd9b1f45d9a50801c33674f3509ff3badadddeb6d").unwrap(),
        version: 1,
        action: 1,
        device_id: "test-device-12345".to_string(),
        signature: Signature::from_str("3045022100e9941e69c0b738b70ae67f17f99cc4cfa974544cb63fbe05142a13a6b35861f5022056939b16a79fccddc55807a605546586247b6e55ded6ee932415f53e84e4755f").unwrap(),
        challenge: "random-challenge-98765".as_bytes().to_vec(),
    }
}
