//! Tests for the PR9 sweep OOBA gate at the sign-transaction endpoint.
//!
//! The gate runs *before* PSBT parsing/validation, so these tests use a
//! placeholder PSBT and assert on the gate's decision directly: whether a
//! `VerifyHardwareSerial` privileged action was initiated. The signing
//! machinery itself is covered by `transaction_integration_tests`.
//!
//! Topology: onboard an enrolled test account (active keyset K0 lands
//! `Pending` via the magic attestation), then create a second inactive
//! keyset K1. Signing the *inactive* K1 is a sweep whose *destination* is
//! the active keyset K0 — which is what the gate inspects.

use std::collections::HashMap;

use http::{HeaderMap, HeaderValue, StatusCode};
use mobile_pay::routes::SignTransactionData;
use onboarding::routes_v2::CreateAccountRequestV2;
use types::account::entities::v2::{
    FullAccountAuthKeysInputV2, HardwareAttestation, SpendingKeysetInputV2,
};
use types::account::entities::HardwareType;
use types::account::identifiers::{AccountId, KeysetId};
use types::privileged_action::router::AuthorizationStrategyDiscriminants;
use types::privileged_action::shared::PrivilegedActionType;

use bdk_utils::bdk::bitcoin::Network;

use crate::tests::lib::{create_new_authkeys, create_pubkey};
use crate::tests::requests::axum::TestClient;
use crate::tests::requests::Response;
use crate::tests::{gen_services_with_overrides, TestContext};
use crate::{Bootstrap, GenServiceOverrides};

const HARDWARE_VERIFICATION_ENABLED_FLAG: &str = "f8e-hardware-verification-enabled";
const HARDWARE_VERIFICATION_REQUIRED_FLAG: &str = "f8e-hardware-verification-required";

fn flag_overrides(enabled: bool, required: bool) -> HashMap<String, String> {
    HashMap::from([
        (
            HARDWARE_VERIFICATION_ENABLED_FLAG.to_string(),
            enabled.to_string(),
        ),
        (
            HARDWARE_VERIFICATION_REQUIRED_FLAG.to_string(),
            required.to_string(),
        ),
        // Server signing on by default — the gate now resolves this kill
        // switch (panics if unset, like the impl's check). Individual
        // tests override it to exercise the disabled path.
        ("f8e-mobile-pay-enabled".to_string(), "true".to_string()),
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

/// Placeholder PSBT — the gate decision precedes any PSBT parsing, so the
/// gated paths never look at it. Not a valid transaction.
const PLACEHOLDER_PSBT: &str = "cHNidP8BAAA=";

/// Onboard an enrolled test account with the magic attestation. The
/// active keyset (returned id) lands `Pending`.
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
        response.body_string
    );
    let body = response.body.unwrap();
    (body.account_id, body.keyset_id)
}

/// Create a second (inactive) keyset on an enrolled account, carrying the
/// magic attestation so it lands `Pending`. Returns its id; this becomes
/// the sweep *source* (signing a non-active keyset).
async fn create_inactive_keyset(client: &TestClient, account_id: &AccountId) -> KeysetId {
    let request = SpendingKeysetInputV2 {
        network: Network::Signet,
        app_pub: create_pubkey(),
        hardware_pub: create_pubkey(),
        hardware_attestation: Some(magic_attestation()),
    };
    let response = client
        .create_keyset_v2_with_headers(
            &account_id.to_string(),
            experimentation_headers(),
            &request,
        )
        .await;
    assert_eq!(
        response.status_code,
        StatusCode::OK,
        "{}",
        response.body_string
    );
    response.body.unwrap().keyset_id
}

async fn pending_verify_hardware_serial_count(bootstrap: &Bootstrap, account_id: &AccountId) -> usize {
    bootstrap
        .services
        .privileged_action_service
        .privileged_action_repository
        .fetch_for_account_id::<()>(
            account_id,
            Some(AuthorizationStrategyDiscriminants::OutOfBand),
            Some(PrivilegedActionType::VerifyHardwareSerial),
            Some(types::privileged_action::repository::RecordStatus::Pending),
        )
        .await
        .expect("fetch instances")
        .len()
}

async fn sign(
    client: &TestClient,
    account_id: &AccountId,
    keyset_id: &KeysetId,
) -> Response<mobile_pay::routes::SignTransactionResponse> {
    client
        .sign_transaction_with_keyset(
            account_id,
            keyset_id,
            &SignTransactionData {
                psbt: PLACEHOLDER_PSBT.to_string(),
                ..Default::default()
            },
        )
        .await
}

/// Sweep into a `Pending` destination on an enrolled account with the
/// kill switch on → gated: 200 Pending response + a VerifyHardwareSerial
/// instance is created. (Signing the inactive K1 sweeps into active K0.)
#[tokio::test]
async fn sweep_into_pending_destination_initiates_ooba() {
    let overrides = GenServiceOverrides::new().feature_flags(flag_overrides(true, true));
    let (mut context, bootstrap) = gen_services_with_overrides(overrides).await;
    let client = TestClient::new(bootstrap.router.clone()).await;

    let (account_id, _active_k0) = onboard_enrolled_account(&mut context, &client).await;
    let source_k1 = create_inactive_keyset(&client, &account_id).await;

    let resp = sign(&client, &account_id, &source_k1).await;
    assert_eq!(resp.status_code, StatusCode::OK, "{}", resp.body_string);
    assert!(
        resp.body_string.contains("privileged_action_instance"),
        "expected a Pending privileged-action response, got: {}",
        resp.body_string,
    );
    assert_eq!(
        pending_verify_hardware_serial_count(&bootstrap, &account_id).await,
        1,
        "exactly one VerifyHardwareSerial instance should be pending",
    );
}

/// Repeated gated sweeps must not mint a second OOB record / email — the
/// concurrency guard returns the existing Pending instance.
#[tokio::test]
async fn repeated_gated_sweeps_do_not_duplicate_ooba() {
    let overrides = GenServiceOverrides::new().feature_flags(flag_overrides(true, true));
    let (mut context, bootstrap) = gen_services_with_overrides(overrides).await;
    let client = TestClient::new(bootstrap.router.clone()).await;

    let (account_id, _active_k0) = onboard_enrolled_account(&mut context, &client).await;
    let source_k1 = create_inactive_keyset(&client, &account_id).await;

    for _ in 0..3 {
        let resp = sign(&client, &account_id, &source_k1).await;
        assert_eq!(resp.status_code, StatusCode::OK, "{}", resp.body_string);
    }
    assert_eq!(
        pending_verify_hardware_serial_count(&bootstrap, &account_id).await,
        1,
        "concurrency guard must keep a single pending instance across retries",
    );
}

/// Signing the active keyset is mobile-pay, not a sweep → never gated,
/// even when enrolled + flag on.
#[tokio::test]
async fn mobile_pay_on_active_keyset_is_not_gated() {
    let overrides = GenServiceOverrides::new().feature_flags(flag_overrides(true, true));
    let (mut context, bootstrap) = gen_services_with_overrides(overrides).await;
    let client = TestClient::new(bootstrap.router.clone()).await;

    let (account_id, active_k0) = onboard_enrolled_account(&mut context, &client).await;

    let _ = sign(&client, &account_id, &active_k0).await;
    assert_eq!(
        pending_verify_hardware_serial_count(&bootstrap, &account_id).await,
        0,
        "mobile-pay (signing the active keyset) must not initiate OOBA",
    );
}

/// Sweep into an already-`Verified` destination → not gated.
#[tokio::test]
async fn sweep_into_verified_destination_is_not_gated() {
    let overrides = GenServiceOverrides::new().feature_flags(flag_overrides(true, true));
    let (mut context, bootstrap) = gen_services_with_overrides(overrides).await;
    let client = TestClient::new(bootstrap.router.clone()).await;

    let (account_id, active_k0) = onboard_enrolled_account(&mut context, &client).await;
    let source_k1 = create_inactive_keyset(&client, &account_id).await;
    // Promote the active (destination) keyset to Verified.
    bootstrap
        .services
        .account_service
        .mark_hardware_serial_verified(&account_id, &active_k0)
        .await
        .expect("mark active keyset verified");

    let _ = sign(&client, &account_id, &source_k1).await;
    assert_eq!(
        pending_verify_hardware_serial_count(&bootstrap, &account_id).await,
        0,
        "a verified destination must not re-trigger OOBA",
    );
}

/// Server-signing kill switch (`f8e-mobile-pay-enabled`) off → the
/// request fails fast with 403 *before* the OOBA gate, so no
/// hardware-verification action is initiated for a co-sign that can
/// never proceed.
#[tokio::test]
async fn server_signing_disabled_does_not_initiate_ooba() {
    let mut flags = flag_overrides(true, true);
    flags.insert("f8e-mobile-pay-enabled".to_string(), "false".to_string());
    let overrides = GenServiceOverrides::new().feature_flags(flags);
    let (mut context, bootstrap) = gen_services_with_overrides(overrides).await;
    let client = TestClient::new(bootstrap.router.clone()).await;

    let (account_id, _active_k0) = onboard_enrolled_account(&mut context, &client).await;
    let source_k1 = create_inactive_keyset(&client, &account_id).await;

    let resp = sign(&client, &account_id, &source_k1).await;
    assert_eq!(
        resp.status_code,
        StatusCode::FORBIDDEN,
        "{}",
        resp.body_string
    );
    assert_eq!(
        pending_verify_hardware_serial_count(&bootstrap, &account_id).await,
        0,
        "disabled server signing must not initiate OOBA",
    );
}

/// Live kill switch off at sign time → not gated even for an enrolled
/// account whose destination keyset is `Pending`. The account is
/// force-enrolled (the flag is off, so onboarding wouldn't enroll it),
/// isolating the sign-time `HARDWARE_VERIFICATION_ENABLED` check from the
/// per-account enrollment flag.
#[tokio::test]
async fn sweep_not_gated_when_kill_switch_off() {
    // Kill switch off. Magic attestation on a test account still lands the
    // keysets `Pending` (opportunistic), but the account won't auto-enroll.
    let overrides = GenServiceOverrides::new().feature_flags(flag_overrides(false, false));
    let (mut context, bootstrap) = gen_services_with_overrides(overrides).await;
    let client = TestClient::new(bootstrap.router.clone()).await;

    let (account_id, _active_k0) = onboard_enrolled_account(&mut context, &client).await;
    let source_k1 = create_inactive_keyset(&client, &account_id).await;
    // Force the per-account enrollment flag on so only the kill switch
    // distinguishes this from the gated case.
    let enrolled = bootstrap
        .services
        .account_service
        .maybe_mark_hardware_verification_required(&account_id, |_| true)
        .await
        .expect("force enroll");
    assert!(enrolled, "account should have been force-enrolled");

    let _ = sign(&client, &account_id, &source_k1).await;
    assert_eq!(
        pending_verify_hardware_serial_count(&bootstrap, &account_id).await,
        0,
        "live kill switch off must suppress OOBA even when enrolled",
    );
}

/// Kill switch *unresolvable* at sign time (flag absent → `FlagNotFound`, the
/// same `Err` an LD outage yields) → fails closed: an enrolled account with a
/// `Pending` destination is still gated into OOBA. Only an affirmative `false`
/// (see [`sweep_not_gated_when_kill_switch_off`]) suppresses the gate; an
/// unknown switch value must not let an unverified sweep through.
#[tokio::test]
async fn sweep_gated_when_kill_switch_unresolvable() {
    // Server signing on (else 403 before the gate); both hardware-verification
    // flags absent so the kill-switch resolution errors.
    let overrides = GenServiceOverrides::new().feature_flags(HashMap::from([(
        "f8e-mobile-pay-enabled".to_string(),
        "true".to_string(),
    )]));
    let (mut context, bootstrap) = gen_services_with_overrides(overrides).await;
    let client = TestClient::new(bootstrap.router.clone()).await;

    let (account_id, _active_k0) = onboard_enrolled_account(&mut context, &client).await;
    let source_k1 = create_inactive_keyset(&client, &account_id).await;
    // Force the per-account enrollment flag on (the flag being absent, onboarding
    // wouldn't enroll), so only the unresolvable kill switch is under test.
    let enrolled = bootstrap
        .services
        .account_service
        .maybe_mark_hardware_verification_required(&account_id, |_| true)
        .await
        .expect("force enroll");
    assert!(enrolled, "account should have been force-enrolled");

    let resp = sign(&client, &account_id, &source_k1).await;
    assert_eq!(resp.status_code, StatusCode::OK, "{}", resp.body_string);
    assert!(
        resp.body_string.contains("privileged_action_instance"),
        "expected a Pending privileged-action response, got: {}",
        resp.body_string,
    );
    assert_eq!(
        pending_verify_hardware_serial_count(&bootstrap, &account_id).await,
        1,
        "an unresolvable kill switch must fail closed and gate the sweep",
    );
}
