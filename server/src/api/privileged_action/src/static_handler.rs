use axum::{http::Uri, response::IntoResponse};
use errors::ApiError;
use rust_embed::RustEmbed;
use types::privileged_action::shared::PrivilegedActionType;

#[derive(RustEmbed)]
#[folder = "resources"]
struct LocalResources;

/// Resolves the embedded HTML template that powers the user-facing verification
/// interface for the given privileged action type.
///
/// Returns `Err(ApiError::GenericBadRequest)` for action types that do not
/// have an associated verification page; callers should not panic on unknown
/// action types.
pub fn get_template_for_action(action_type: &PrivilegedActionType) -> Result<String, ApiError> {
    let template_file = match action_type {
        PrivilegedActionType::LoosenTransactionVerificationPolicy => "tx-policy-verification.html",
        PrivilegedActionType::VerifyHardwareSerial => "hardware-serial-verification.html",
        other => {
            return Err(ApiError::GenericBadRequest(format!(
                "No verification template for privileged action type: {:?}",
                other
            )));
        }
    };

    Ok(secure_site::static_handler::get_embedded_template::<
        LocalResources,
    >(template_file))
}

pub async fn static_handler(uri: Uri) -> impl IntoResponse {
    secure_site::static_handler::serve_with_fallback::<LocalResources>(uri).await
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn returns_template_for_loosen_tx_verification_policy() {
        let template =
            get_template_for_action(&PrivilegedActionType::LoosenTransactionVerificationPolicy)
                .expect("template must resolve for this action type");
        assert!(
            !template.is_empty(),
            "embedded template should not be empty"
        );
    }

    #[test]
    fn returns_template_for_verify_hardware_serial() {
        let template = get_template_for_action(&PrivilegedActionType::VerifyHardwareSerial)
            .expect("template must resolve for this action type");
        assert!(
            !template.is_empty(),
            "embedded template should not be empty"
        );
    }

    /// The whole point of the refactor: action types without a verification
    /// page must return a typed error rather than panicking the handler.
    #[test]
    fn returns_bad_request_for_action_types_without_templates() {
        for action_type in [
            PrivilegedActionType::ConfigurePrivilegedActionDelays,
            PrivilegedActionType::ActivateTouchpoint,
            PrivilegedActionType::ResetFingerprint,
        ] {
            let err = get_template_for_action(&action_type)
                .expect_err("action types without a verification page must return an error");
            assert!(matches!(err, ApiError::GenericBadRequest(_)));
        }
    }
}
