//! Tests for the hardware-verification enrollment gate at both touchpoints
//! (`create_account_v2`, `get_tokens`). LD test mode resolves flags from
//! the override map only; targeting-rule attributes are ignored, so the
//! `Bitkey-App-Installation-Id` header is the only one strictly required.

use std::collections::HashMap;

use account::attestation_verifier::test_fixture::TEST_ACCOUNT_ATTESTED_SERIAL;
use account::service::FetchAccountInput;
use authn_authz::routes::{
    AuthRequestKey, AuthenticationRequest, ChallengeResponseParameters, GetTokensRequest,
};
use bdk_utils::bdk::bitcoin::secp256k1::Secp256k1;
use bdk_utils::bdk::bitcoin::Network;
use bdk_utils::signature::message_to_digest;
use http::{HeaderMap, HeaderValue, StatusCode};
use onboarding::routes_v2::{CreateAccountRequestV2, UpgradeAccountRequestV2};
use types::account::entities::v2::{
    FullAccountAuthKeysInputV2, HardwareAttestation, SpendingKeysetInputV2,
    UpgradeLiteAccountAuthKeysInputV2,
};
use types::account::entities::{Account, HardwareType};
use types::account::spending::AttestedHardwareSerial;

use crate::tests::lib::{
    create_full_account_v2, create_lite_account, create_new_authkeys, create_pubkey,
};
use crate::tests::requests::axum::TestClient;
use crate::tests::{gen_services_with_overrides, TestContext};
use crate::GenServiceOverrides;

/// Magic attestation accepted on `is_test_account` requests when
/// hardware verification is required. Mirrors the on-the-wire bytes
/// of [`TEST_ACCOUNT_ATTESTATION_SIGNATURE_B64`] /
/// [`TEST_ACCOUNT_ATTESTATION_CERT_B64`] (= b"good").
fn magic_attestation() -> HardwareAttestation {
    HardwareAttestation {
        signature: b"good".to_vec(),
        cert_chain: vec![b"good".to_vec()],
    }
}

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

fn create_account_v2_request(
    context: &mut TestContext,
) -> (
    CreateAccountRequestV2,
    account::service::tests::TestAuthenticationKeys,
) {
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
            hardware_attestation: None,
        },
        is_test_account: true,
    };
    (request, keys)
}

/// Gate-trips path: 400 if attestation is missing. Implicitly confirms
/// the gate trips (otherwise we'd see 200 OK).
#[tokio::test]
async fn create_account_v2_requires_attestation_when_both_flags_on() {
    let overrides = GenServiceOverrides::new().feature_flags(flag_overrides(true, true));
    let (mut context, bootstrap) = gen_services_with_overrides(overrides).await;
    let client = TestClient::new(bootstrap.router).await;

    let (request, _keys) = create_account_v2_request(&mut context);
    let response = client
        .create_account_v2_with_headers(&mut context, experimentation_headers(), &request)
        .await;
    assert_eq!(response.status_code, StatusCode::BAD_REQUEST);
    assert!(
        response
            .body_string
            .contains("HARDWARE_ATTESTATION_REQUIRED"),
        "expected HARDWARE_ATTESTATION_REQUIRED, got: {}",
        response.body_string,
    );
}

/// Pins that the verifier runs on the path (garbage attestation → 400).
#[tokio::test]
async fn create_account_v2_rejects_invalid_attestation() {
    let overrides = GenServiceOverrides::new().feature_flags(flag_overrides(true, true));
    let (mut context, bootstrap) = gen_services_with_overrides(overrides).await;
    let client = TestClient::new(bootstrap.router).await;

    let (mut request, _keys) = create_account_v2_request(&mut context);
    request.spend.hardware_attestation = Some(HardwareAttestation {
        signature: vec![0u8; 64],
        cert_chain: vec![vec![0u8; 32], vec![0u8; 32]],
    });
    let response = client
        .create_account_v2_with_headers(&mut context, experimentation_headers(), &request)
        .await;
    assert_eq!(response.status_code, StatusCode::BAD_REQUEST);
    assert!(
        response
            .body_string
            .contains("HARDWARE_ATTESTATION_INVALID"),
        "expected HARDWARE_ATTESTATION_INVALID, got: {}",
        response.body_string,
    );
}

/// Pre-enrollment path: gate off → keyset persists with no attested serial.
#[tokio::test]
async fn create_account_v2_persists_no_attested_serial_when_gate_off() {
    let overrides = GenServiceOverrides::new().feature_flags(flag_overrides(false, true));
    let (mut context, bootstrap) = gen_services_with_overrides(overrides).await;
    let client = TestClient::new(bootstrap.router).await;

    let (request, _keys) = create_account_v2_request(&mut context);
    let response = client
        .create_account_v2_with_headers(&mut context, experimentation_headers(), &request)
        .await;
    assert_eq!(response.status_code, StatusCode::OK);
    let account_id = response.body.unwrap().account_id;

    let account = bootstrap
        .services
        .account_service
        .fetch_account(FetchAccountInput {
            account_id: &account_id,
        })
        .await
        .expect("account should be fetchable");
    let Account::Full(full) = account else {
        panic!("expected FullAccount");
    };
    let active = full
        .active_spending_keyset()
        .and_then(|k| k.optional_private_multi_sig())
        .expect("active keyset is private");
    assert!(
        active.attested_hardware_serial.is_none(),
        "pre-enrollment keysets must persist without attested_hardware_serial",
    );
}

/// Kill switch off must suppress enrollment even when version eligibility
/// would otherwise green-light it.
#[tokio::test]
async fn create_account_v2_does_not_enroll_when_kill_switch_off() {
    let overrides = GenServiceOverrides::new().feature_flags(flag_overrides(false, true));
    let (mut context, bootstrap) = gen_services_with_overrides(overrides).await;
    let client = TestClient::new(bootstrap.router).await;

    let (request, _keys) = create_account_v2_request(&mut context);
    let response = client
        .create_account_v2_with_headers(&mut context, experimentation_headers(), &request)
        .await;
    assert_eq!(response.status_code, StatusCode::OK);
    let account_id = response.body.unwrap().account_id;

    let account = bootstrap
        .services
        .account_service
        .fetch_account(FetchAccountInput {
            account_id: &account_id,
        })
        .await
        .expect("account should be fetchable");
    let Account::Full(full) = account else {
        panic!("expected FullAccount");
    };
    assert!(
        !full.hardware_verification_required,
        "kill switch off must suppress enrollment even when the version flag is on",
    );
}

#[tokio::test]
async fn get_tokens_enrolls_existing_unenrolled_account_when_both_flags_on() {
    let overrides = GenServiceOverrides::new().feature_flags(flag_overrides(true, true));
    let (mut context, bootstrap) = gen_services_with_overrides(overrides).await;
    let client = TestClient::new(bootstrap.router).await;

    // Build via the service helper so the account starts unenrolled (bypasses
    // the route-level gate at `create_account_v2`); we want to assert that
    // get_tokens is what flips the flag.
    let account = create_full_account_v2(
        &mut context,
        &bootstrap.services,
        types::account::bitcoin::Network::BitcoinSignet,
        None,
    )
    .await;
    assert!(
        !account.hardware_verification_required,
        "precondition: account starts unenrolled",
    );

    let keys = context
        .get_authentication_keys_for_account_id(&account.id)
        .expect("authentication keys");
    let auth_resp = client
        .authenticate(&AuthenticationRequest {
            auth_request_key: AuthRequestKey::HwPubkey(keys.hw.public_key),
        })
        .await;
    assert_eq!(auth_resp.status_code, StatusCode::OK);
    let auth_resp = auth_resp.body.expect("auth response body");

    let secp = Secp256k1::new();
    let message = message_to_digest(auth_resp.challenge.as_ref());
    let signature = secp.sign_ecdsa(&message, &keys.hw.secret_key);

    let tokens_resp = client
        .get_tokens_with_headers(
            experimentation_headers(),
            &GetTokensRequest {
                challenge: Some(ChallengeResponseParameters {
                    username: auth_resp.username,
                    challenge_response: signature.to_string(),
                    session: auth_resp.session,
                }),
                refresh_token: None,
            },
        )
        .await;
    assert_eq!(
        tokens_resp.status_code,
        StatusCode::OK,
        "{}",
        tokens_resp.body_string,
    );

    let refreshed = bootstrap
        .services
        .account_service
        .fetch_account(FetchAccountInput {
            account_id: &account.id,
        })
        .await
        .expect("account still fetchable after get_tokens");
    let Account::Full(full) = refreshed else {
        panic!("expected FullAccount");
    };
    assert!(
        full.hardware_verification_required,
        "get_tokens should have flipped hardware_verification_required when both flags are on",
    );
}

/// Magic-fixture happy path: a test account submitting the bypass
/// attestation persists `Pending(TEST_ACCOUNT_ATTESTED_SERIAL)` instead
/// of running the Silicon Labs verifier.
#[tokio::test]
async fn create_account_v2_accepts_magic_attestation_on_test_account() {
    let overrides = GenServiceOverrides::new().feature_flags(flag_overrides(true, true));
    let (mut context, bootstrap) = gen_services_with_overrides(overrides).await;
    let client = TestClient::new(bootstrap.router).await;

    let (mut request, _keys) = create_account_v2_request(&mut context);
    request.spend.hardware_attestation = Some(magic_attestation());
    assert!(
        request.is_test_account,
        "precondition: request is for a test account",
    );

    let response = client
        .create_account_v2_with_headers(&mut context, experimentation_headers(), &request)
        .await;
    assert_eq!(
        response.status_code,
        StatusCode::OK,
        "{}",
        response.body_string,
    );
    let account_id = response.body.unwrap().account_id;

    let account = bootstrap
        .services
        .account_service
        .fetch_account(FetchAccountInput {
            account_id: &account_id,
        })
        .await
        .expect("account should be fetchable");
    let Account::Full(full) = account else {
        panic!("expected FullAccount");
    };
    let active = full
        .active_spending_keyset()
        .and_then(|k| k.optional_private_multi_sig())
        .expect("active keyset is private");
    let serial = active
        .attested_hardware_serial
        .as_ref()
        .expect("magic attestation should persist an attested_hardware_serial");
    assert!(
        matches!(serial, AttestedHardwareSerial::Pending(s) if s == TEST_ACCOUNT_ATTESTED_SERIAL),
        "expected Pending({TEST_ACCOUNT_ATTESTED_SERIAL}), got {serial:?}",
    );
}

/// A test account that submits a non-magic, otherwise-invalid attestation
/// must still be rejected — the bypass only matches the exact fixture.
#[tokio::test]
async fn create_account_v2_rejects_non_magic_attestation_on_test_account() {
    let overrides = GenServiceOverrides::new().feature_flags(flag_overrides(true, true));
    let (mut context, bootstrap) = gen_services_with_overrides(overrides).await;
    let client = TestClient::new(bootstrap.router).await;

    let (mut request, _keys) = create_account_v2_request(&mut context);
    request.spend.hardware_attestation = Some(HardwareAttestation {
        // "good" prefix but garbage cert chain — must NOT short-circuit.
        signature: b"good".to_vec(),
        cert_chain: vec![vec![0u8; 32], vec![0u8; 32]],
    });
    let response = client
        .create_account_v2_with_headers(&mut context, experimentation_headers(), &request)
        .await;
    assert_eq!(response.status_code, StatusCode::BAD_REQUEST);
    assert!(
        response
            .body_string
            .contains("HARDWARE_ATTESTATION_INVALID"),
        "expected HARDWARE_ATTESTATION_INVALID, got: {}",
        response.body_string,
    );
}

/// Upgrade path: a lite test account upgrading with the magic fixture
/// persists the canned serial on the resulting full account's keyset.
#[tokio::test]
async fn upgrade_account_v2_accepts_magic_attestation_on_test_account() {
    let overrides = GenServiceOverrides::new().feature_flags(flag_overrides(true, true));
    let (mut context, bootstrap) = gen_services_with_overrides(overrides).await;
    let client = TestClient::new(bootstrap.router).await;

    let lite = create_lite_account(&mut context, &bootstrap.services, None, true).await;
    assert!(
        lite.common_fields.properties.is_test_account,
        "precondition: lite account is a test account",
    );

    let keys = create_new_authkeys(&mut context);
    let request = UpgradeAccountRequestV2 {
        auth: UpgradeLiteAccountAuthKeysInputV2 {
            app_pub: keys.app.public_key,
            hardware_pub: keys.hw.public_key,
            hardware_type: HardwareType::default(),
        },
        spend: SpendingKeysetInputV2 {
            network: Network::Signet,
            app_pub: create_pubkey(),
            hardware_pub: create_pubkey(),
            hardware_attestation: Some(magic_attestation()),
        },
    };
    let response = client
        .upgrade_account_v2_with_headers(
            &mut context,
            experimentation_headers(),
            &lite.id.to_string(),
            &request,
        )
        .await;
    assert_eq!(
        response.status_code,
        StatusCode::OK,
        "{}",
        response.body_string,
    );

    let account = bootstrap
        .services
        .account_service
        .fetch_account(FetchAccountInput {
            account_id: &lite.id,
        })
        .await
        .expect("account should be fetchable after upgrade");
    let Account::Full(full) = account else {
        panic!("expected FullAccount after upgrade");
    };
    let active = full
        .active_spending_keyset()
        .and_then(|k| k.optional_private_multi_sig())
        .expect("active keyset is private");
    let serial = active
        .attested_hardware_serial
        .as_ref()
        .expect("magic attestation should persist an attested_hardware_serial");
    assert!(
        matches!(serial, AttestedHardwareSerial::Pending(s) if s == TEST_ACCOUNT_ATTESTED_SERIAL),
        "expected Pending({TEST_ACCOUNT_ATTESTED_SERIAL}), got {serial:?}",
    );
}

/// create_keyset_v2 on an already-enrolled test account: the gate forces
/// attestation, magic fixture is accepted, new (inactive) keyset persists
/// with the canned serial.
#[tokio::test]
async fn create_keyset_v2_accepts_magic_attestation_on_enrolled_test_account() {
    let overrides = GenServiceOverrides::new().feature_flags(flag_overrides(true, true));
    let (mut context, bootstrap) = gen_services_with_overrides(overrides).await;
    let client = TestClient::new(bootstrap.router).await;

    let (mut create_request, _keys) = create_account_v2_request(&mut context);
    create_request.spend.hardware_attestation = Some(magic_attestation());
    let create_response = client
        .create_account_v2_with_headers(
            &mut context,
            experimentation_headers(),
            &create_request,
        )
        .await;
    assert_eq!(
        create_response.status_code,
        StatusCode::OK,
        "{}",
        create_response.body_string,
    );
    let account_id = create_response.body.unwrap().account_id;

    let new_keyset_request = SpendingKeysetInputV2 {
        network: Network::Signet,
        app_pub: create_pubkey(),
        hardware_pub: create_pubkey(),
        hardware_attestation: Some(magic_attestation()),
    };
    let keyset_response = client
        .create_keyset_v2_with_headers(
            &account_id.to_string(),
            experimentation_headers(),
            &new_keyset_request,
        )
        .await;
    assert_eq!(
        keyset_response.status_code,
        StatusCode::OK,
        "{}",
        keyset_response.body_string,
    );
    let new_keyset_id = keyset_response.body.unwrap().keyset_id;

    let account = bootstrap
        .services
        .account_service
        .fetch_account(FetchAccountInput {
            account_id: &account_id,
        })
        .await
        .expect("account should be fetchable");
    let Account::Full(full) = account else {
        panic!("expected FullAccount");
    };
    let new_keyset = full
        .spending_keysets
        .get(&new_keyset_id)
        .and_then(|k| k.optional_private_multi_sig())
        .expect("new keyset is private");
    let serial = new_keyset
        .attested_hardware_serial
        .as_ref()
        .expect("magic attestation should persist an attested_hardware_serial");
    assert!(
        matches!(serial, AttestedHardwareSerial::Pending(s) if s == TEST_ACCOUNT_ATTESTED_SERIAL),
        "expected Pending({TEST_ACCOUNT_ATTESTED_SERIAL}), got {serial:?}",
    );
}

/// Opportunistic verification: gate off, test account submits the magic
/// fixture → 200 OK and the keyset persists `Pending(TEST_…_SERIAL)`.
/// Lets attestation-capable clients populate the field before enforcement.
#[tokio::test]
async fn create_account_v2_persists_magic_attestation_when_gate_off() {
    let overrides = GenServiceOverrides::new().feature_flags(flag_overrides(false, false));
    let (mut context, bootstrap) = gen_services_with_overrides(overrides).await;
    let client = TestClient::new(bootstrap.router).await;

    let (mut request, _keys) = create_account_v2_request(&mut context);
    request.spend.hardware_attestation = Some(magic_attestation());
    let response = client
        .create_account_v2_with_headers(&mut context, experimentation_headers(), &request)
        .await;
    assert_eq!(
        response.status_code,
        StatusCode::OK,
        "{}",
        response.body_string,
    );
    let account_id = response.body.unwrap().account_id;

    let account = bootstrap
        .services
        .account_service
        .fetch_account(FetchAccountInput {
            account_id: &account_id,
        })
        .await
        .expect("account should be fetchable");
    let Account::Full(full) = account else {
        panic!("expected FullAccount");
    };
    let active = full
        .active_spending_keyset()
        .and_then(|k| k.optional_private_multi_sig())
        .expect("active keyset is private");
    let serial = active
        .attested_hardware_serial
        .as_ref()
        .expect("opportunistic magic attestation should persist a serial even when gate is off");
    assert!(
        matches!(serial, AttestedHardwareSerial::Pending(s) if s == TEST_ACCOUNT_ATTESTED_SERIAL),
        "expected Pending({TEST_ACCOUNT_ATTESTED_SERIAL}), got {serial:?}",
    );
}

/// Opportunistic verification: gate off, attestation submitted but
/// invalid → 200 OK, serial persists as `None`, no 400. Preserves the
/// "ignored when not required" contract for clients shipping experimental
/// or buggy attestation code.
#[tokio::test]
async fn create_account_v2_swallows_invalid_attestation_when_gate_off() {
    let overrides = GenServiceOverrides::new().feature_flags(flag_overrides(false, false));
    let (mut context, bootstrap) = gen_services_with_overrides(overrides).await;
    let client = TestClient::new(bootstrap.router).await;

    let (mut request, _keys) = create_account_v2_request(&mut context);
    request.spend.hardware_attestation = Some(HardwareAttestation {
        signature: vec![0u8; 64],
        cert_chain: vec![vec![0u8; 32], vec![0u8; 32]],
    });
    let response = client
        .create_account_v2_with_headers(&mut context, experimentation_headers(), &request)
        .await;
    assert_eq!(
        response.status_code,
        StatusCode::OK,
        "{}",
        response.body_string,
    );
    let account_id = response.body.unwrap().account_id;

    let account = bootstrap
        .services
        .account_service
        .fetch_account(FetchAccountInput {
            account_id: &account_id,
        })
        .await
        .expect("account should be fetchable");
    let Account::Full(full) = account else {
        panic!("expected FullAccount");
    };
    let active = full
        .active_spending_keyset()
        .and_then(|k| k.optional_private_multi_sig())
        .expect("active keyset is private");
    assert!(
        active.attested_hardware_serial.is_none(),
        "invalid opportunistic attestation must not persist a serial",
    );
}

/// Reuse path: when the active keyset's attestation is already
/// `Verified` and the user creates a new keyset on the same hardware,
/// the new keyset's attestation is persisted as `Verified` directly —
/// no re-OOBA required.
#[tokio::test]
async fn create_keyset_v2_inherits_verified_when_reusing_same_hardware() {
    let overrides = GenServiceOverrides::new().feature_flags(flag_overrides(true, true));
    let (mut context, bootstrap) = gen_services_with_overrides(overrides).await;
    let client = TestClient::new(bootstrap.router).await;

    // Onboard with magic attestation → active keyset has `Pending`.
    let (mut create_request, _keys) = create_account_v2_request(&mut context);
    create_request.spend.hardware_attestation = Some(magic_attestation());
    let create_response = client
        .create_account_v2_with_headers(
            &mut context,
            experimentation_headers(),
            &create_request,
        )
        .await;
    assert_eq!(create_response.status_code, StatusCode::OK);
    let account_id = create_response.body.unwrap().account_id;

    // Promote the active keyset's attestation to `Verified` directly
    // via the service helper, modeling the post-OOBA state.
    let full = match bootstrap
        .services
        .account_service
        .fetch_account(FetchAccountInput {
            account_id: &account_id,
        })
        .await
        .expect("fetch")
    {
        Account::Full(f) => f,
        _ => panic!("expected FullAccount"),
    };
    bootstrap
        .services
        .account_service
        .mark_hardware_serial_verified(&account_id, &full.active_keyset_id)
        .await
        .expect("pre-mark verified");

    // Create a new keyset on the same hardware (same magic attestation,
    // therefore same extracted serial = TEST_ACCOUNT_ATTESTED_SERIAL).
    let new_keyset_request = SpendingKeysetInputV2 {
        network: Network::Signet,
        app_pub: create_pubkey(),
        hardware_pub: create_pubkey(),
        hardware_attestation: Some(magic_attestation()),
    };
    let keyset_response = client
        .create_keyset_v2_with_headers(
            &account_id.to_string(),
            experimentation_headers(),
            &new_keyset_request,
        )
        .await;
    assert_eq!(
        keyset_response.status_code,
        StatusCode::OK,
        "{}",
        keyset_response.body_string,
    );
    let new_keyset_id = keyset_response.body.unwrap().keyset_id;

    let account = bootstrap
        .services
        .account_service
        .fetch_account(FetchAccountInput {
            account_id: &account_id,
        })
        .await
        .expect("fetch");
    let Account::Full(full) = account else {
        panic!("expected FullAccount");
    };
    let new_keyset = full
        .spending_keysets
        .get(&new_keyset_id)
        .and_then(|k| k.optional_private_multi_sig())
        .expect("new keyset is private");
    let serial = new_keyset
        .attested_hardware_serial
        .as_ref()
        .expect("new keyset must carry an attested serial");
    assert!(
        matches!(serial, AttestedHardwareSerial::Verified(s) if s == TEST_ACCOUNT_ATTESTED_SERIAL),
        "expected Verified({TEST_ACCOUNT_ATTESTED_SERIAL}) inherited from active keyset, got {serial:?}",
    );
}

/// Negative: when the active keyset is still `Pending` (not yet
/// OOBA-verified), a new keyset on the same hardware also lands as
/// `Pending`. Verification has to happen at least once.
#[tokio::test]
async fn create_keyset_v2_does_not_inherit_when_active_keyset_still_pending() {
    let overrides = GenServiceOverrides::new().feature_flags(flag_overrides(true, true));
    let (mut context, bootstrap) = gen_services_with_overrides(overrides).await;
    let client = TestClient::new(bootstrap.router).await;

    let (mut create_request, _keys) = create_account_v2_request(&mut context);
    create_request.spend.hardware_attestation = Some(magic_attestation());
    let create_response = client
        .create_account_v2_with_headers(
            &mut context,
            experimentation_headers(),
            &create_request,
        )
        .await;
    assert_eq!(create_response.status_code, StatusCode::OK);
    let account_id = create_response.body.unwrap().account_id;
    // NOTE: deliberately do NOT mark the active keyset verified.

    let new_keyset_request = SpendingKeysetInputV2 {
        network: Network::Signet,
        app_pub: create_pubkey(),
        hardware_pub: create_pubkey(),
        hardware_attestation: Some(magic_attestation()),
    };
    let keyset_response = client
        .create_keyset_v2_with_headers(
            &account_id.to_string(),
            experimentation_headers(),
            &new_keyset_request,
        )
        .await;
    assert_eq!(keyset_response.status_code, StatusCode::OK);
    let new_keyset_id = keyset_response.body.unwrap().keyset_id;

    let account = bootstrap
        .services
        .account_service
        .fetch_account(FetchAccountInput {
            account_id: &account_id,
        })
        .await
        .expect("fetch");
    let Account::Full(full) = account else {
        panic!("expected FullAccount");
    };
    let new_keyset = full
        .spending_keysets
        .get(&new_keyset_id)
        .and_then(|k| k.optional_private_multi_sig())
        .expect("new keyset is private");
    let serial = new_keyset
        .attested_hardware_serial
        .as_ref()
        .expect("new keyset must carry an attested serial");
    assert!(
        matches!(serial, AttestedHardwareSerial::Pending(_)),
        "new keyset must stay Pending when active keyset is still Pending, got {serial:?}",
    );
}

