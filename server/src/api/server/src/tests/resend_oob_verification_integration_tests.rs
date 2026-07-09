//! Resend of the out-of-band verification email for a pending privileged
//! action instance: POST .../privileged-actions/{instance_id}/resend.
//!
//! Uses the transaction-verification-policy loosen flow as a generic source
//! of a pending OOB instance (the resend endpoint is action-agnostic), and an
//! `OffsetClock` to drive the per-instance cooldown.

use std::sync::Arc;

use http::StatusCode;
use serde_json::Value;
use time::Duration;

use notification::service::FetchForAccountInput;
use notification::NotificationPayloadType;
use types::account::entities::Account;
use types::account::identifiers::AccountId;
use types::account::AccountType;
use types::privileged_action::repository::{
    AuthorizationStrategyRecord, PrivilegedActionInstanceRecord, DEFAULT_OOB_MAX_RESENDS,
};
use types::privileged_action::router::generic::PrivilegedActionResponse;
use types::privileged_action::shared::PrivilegedActionInstanceId;
use types::transaction_verification::entities::PolicyUpdate;
use types::transaction_verification::router::PutTransactionVerificationPolicyRequest;

use crate::tests::gen_services_with_overrides;
use crate::tests::lib::{create_account, create_email_touchpoint, OffsetClock};
use crate::tests::requests::axum::TestClient;
use crate::tests::TestContext;
use crate::{Bootstrap, GenServiceOverrides};

/// 30s `RESEND_COOLDOWN` + margin.
const PAST_COOLDOWN: Duration = Duration::seconds(31);

/// Create a Full account with an email touchpoint and initiate a pending
/// out-of-band instance by loosening the transaction-verification policy
/// (tighten to `Always` first so the subsequent `Never` is a genuine loosen,
/// which is the privileged action). Returns the account and the new instance.
async fn initiate_pending_oob(
    context: &mut TestContext,
    bootstrap: &Bootstrap,
    client: &TestClient,
) -> (Account, PrivilegedActionInstanceId) {
    let account = create_account(context, &bootstrap.services, AccountType::Full, false).await;
    create_email_touchpoint(&bootstrap.services, account.get_id(), true).await;
    let keys = context
        .get_authentication_keys_for_account_id(account.get_id())
        .unwrap();

    client
        .update_transaction_verification_policy(
            account.get_id(),
            true,
            false,
            &keys,
            &PutTransactionVerificationPolicyRequest {
                policy: PolicyUpdate::Always,
                use_bip_177: false,
            },
        )
        .await;
    let put_resp = client
        .update_transaction_verification_policy(
            account.get_id(),
            true,
            true,
            &keys,
            &PutTransactionVerificationPolicyRequest {
                policy: PolicyUpdate::Never,
                use_bip_177: false,
            },
        )
        .await;
    let PrivilegedActionResponse::Pending(pending) = put_resp.body.unwrap() else {
        panic!("expected a Pending out-of-band response: {}", put_resp.body_string);
    };
    (account, pending.privileged_action_instance.id)
}

/// Count `PrivilegedActionPendingOutOfBandVerification` customer notifications
/// for the account (one per send; the account has a single touchpoint).
async fn pending_oob_notification_count(bootstrap: &Bootstrap, account_id: &AccountId) -> usize {
    bootstrap
        .services
        .notification_service
        .fetch_customer_for_account(FetchForAccountInput {
            account_id: account_id.clone(),
        })
        .await
        .unwrap()
        .iter()
        .filter(|n| {
            n.payload_type == NotificationPayloadType::PrivilegedActionPendingOutOfBandVerification
        })
        .count()
}

async fn web_auth_token(bootstrap: &Bootstrap, instance_id: &PrivilegedActionInstanceId) -> String {
    let record: PrivilegedActionInstanceRecord<Value> = bootstrap
        .services
        .privileged_action_service
        .privileged_action_repository
        .fetch_by_id(instance_id)
        .await
        .expect("fetch instance");
    let AuthorizationStrategyRecord::OutOfBand(oob) = record.authorization_strategy else {
        panic!("expected an out-of-band instance");
    };
    oob.web_auth_token
}

/// Happy path: after the cooldown, resend sends a second email and keeps the
/// same `web_auth_token` (old links stay valid).
#[tokio::test]
async fn resend_sends_second_email_reusing_token() {
    let clock = Arc::new(OffsetClock::new());
    let (mut context, bootstrap) =
        gen_services_with_overrides(GenServiceOverrides::new().clock(clock.clone())).await;
    let client = TestClient::new(bootstrap.router.clone()).await;

    let (account, instance_id) = initiate_pending_oob(&mut context, &bootstrap, &client).await;
    assert_eq!(pending_oob_notification_count(&bootstrap, account.get_id()).await, 1);
    let token_before = web_auth_token(&bootstrap, &instance_id).await;

    clock.add_offset(PAST_COOLDOWN);
    let resp = client
        .resend_out_of_band_verification(&account.get_id().to_string(), &instance_id.to_string())
        .await;
    assert_eq!(resp.status_code, StatusCode::OK, "{}", resp.body_string);

    assert_eq!(pending_oob_notification_count(&bootstrap, account.get_id()).await, 2);
    assert_eq!(
        web_auth_token(&bootstrap, &instance_id).await,
        token_before,
        "resend must reuse the existing web_auth_token",
    );
}

/// A resend inside the cooldown is rejected (429); after the cooldown it
/// succeeds.
#[tokio::test]
async fn resend_within_cooldown_is_throttled() {
    let clock = Arc::new(OffsetClock::new());
    let (mut context, bootstrap) =
        gen_services_with_overrides(GenServiceOverrides::new().clock(clock.clone())).await;
    let client = TestClient::new(bootstrap.router.clone()).await;

    let (account, instance_id) = initiate_pending_oob(&mut context, &bootstrap, &client).await;
    let account_id = account.get_id().to_string();

    // Immediately after the initial send → still in cooldown.
    let resp = client
        .resend_out_of_band_verification(&account_id, &instance_id.to_string())
        .await;
    assert_eq!(resp.status_code, StatusCode::TOO_MANY_REQUESTS, "{}", resp.body_string);
    assert!(
        resp.body_string.contains("OUT_OF_BAND_RESEND_THROTTLED"),
        "got: {}",
        resp.body_string,
    );
    assert_eq!(pending_oob_notification_count(&bootstrap, account.get_id()).await, 1);

    clock.add_offset(PAST_COOLDOWN);
    let resp = client
        .resend_out_of_band_verification(&account_id, &instance_id.to_string())
        .await;
    assert_eq!(resp.status_code, StatusCode::OK, "{}", resp.body_string);
    assert_eq!(pending_oob_notification_count(&bootstrap, account.get_id()).await, 2);
}

/// Resends are capped: after `DEFAULT_OOB_MAX_RESENDS` successful resends, the
/// next is rejected (409).
#[tokio::test]
async fn resend_is_capped_at_max() {
    let clock = Arc::new(OffsetClock::new());
    let (mut context, bootstrap) =
        gen_services_with_overrides(GenServiceOverrides::new().clock(clock.clone())).await;
    let client = TestClient::new(bootstrap.router.clone()).await;

    let (account, instance_id) = initiate_pending_oob(&mut context, &bootstrap, &client).await;
    let account_id = account.get_id().to_string();

    for _ in 0..DEFAULT_OOB_MAX_RESENDS {
        clock.add_offset(PAST_COOLDOWN);
        let resp = client
            .resend_out_of_band_verification(&account_id, &instance_id.to_string())
            .await;
        assert_eq!(resp.status_code, StatusCode::OK, "{}", resp.body_string);
    }

    clock.add_offset(PAST_COOLDOWN);
    let resp = client
        .resend_out_of_band_verification(&account_id, &instance_id.to_string())
        .await;
    assert_eq!(resp.status_code, StatusCode::CONFLICT, "{}", resp.body_string);
    assert!(
        resp.body_string.contains("OUT_OF_BAND_RESEND_LIMIT_EXCEEDED"),
        "got: {}",
        resp.body_string,
    );

    // 1 initial + DEFAULT_OOB_MAX_RESENDS resends; the capped attempt sends nothing.
    assert_eq!(
        pending_oob_notification_count(&bootstrap, account.get_id()).await,
        1 + DEFAULT_OOB_MAX_RESENDS as usize,
    );
}

/// A canceled (non-pending) instance can't be resent.
#[tokio::test]
async fn resend_rejected_when_not_pending() {
    let clock = Arc::new(OffsetClock::new());
    let (mut context, bootstrap) =
        gen_services_with_overrides(GenServiceOverrides::new().clock(clock.clone())).await;
    let client = TestClient::new(bootstrap.router.clone()).await;

    let (account, instance_id) = initiate_pending_oob(&mut context, &bootstrap, &client).await;
    let account_id = account.get_id().to_string();

    let resp = client
        .cancel_pending_out_of_band_instance(&account_id, &instance_id.to_string())
        .await;
    assert_eq!(resp.status_code, StatusCode::OK, "{}", resp.body_string);

    clock.add_offset(PAST_COOLDOWN);
    let resp = client
        .resend_out_of_band_verification(&account_id, &instance_id.to_string())
        .await;
    assert_eq!(resp.status_code, StatusCode::CONFLICT, "{}", resp.body_string);
}

/// A different account cannot resend another account's instance.
#[tokio::test]
async fn resend_rejected_for_wrong_account() {
    let clock = Arc::new(OffsetClock::new());
    let (mut context, bootstrap) =
        gen_services_with_overrides(GenServiceOverrides::new().clock(clock.clone())).await;
    let client = TestClient::new(bootstrap.router.clone()).await;

    let (_owner, instance_id) = initiate_pending_oob(&mut context, &bootstrap, &client).await;
    let other = create_account(&mut context, &bootstrap.services, AccountType::Full, false).await;

    clock.add_offset(PAST_COOLDOWN);
    let resp = client
        .resend_out_of_band_verification(&other.get_id().to_string(), &instance_id.to_string())
        .await;
    assert_eq!(resp.status_code, StatusCode::FORBIDDEN, "{}", resp.body_string);
}
