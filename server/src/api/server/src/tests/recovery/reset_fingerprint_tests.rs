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
        let expected_signature = Signature::from_str("3045022100b9f5494d72019efe6e0379027880b0a55d02c06925d5c781b4d9f54679bfbba5022054a4cfb01336ff95a76ded3ccbd501b27693a615e0a237ede71a85937ba1e912").unwrap();
        assert_eq!(signature, expected_signature);
    }
}

// Pregenerated request with a signature that matches the hw_auth_public_key
fn get_pregenerated_request() -> ResetFingerprintRequest {
    ResetFingerprintRequest {
        hw_auth_public_key: PublicKey::from_str("032d2b02cf205aea7173627bcfcd48838b4204fa9bef0f9d85b1e4d84f179b55c1").unwrap(),
        version: 1,
        action: "FINGERPRINT_RESET".to_string(),
        device_id: "test-device-12345".to_string(),
        signature: Signature::from_str("3045022100afe9ac067b2e50021bd563ff25969e82ab11f103dc590326d2bcba036bd4ee4002206ac69067da7236c703e5f1d231fc3fd20e5aab72ce30235fb78e08596590d9cb").unwrap(),
        challenge: "random-challenge-98765".as_bytes().to_vec(),
    }
}
