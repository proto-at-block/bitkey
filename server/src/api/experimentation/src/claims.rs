use std::str::FromStr;

use axum::async_trait;
use axum::extract::{FromRef, FromRequestParts};
use axum::http::request::Parts;
use axum::http::StatusCode;

use account::service::Service as AccountService;
use authn_authz::key_claims::{get_jwt_from_request_parts, get_user_name_from_jwt};
use feature_flags::flag::ContextKey;
use instrumentation::middleware::APP_INSTALLATION_ID_HEADER_NAME;
use types::account::identifiers::AccountId;
use types::authn_authz::cognito::CognitoUser;

use crate::attributes::ToLaunchDarklyAttributes;
use crate::error::ExperimentationError;

const APP_VERSION_HEADER_NAME: &str = "Bitkey-App-Version";
const OS_TYPE_HEADER_NAME: &str = "Bitkey-OS-Type";
const OS_VERSION_HEADER_NAME: &str = "Bitkey-OS-Version";
const DEVICE_REGION_HEADER_NAME: &str = "Bitkey-Device-Region";

#[derive(Debug, Clone)]
pub struct ExperimentationClaims {
    pub account_id: Option<String>,
    pub app_installation_id: Option<String>,
    pub app_version: Option<String>,
    pub os_type: Option<String>,
    pub os_version: Option<String>,
    pub device_region: Option<String>,
}

impl ExperimentationClaims {
    fn from_request_parts(parts: &mut Parts) -> ExperimentationClaims {
        ExperimentationClaims {
            account_id: Self::extract_account_id(parts),
            app_installation_id: Self::extract_header_value(parts, APP_INSTALLATION_ID_HEADER_NAME),
            app_version: Self::extract_header_value(parts, APP_VERSION_HEADER_NAME),
            os_type: Self::extract_header_value(parts, OS_TYPE_HEADER_NAME),
            os_version: Self::extract_header_value(parts, OS_VERSION_HEADER_NAME),
            device_region: Self::extract_header_value(parts, DEVICE_REGION_HEADER_NAME),
        }
    }

    fn extract_account_id(parts: &mut Parts) -> Option<String> {
        get_jwt_from_request_parts(parts)
            .and_then(|jwt| get_user_name_from_jwt(&jwt))
            .and_then(|u| CognitoUser::from_str(u.as_ref()).ok())
            .map(|cognito_user| cognito_user.get_account_id().to_string())
    }

    fn extract_header_value(parts: &mut Parts, header_name: &str) -> Option<String> {
        parts
            .headers
            .get(header_name)
            .and_then(|value| value.to_str().ok().map(String::from))
    }

    /// The context to use for authenticated users.
    pub fn account_context_key(&self) -> Result<ContextKey, ExperimentationError> {
        let attributes = self.to_attributes();
        if let Some(account_id) = &self.account_id {
            Ok(ContextKey::Account(account_id.clone(), attributes))
        } else {
            Err(ExperimentationError::ContextGeneration)
        }
    }

    /// The context to use for authenticated users (used by delay and notify only for now on the complete endpoint).
    /// This is a temporary function to be removed once inheritance is fully rolled out.
    pub fn overridden_account_context_key(&self, account_id: AccountId) -> ContextKey {
        let attributes = self.to_attributes();
        ContextKey::Account(account_id.to_string().clone(), attributes)
    }

    /// The context to use for unauthenticated users.
    pub fn app_installation_context_key(&self) -> Result<ContextKey, ExperimentationError> {
        let attributes = self.to_attributes();
        if let Some(app_installation_id) = &self.app_installation_id {
            Ok(ContextKey::AppInstallation(
                app_installation_id.clone(),
                attributes,
            ))
        } else {
            Err(ExperimentationError::ContextGeneration)
        }
    }
}

#[async_trait]
impl<S> FromRequestParts<S> for ExperimentationClaims
where
    S: Send + Sync,
    AccountService: FromRef<S>,
{
    type Rejection = StatusCode;

    async fn from_request_parts(parts: &mut Parts, _: &S) -> Result<Self, Self::Rejection> {
        Ok(Self::from_request_parts(parts))
    }
}

#[cfg(test)]
mod tests {
    use authn_authz::test_utils::get_test_access_token;
    use axum::http::header::HeaderValue;
    use axum::http::{HeaderMap, Request};

    use super::*;

    #[tokio::test]
    async fn test_from_request_parts() {
        // arrange
        let account_id = "urn:wallet-account:000000000000000000000000000";
        let app_installation_id = "test_app_installation_id";
        let app_version = "test_app_version";
        let os_type = "test_os_type";
        let os_version = "test_os_version";
        let device_region = "test_device_region";
        let access_token = get_test_access_token();

        let mut headers = HeaderMap::new();
        headers.insert(
            APP_INSTALLATION_ID_HEADER_NAME,
            HeaderValue::from_static(app_installation_id),
        );
        headers.insert(
            APP_VERSION_HEADER_NAME,
            HeaderValue::from_static(app_version),
        );
        headers.insert(OS_TYPE_HEADER_NAME, HeaderValue::from_static(os_type));
        headers.insert(OS_VERSION_HEADER_NAME, HeaderValue::from_static(os_version));
        headers.insert(
            DEVICE_REGION_HEADER_NAME,
            HeaderValue::from_static(device_region),
        );

        headers.insert(
            "Authorization",
            HeaderValue::from_str(format!("Bearer {}", access_token).as_str()).unwrap(),
        );

        let mut request = Request::new(());
        request.headers_mut().extend(headers);

        let mut parts = request.into_parts().0;

        // act
        let claims: ExperimentationClaims = ExperimentationClaims::from_request_parts(&mut parts);

        //assert
        assert_eq!(claims.account_id, Some(account_id.to_string()));
        assert_eq!(
            claims.app_installation_id,
            Some(app_installation_id.to_string())
        );
        assert_eq!(claims.app_version, Some(app_version.to_string()));
        assert_eq!(claims.os_type, Some(os_type.to_string()));
        assert_eq!(claims.os_version, Some(os_version.to_string()));
        assert_eq!(claims.device_region, Some(device_region.to_string()));
    }
}
