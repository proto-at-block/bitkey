use std::collections::{HashMap, HashSet};

use errors::ApiError;
use instrumentation::metrics::KeyValue;
use instrumentation::middleware::CLIENT_REQUEST_CONTEXT;
use notification::clients::iterable::{
    IterableClient, IterableUserId, ACCOUNT_ID_KEY, PLACEHOLDER_EMAIL_ADDRESS, TOUCHPOINT_ID_KEY,
    USER_SCOPE_KEY,
};
use types::account::identifiers::AccountId;
use types::account::identifiers::TouchpointId;
use types::notification::NotificationCategory;

use crate::metrics::{
    V1HardwareKeysetOperation, V1HardwareKeysetOutcome, V2HardwareAttestationNetwork,
    V2HardwareAttestationOperation, APP_ID_KEY, KEYSET_CREATED, KEYSET_TYPE_KEY,
    TEST_HARDWARE_ATTESTATION_FIXTURE_USE, V1_HARDWARE_KEYSET_USE, V1_OPERATION_KEY,
    V1_OUTCOME_KEY, V2_NETWORK_KEY, V2_OPERATION_KEY,
};

pub mod account_validation;
pub(crate) mod metrics;
pub mod routes;
pub mod routes_v2;

async fn upsert_account_iterable_user(
    iterable_client: &IterableClient,
    account_id: &AccountId,
    touchpoint_id: Option<&TouchpointId>,
    email_address: Option<String>,
) -> Result<(), ApiError> {
    // Account-scoped Iterable user; this is the primary target for sending emails. The email address on
    // this user only gets updated when a new email address is activated for the account.

    let email_address = email_address.unwrap_or_else(|| PLACEHOLDER_EMAIL_ADDRESS.to_string());

    let mut data_fields = HashMap::from([(USER_SCOPE_KEY, "account")]);

    let touchpoint_id_str = &touchpoint_id.map_or(Default::default(), |id| id.to_string());
    if touchpoint_id.is_some() {
        data_fields.insert(TOUCHPOINT_ID_KEY, touchpoint_id_str);
    }

    iterable_client
        .update_user(
            IterableUserId::Account(account_id),
            email_address,
            Some(data_fields),
        )
        .await?;

    Ok(())
}

async fn create_touchpoint_iterable_user(
    iterable_client: &IterableClient,
    account_id: &AccountId,
    touchpoint_id: &TouchpointId,
    email_address: String,
) -> Result<(), ApiError> {
    // Touchpoint-scoped Iterable user; this user is only used for sending the verification OTP. Since this
    // happens before an email address is activated, we can't use the account-scoped Iterable user. So this
    // operates as sort of a staging user for the account's pending email address change. This allows us to
    // send an OTP to a new email address while there's still an existing active one on the account-scoped
    // user.
    iterable_client
        .update_user(
            IterableUserId::Touchpoint(touchpoint_id),
            email_address,
            Some(HashMap::from([
                (ACCOUNT_ID_KEY, account_id.to_string().as_str()),
                (USER_SCOPE_KEY, "touchpoint"),
            ])),
        )
        .await?;

    // Subscribe the touchpoint user to account security so it can receive the OTP.
    iterable_client
        .set_initial_subscribed_notification_categories(
            IterableUserId::Touchpoint(touchpoint_id),
            HashSet::from([NotificationCategory::AccountSecurity]),
        )
        .await?;

    Ok(())
}

fn emit_keyset_created(keyset_type: &str) {
    let mut attributes = vec![KeyValue::new(KEYSET_TYPE_KEY, keyset_type.to_string())];

    if let Ok(Some(app_id)) = CLIENT_REQUEST_CONTEXT.try_with(|c| c.app_id.clone()) {
        attributes.push(KeyValue::new(APP_ID_KEY, app_id));
    }
    KEYSET_CREATED.add(1, &attributes);
}

// `attempted` is the request denominator; `created` and `existing` are terminal subsets.
fn emit_v1_hardware_keyset_use(
    operation: V1HardwareKeysetOperation,
    outcome: V1HardwareKeysetOutcome,
) {
    V1_HARDWARE_KEYSET_USE.add(
        1,
        &[
            KeyValue::new(V1_OPERATION_KEY, operation.as_str()),
            KeyValue::new(V1_OUTCOME_KEY, outcome.as_str()),
        ],
    );
}

fn emit_test_hardware_attestation_fixture_use(
    operation: V2HardwareAttestationOperation,
    network: V2HardwareAttestationNetwork,
) {
    TEST_HARDWARE_ATTESTATION_FIXTURE_USE.add(
        1,
        &[
            KeyValue::new(V2_OPERATION_KEY, operation.as_str()),
            KeyValue::new(V2_NETWORK_KEY, network.as_str()),
        ],
    );
}
