use std::collections::HashMap;

use tracing::{event, Level};

use feature_flags::flag::{evaluate_flag_value, ContextKey};
use feature_flags::service::Service as FeatureFlagService;
use types::account::identifiers::AccountId;

use crate::routes::AuthRequestKey;

const FLAG_KEY: &str = "f8e-debug-authentication-calls";

pub(crate) fn log_debug_info_if_applicable(
    feature_flag_service: &FeatureFlagService,
    account_id: &AccountId,
    auth_pubkey: &AuthRequestKey,
) {
    let context_key = ContextKey::Account(account_id.to_string(), HashMap::new());
    if !evaluate_flag_value(feature_flag_service, FLAG_KEY, &context_key).unwrap_or(false) {
        return;
    }
    event!(
        Level::INFO,
        "Authentication call for account {}: with pubkey: {:?}",
        account_id,
        auth_pubkey
    );
}
