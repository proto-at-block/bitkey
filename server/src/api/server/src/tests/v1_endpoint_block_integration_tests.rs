//! PR8: v1 keyset-creating endpoints are closed for
//! hardware-verification-enrolled accounts (409 ACCOUNT_ENROLLED_REQUIRES_V2),
//! so a downgrade to v1 can't mint an unattested legacy keyset that would
//! bypass the OOBA sweep gate.
//!
//! The block is gated on the `f8e-hardware-verification-enabled` kill switch,
//! exactly like the sweep gate it protects: with the switch off the sweep
//! gate stops enforcing, so there is no bypass to close and v1 reopens.

use std::collections::HashMap;

use http::StatusCode;

use account::service::tests::create_descriptor_keys;
use onboarding::routes::{CreateAccountRequest, CreateKeysetRequest};
use types::account::bitcoin::Network;
use types::account::entities::{FullAccountAuthKeysInput, HardwareType, SpendingKeysetInput};
use types::account::identifiers::AccountId;

use crate::tests::lib::create_new_authkeys;
use crate::tests::requests::axum::TestClient;
use crate::tests::{gen_services_with_overrides, TestContext};
use crate::{Bootstrap, GenServiceOverrides};

const ENROLLED_CODE: &str = "ACCOUNT_ENROLLED_REQUIRES_V2";
const HARDWARE_VERIFICATION_ENABLED_FLAG: &str = "f8e-hardware-verification-enabled";

fn flag_overrides(enabled: bool) -> HashMap<String, String> {
    HashMap::from([(
        HARDWARE_VERIFICATION_ENABLED_FLAG.to_string(),
        enabled.to_string(),
    )])
}

/// Build a v1 full-account create request with fresh keys.
fn full_account_request(context: &mut TestContext) -> CreateAccountRequest {
    let network = Network::BitcoinTest;
    let keys = create_new_authkeys(context);
    let (_, spend_app) = create_descriptor_keys(network);
    let (_, spend_hw) = create_descriptor_keys(network);
    CreateAccountRequest::Full {
        auth: FullAccountAuthKeysInput {
            app: keys.app.public_key,
            hardware: keys.hw.public_key,
            recovery: Some(keys.recovery.public_key),
            hardware_type: HardwareType::default(),
        },
        spending: SpendingKeysetInput {
            network: network.into(),
            app: spend_app,
            hardware: spend_hw,
        },
        is_test_account: true,
    }
}

fn create_keyset_request() -> CreateKeysetRequest {
    let network = Network::BitcoinTest;
    let (_, spend_app) = create_descriptor_keys(network);
    let (_, spend_hw) = create_descriptor_keys(network);
    CreateKeysetRequest {
        spending: SpendingKeysetInput {
            network: network.into(),
            app: spend_app,
            hardware: spend_hw,
        },
    }
}

async fn force_enroll(bootstrap: &Bootstrap, account_id: &AccountId) {
    let enrolled = bootstrap
        .services
        .account_service
        .maybe_mark_hardware_verification_required(account_id, |_| true)
        .await
        .expect("force enroll");
    assert!(enrolled, "account should have been force-enrolled");
}

/// v1 create-keyset on an enrolled account, kill switch on → 409.
#[tokio::test]
async fn create_keyset_v1_blocked_when_enrolled() {
    let overrides = GenServiceOverrides::new().feature_flags(flag_overrides(true));
    let (mut context, bootstrap) = gen_services_with_overrides(overrides).await;
    let client = TestClient::new(bootstrap.router.clone()).await;

    let request = full_account_request(&mut context);
    let resp = client.create_account(&mut context, &request).await;
    assert_eq!(resp.status_code, StatusCode::OK, "{}", resp.body_string);
    let account_id = resp.body.unwrap().account_id;
    force_enroll(&bootstrap, &account_id).await;

    let resp = client
        .create_keyset(&account_id.to_string(), &create_keyset_request())
        .await;
    assert_eq!(resp.status_code, StatusCode::CONFLICT, "{}", resp.body_string);
    assert!(
        resp.body_string.contains(ENROLLED_CODE),
        "expected {ENROLLED_CODE}, got: {}",
        resp.body_string,
    );
}

/// v1 create-account (idempotent re-create of an existing, now-enrolled
/// account) with kill switch on → 409 rather than returning the existing
/// keyset.
#[tokio::test]
async fn create_account_v1_blocked_when_enrolled() {
    let overrides = GenServiceOverrides::new().feature_flags(flag_overrides(true));
    let (mut context, bootstrap) = gen_services_with_overrides(overrides).await;
    let client = TestClient::new(bootstrap.router.clone()).await;

    let request = full_account_request(&mut context);
    let resp = client.create_account(&mut context, &request).await;
    assert_eq!(resp.status_code, StatusCode::OK, "{}", resp.body_string);
    let account_id = resp.body.unwrap().account_id;
    force_enroll(&bootstrap, &account_id).await;

    // Same keys → idempotent path resolves the existing (now enrolled)
    // full account, which must be rejected.
    let resp = client.create_account(&mut context, &request).await;
    assert_eq!(resp.status_code, StatusCode::CONFLICT, "{}", resp.body_string);
    assert!(
        resp.body_string.contains(ENROLLED_CODE),
        "expected {ENROLLED_CODE}, got: {}",
        resp.body_string,
    );
}

/// Control: a non-enrolled account is unaffected even with the kill switch
/// on — v1 create-keyset still succeeds.
#[tokio::test]
async fn create_keyset_v1_allowed_when_not_enrolled() {
    let overrides = GenServiceOverrides::new().feature_flags(flag_overrides(true));
    let (mut context, bootstrap) = gen_services_with_overrides(overrides).await;
    let client = TestClient::new(bootstrap.router.clone()).await;

    let request = full_account_request(&mut context);
    let resp = client.create_account(&mut context, &request).await;
    assert_eq!(resp.status_code, StatusCode::OK, "{}", resp.body_string);
    let account_id = resp.body.unwrap().account_id;

    let resp = client
        .create_keyset(&account_id.to_string(), &create_keyset_request())
        .await;
    assert_eq!(resp.status_code, StatusCode::OK, "{}", resp.body_string);
}

/// Kill switch *deliberately* off (`Ok(false)`): the whole feature reverts to
/// pre-feature behavior, so an enrolled account's v1 create-keyset is allowed
/// again (the sweep gate is likewise inert, so there is no bypass to close).
#[tokio::test]
async fn create_keyset_v1_allowed_when_enrolled_but_killswitch_off() {
    let overrides = GenServiceOverrides::new().feature_flags(flag_overrides(false));
    let (mut context, bootstrap) = gen_services_with_overrides(overrides).await;
    let client = TestClient::new(bootstrap.router.clone()).await;

    let request = full_account_request(&mut context);
    let resp = client.create_account(&mut context, &request).await;
    assert_eq!(resp.status_code, StatusCode::OK, "{}", resp.body_string);
    let account_id = resp.body.unwrap().account_id;
    force_enroll(&bootstrap, &account_id).await;

    let resp = client
        .create_keyset(&account_id.to_string(), &create_keyset_request())
        .await;
    assert_eq!(resp.status_code, StatusCode::OK, "{}", resp.body_string);
}

/// Fail closed: when the kill switch can't be resolved (here, flag absent →
/// `EvalError::FlagNotFound`, the same `Err` an LD outage yields), an enrolled
/// account stays blocked. Only an affirmative `false` reopens v1 — an unknown
/// value must not mint a durable unattested keyset that outlives the outage.
#[tokio::test]
async fn create_keyset_v1_blocked_when_enrolled_and_flag_unresolvable() {
    // No hardware-verification flag in the override map → resolution errors.
    let overrides = GenServiceOverrides::new().feature_flags(HashMap::new());
    let (mut context, bootstrap) = gen_services_with_overrides(overrides).await;
    let client = TestClient::new(bootstrap.router.clone()).await;

    let request = full_account_request(&mut context);
    let resp = client.create_account(&mut context, &request).await;
    assert_eq!(resp.status_code, StatusCode::OK, "{}", resp.body_string);
    let account_id = resp.body.unwrap().account_id;
    force_enroll(&bootstrap, &account_id).await;

    let resp = client
        .create_keyset(&account_id.to_string(), &create_keyset_request())
        .await;
    assert_eq!(resp.status_code, StatusCode::CONFLICT, "{}", resp.body_string);
    assert!(
        resp.body_string.contains(ENROLLED_CODE),
        "expected {ENROLLED_CODE}, got: {}",
        resp.body_string,
    );
}
