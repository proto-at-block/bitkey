//! Integration tests for the `VerifyHardwareSerial` confirm path. The
//! sweep gate that initiates this action lands in PR9, so tests
//! initiate via the service layer directly.

use std::collections::HashMap;

use account::attestation_verifier::test_fixture::TEST_ACCOUNT_ATTESTED_SERIAL;
use account::service::FetchAccountInput;
use bdk_utils::bdk::bitcoin::Network;
use http::{HeaderMap, HeaderValue, StatusCode};
use onboarding::routes_v2::CreateAccountRequestV2;
use privileged_action::routes::{ConfirmSubmission, ProcessPrivilegedActionVerificationRequest};
use privileged_action::service::authorize_privileged_action::{
    AuthorizePrivilegedActionInput, AuthorizePrivilegedActionOutput,
    PrivilegedActionRequestValidatorBuilder,
};
use types::account::entities::v2::{
    FullAccountAuthKeysInputV2, HardwareAttestation, SpendingKeysetInputV2,
};
use types::account::entities::{Account, HardwareType};
use types::account::identifiers::{AccountId, KeysetId};
use types::account::spending::AttestedHardwareSerial;
use types::privileged_action::repository::AuthorizationStrategyRecord;
use types::privileged_action::router::generic::{
    PrivilegedActionRequest, PrivilegedActionResponse,
};
use types::privileged_action::shared::PrivilegedActionType;
use types::privileged_action::verify_hardware_serial::VerifyHardwareSerialSubmission;

use crate::tests::lib::{create_new_authkeys, create_pubkey};
use crate::tests::requests::axum::TestClient;
use crate::tests::{gen_services_with_overrides, TestContext};
use crate::{Bootstrap, GenServiceOverrides};

const HARDWARE_VERIFICATION_ENABLED_FLAG: &str = "f8e-hardware-verification-enabled";
const HARDWARE_VERIFICATION_REQUIRED_FLAG: &str = "f8e-hardware-verification-required";

fn flag_overrides() -> HashMap<String, String> {
    HashMap::from([
        (
            HARDWARE_VERIFICATION_ENABLED_FLAG.to_string(),
            "true".to_string(),
        ),
        (
            HARDWARE_VERIFICATION_REQUIRED_FLAG.to_string(),
            "true".to_string(),
        ),
    ])
}

fn experimentation_headers() -> HeaderMap {
    let mut headers = HeaderMap::new();
    headers.insert(
        "Bitkey-App-Installation-Id",
        HeaderValue::from_static("test-app-installation-id"),
    );
    headers
}

fn magic_attestation() -> HardwareAttestation {
    HardwareAttestation {
        signature: b"good".to_vec(),
        cert_chain: vec![b"good".to_vec()],
    }
}

/// Onboard a test account with the magic attestation so the active keyset
/// has `attested_hardware_serial = Some(Pending(TEST_ACCOUNT_ATTESTED_SERIAL))`.
async fn onboard_enrolled_account(
    context: &mut TestContext,
    client: &TestClient,
) -> (AccountId, KeysetId) {
    let keys = create_new_authkeys(context);
    let request = CreateAccountRequestV2 {
        auth: FullAccountAuthKeysInputV2 {
            app_pub: keys.app.public_key,
            hardware_pub: keys.hw.public_key,
            recovery_pub: keys.recovery.public_key,
            hardware_type: HardwareType::default(),
        },
        spend: SpendingKeysetInputV2 {
            network: Network::Signet,
            app_pub: create_pubkey(),
            hardware_pub: create_pubkey(),
            hardware_attestation: Some(magic_attestation()),
        },
        is_test_account: true,
    };
    let response = client
        .create_account_v2_with_headers(context, experimentation_headers(), &request)
        .await;
    assert_eq!(
        response.status_code,
        StatusCode::OK,
        "{}",
        response.body_string,
    );
    let body = response.body.expect("create_account_v2 returned no body");
    (body.account_id, body.keyset_id)
}

/// Initiate a VerifyHardwareSerial priv action via the service layer
/// (mirroring PR9's sweep gate) and return the issued web_auth_token.
async fn initiate_verify_hardware_serial(
    bootstrap: &Bootstrap,
    account_id: &AccountId,
) -> String {
    let request = PrivilegedActionRequest::Initiate(());
    let result: AuthorizePrivilegedActionOutput<(), ()> = bootstrap
        .services
        .privileged_action_service
        .authorize_privileged_action(AuthorizePrivilegedActionInput::<(), errors::ApiError> {
            account_id,
            privileged_action_definition: &PrivilegedActionType::VerifyHardwareSerial.into(),
            privileged_action_request: &request,
            request_validator: PrivilegedActionRequestValidatorBuilder::default()
                .build()
                .expect("validator"),
        })
        .await
        .expect("authorize_privileged_action(Initiate) succeeded");
    let PrivilegedActionResponse::Pending(pending) = (match result {
        AuthorizePrivilegedActionOutput::Pending(p) => p,
        AuthorizePrivilegedActionOutput::Authorized(_) => {
            panic!("expected Pending for OOB initiate, got Authorized")
        }
    }) else {
        panic!("expected PrivilegedActionResponse::Pending");
    };
    let instance_record = bootstrap
        .services
        .privileged_action_service
        .privileged_action_repository
        .fetch_by_id::<()>(&pending.privileged_action_instance.id)
        .await
        .expect("fetch instance");
    let AuthorizationStrategyRecord::OutOfBand(oob) = instance_record.authorization_strategy else {
        panic!("expected OutOfBand strategy");
    };
    oob.web_auth_token
}

async fn fetch_attested_serial(
    bootstrap: &Bootstrap,
    account_id: &AccountId,
    keyset_id: &KeysetId,
) -> Option<AttestedHardwareSerial> {
    let account = bootstrap
        .services
        .account_service
        .fetch_account(FetchAccountInput { account_id })
        .await
        .expect("fetch_account");
    let Account::Full(full) = account else {
        panic!("expected FullAccount");
    };
    full.spending_keysets
        .get(keyset_id)
        .and_then(|k| k.optional_private_multi_sig())
        .and_then(|p| p.attested_hardware_serial.clone())
}

/// Happy path: matching serial → 200 OK and the keyset's attested
/// hardware serial is promoted from `Pending` to `Verified`.
#[tokio::test]
async fn confirm_with_matching_serial_promotes_keyset_to_verified() {
    let overrides = GenServiceOverrides::new().feature_flags(flag_overrides());
    let (mut context, bootstrap) = gen_services_with_overrides(overrides).await;
    let client = TestClient::new(bootstrap.router.clone()).await;

    let (account_id, keyset_id) = onboard_enrolled_account(&mut context, &client).await;
    let web_auth_token =
        initiate_verify_hardware_serial(&bootstrap, &account_id).await;

    let resp = client
        .respond_to_out_of_band_privileged_action(
            &ProcessPrivilegedActionVerificationRequest::Confirm {
                web_auth_token,
                submission: ConfirmSubmission::VerifyHardwareSerial {
                    submission_data: VerifyHardwareSerialSubmission {
                        serial: TEST_ACCOUNT_ATTESTED_SERIAL.to_string(),
                    },
                },
            },
        )
        .await;
    assert_eq!(resp.status_code, StatusCode::OK, "{}", resp.body_string);

    let attested = fetch_attested_serial(&bootstrap, &account_id, &keyset_id).await;
    assert!(
        matches!(attested, Some(AttestedHardwareSerial::Verified(ref s)) if s == TEST_ACCOUNT_ATTESTED_SERIAL),
        "expected Verified({TEST_ACCOUNT_ATTESTED_SERIAL}), got {attested:?}",
    );
}

/// Models a partial-completion retry: keyset already Verified (e.g.,
/// from a previous confirm where the keyset write succeeded but the
/// priv-action write failed) and a new confirm arrives against the
/// still-Pending priv-action. The handler should complete the priv-
/// action rather than leaving it stale.
#[tokio::test]
async fn confirm_completes_pending_priv_action_when_keyset_already_verified() {
    let overrides = GenServiceOverrides::new().feature_flags(flag_overrides());
    let (mut context, bootstrap) = gen_services_with_overrides(overrides).await;
    let client = TestClient::new(bootstrap.router.clone()).await;

    let (account_id, keyset_id) = onboard_enrolled_account(&mut context, &client).await;
    bootstrap
        .services
        .account_service
        .mark_hardware_serial_verified(&account_id, &keyset_id)
        .await
        .expect("pre-mark keyset verified");

    let web_auth_token = initiate_verify_hardware_serial(&bootstrap, &account_id).await;
    let resp = client
        .respond_to_out_of_band_privileged_action(
            &ProcessPrivilegedActionVerificationRequest::Confirm {
                web_auth_token: web_auth_token.clone(),
                submission: ConfirmSubmission::VerifyHardwareSerial {
                    submission_data: VerifyHardwareSerialSubmission {
                        // Submitted serial doesn't matter; the handler
                        // short-circuits on the already-Verified keyset.
                        serial: "this-should-not-be-checked".to_string(),
                    },
                },
            },
        )
        .await;
    assert_eq!(resp.status_code, StatusCode::OK, "{}", resp.body_string);

    let instance = bootstrap
        .services
        .privileged_action_service
        .get_by_web_auth_token::<()>(&web_auth_token)
        .await
        .expect("priv-action lookup");
    let AuthorizationStrategyRecord::OutOfBand(oob) = instance.authorization_strategy else {
        panic!("expected OutOfBand strategy");
    };
    assert_eq!(
        oob.status,
        types::privileged_action::repository::RecordStatus::Completed,
        "priv-action must transition to Completed when keyset already Verified",
    );
}

/// Mismatch with attempts remaining: 422 + structured detail carrying
/// `remainingAttempts`. The record stays Pending so the user can retry.
#[tokio::test]
async fn confirm_with_mismatched_serial_returns_422_with_remaining_attempts() {
    let overrides = GenServiceOverrides::new().feature_flags(flag_overrides());
    let (mut context, bootstrap) = gen_services_with_overrides(overrides).await;
    let client = TestClient::new(bootstrap.router.clone()).await;

    let (account_id, keyset_id) = onboard_enrolled_account(&mut context, &client).await;
    let web_auth_token =
        initiate_verify_hardware_serial(&bootstrap, &account_id).await;

    let resp = client
        .respond_to_out_of_band_privileged_action(
            &ProcessPrivilegedActionVerificationRequest::Confirm {
                web_auth_token,
                submission: ConfirmSubmission::VerifyHardwareSerial {
                    submission_data: VerifyHardwareSerialSubmission {
                        serial: "WRONG-SERIAL".to_string(),
                    },
                },
            },
        )
        .await;
    assert_eq!(resp.status_code, StatusCode::UNPROCESSABLE_ENTITY);
    assert!(
        resp.body_string.contains("HARDWARE_SERIAL_MISMATCH"),
        "expected HARDWARE_SERIAL_MISMATCH, got: {}",
        resp.body_string,
    );
    assert!(
        resp.body_string.contains("remainingAttempts"),
        "expected detail to carry remainingAttempts, got: {}",
        resp.body_string,
    );

    // Keyset remains Pending; user can retry.
    let attested = fetch_attested_serial(&bootstrap, &account_id, &keyset_id).await;
    assert!(
        matches!(attested, Some(AttestedHardwareSerial::Pending(_))),
        "keyset must remain Pending after a single mismatch, got {attested:?}",
    );
}

/// Normalization: whitespace, dashes, and case differences shouldn't
/// matter — the user typing the serial with separators should still
/// match the attested form.
#[tokio::test]
async fn confirm_normalizes_separators_and_case() {
    let overrides = GenServiceOverrides::new().feature_flags(flag_overrides());
    let (mut context, bootstrap) = gen_services_with_overrides(overrides).await;
    let client = TestClient::new(bootstrap.router.clone()).await;

    let (account_id, keyset_id) = onboard_enrolled_account(&mut context, &client).await;
    let web_auth_token =
        initiate_verify_hardware_serial(&bootstrap, &account_id).await;

    // Attested is "000WS27100000000". User types it lowercase with dashes
    // and stray whitespace.
    let lower = TEST_ACCOUNT_ATTESTED_SERIAL.to_lowercase();
    let mut chars = lower.chars();
    let mut decorated = String::new();
    for (i, c) in chars.by_ref().enumerate() {
        decorated.push(c);
        if i == 3 || i == 7 || i == 11 {
            decorated.push('-');
        }
    }
    decorated = format!("  {}  ", decorated);

    let resp = client
        .respond_to_out_of_band_privileged_action(
            &ProcessPrivilegedActionVerificationRequest::Confirm {
                web_auth_token,
                submission: ConfirmSubmission::VerifyHardwareSerial {
                    submission_data: VerifyHardwareSerialSubmission { serial: decorated },
                },
            },
        )
        .await;
    assert_eq!(resp.status_code, StatusCode::OK, "{}", resp.body_string);
    let attested = fetch_attested_serial(&bootstrap, &account_id, &keyset_id).await;
    assert!(matches!(attested, Some(AttestedHardwareSerial::Verified(_))));
}

/// Cancel path: CANCEL action on the priv-action transitions the record
/// to Canceled. The keyset's attested_hardware_serial stays Pending —
/// the next sweep attempt initiates a fresh VerifyHardwareSerial.
#[tokio::test]
async fn cancel_does_not_promote_keyset_attested_serial() {
    let overrides = GenServiceOverrides::new().feature_flags(flag_overrides());
    let (mut context, bootstrap) = gen_services_with_overrides(overrides).await;
    let client = TestClient::new(bootstrap.router.clone()).await;

    let (account_id, keyset_id) = onboard_enrolled_account(&mut context, &client).await;
    let web_auth_token =
        initiate_verify_hardware_serial(&bootstrap, &account_id).await;

    let resp = client
        .respond_to_out_of_band_privileged_action(
            &ProcessPrivilegedActionVerificationRequest::Cancel { web_auth_token },
        )
        .await;
    assert_eq!(resp.status_code, StatusCode::OK, "{}", resp.body_string);

    let attested = fetch_attested_serial(&bootstrap, &account_id, &keyset_id).await;
    assert!(
        matches!(attested, Some(AttestedHardwareSerial::Pending(_))),
        "keyset must remain Pending after Cancel, got {attested:?}",
    );
}

/// Exhaustion path: repeated mismatches eventually trip the attempt
/// counter (default max 5). The terminal mismatch returns 410 and the
/// record transitions to Failed.
#[tokio::test]
async fn repeated_mismatches_exhaust_attempts_and_return_410() {
    let overrides = GenServiceOverrides::new().feature_flags(flag_overrides());
    let (mut context, bootstrap) = gen_services_with_overrides(overrides).await;
    let client = TestClient::new(bootstrap.router.clone()).await;

    let (account_id, keyset_id) = onboard_enrolled_account(&mut context, &client).await;
    let web_auth_token =
        initiate_verify_hardware_serial(&bootstrap, &account_id).await;

    // Submit up to 10 wrong serials. Most builds default to max_attempts=5
    // so the 5th mismatch should trip the counter — but the loop tolerates
    // any small max_attempts by stopping the first time we see a 410.
    let mut saw_lockout = false;
    for _ in 0..10 {
        let resp = client
            .respond_to_out_of_band_privileged_action(
                &ProcessPrivilegedActionVerificationRequest::Confirm {
                    web_auth_token: web_auth_token.clone(),
                    submission: ConfirmSubmission::VerifyHardwareSerial {
                        submission_data: VerifyHardwareSerialSubmission {
                            serial: "WRONG-SERIAL".to_string(),
                        },
                    },
                },
            )
            .await;
        if resp.status_code == StatusCode::GONE {
            assert!(
                resp.body_string
                    .contains("OUT_OF_BAND_VERIFICATION_SESSION_ENDED"),
                "expected OUT_OF_BAND_VERIFICATION_SESSION_ENDED, got: {}",
                resp.body_string,
            );
            saw_lockout = true;
            break;
        }
        assert_eq!(
            resp.status_code,
            StatusCode::UNPROCESSABLE_ENTITY,
            "intermediate mismatches should be 422, got {}: {}",
            resp.status_code,
            resp.body_string,
        );
    }
    assert!(
        saw_lockout,
        "expected to hit attempts-exhausted within 10 tries",
    );

    let attested = fetch_attested_serial(&bootstrap, &account_id, &keyset_id).await;
    assert!(
        matches!(attested, Some(AttestedHardwareSerial::Pending(_))),
        "keyset must remain Pending even after attempts-exhausted, got {attested:?}",
    );
}

/// Lazy expiry: an OOB record whose `expiry_time` has passed is
/// transitioned to `Failed` on the next confirm read, and the same 410
/// `OUT_OF_BAND_VERIFICATION_SESSION_ENDED` error returned as the
/// max-attempts path. Keyset stays `Pending`; user can re-initiate.
#[tokio::test]
async fn expired_pending_oob_record_returns_410_on_confirm() {
    use std::sync::Arc;
    use time::Duration;

    let clock = Arc::new(crate::tests::lib::OffsetClock::new());
    let overrides = GenServiceOverrides::new()
        .feature_flags(flag_overrides())
        .clock(clock.clone());
    let (mut context, bootstrap) = gen_services_with_overrides(overrides).await;
    let client = TestClient::new(bootstrap.router.clone()).await;

    let (account_id, keyset_id) = onboard_enrolled_account(&mut context, &client).await;
    let web_auth_token = initiate_verify_hardware_serial(&bootstrap, &account_id).await;

    // Advance past the default 1-day expiry window set by
    // `initiate_out_of_band` in PR1.
    clock.add_offset(Duration::days(2));

    let resp = client
        .respond_to_out_of_band_privileged_action(
            &ProcessPrivilegedActionVerificationRequest::Confirm {
                web_auth_token,
                submission: ConfirmSubmission::VerifyHardwareSerial {
                    submission_data: VerifyHardwareSerialSubmission {
                        // A correct serial; the expiry should still
                        // win and reject the confirm.
                        serial: TEST_ACCOUNT_ATTESTED_SERIAL.to_string(),
                    },
                },
            },
        )
        .await;
    assert_eq!(resp.status_code, StatusCode::GONE, "{}", resp.body_string);
    assert!(
        resp.body_string
            .contains("OUT_OF_BAND_VERIFICATION_SESSION_ENDED"),
        "expected OUT_OF_BAND_VERIFICATION_SESSION_ENDED, got: {}",
        resp.body_string,
    );
    let attested = fetch_attested_serial(&bootstrap, &account_id, &keyset_id).await;
    assert!(
        matches!(attested, Some(AttestedHardwareSerial::Pending(_))),
        "keyset must remain Pending after expiry-triggered failure, got {attested:?}",
    );
}
