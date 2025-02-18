use std::collections::HashMap;

use tracing::{event, Level};

use feature_flags::flag::{evaluate_flag_value, ContextKey};
use feature_flags::service::Service as FeatureFlagsService;

use crate::routes::AuthRequestKey;

const FLAG_KEY: &str = "f8e-debug-authentication-calls";

pub(crate) fn log_debug_info_if_applicable(
    feature_flags_service: &FeatureFlagsService,
    app_installation_id: &str,
    auth_pubkey: &AuthRequestKey,
) {
    let context_key = ContextKey::AppInstallation(app_installation_id.to_string(), HashMap::new());
    if !evaluate_flag_value(feature_flags_service, FLAG_KEY, &context_key).unwrap_or(false) {
        return;
    }
    event!(
        Level::INFO,
        "Authentication call for app installation {}: with pubkey: {:?}",
        app_installation_id,
        auth_pubkey
    );
}
