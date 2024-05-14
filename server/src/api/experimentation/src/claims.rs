use account::service::Service as AccountService;
use axum::async_trait;
use axum::extract::{FromRef, FromRequestParts};
use axum::http::request::Parts;
use axum::http::StatusCode;
use feature_flags::flag::ContextKey;
use instrumentation::middleware::APP_INSTALLATION_ID_HEADER_NAME;

use crate::attributes::ToLaunchDarklyAttributes;
use crate::error::ExperimentationError;

const ACCOUNT_ID_HEADER_NAME: &str = "Account-Id";
const APP_VERSION_HEADER_NAME: &str = "App-Version";
const OS_TYPE_HEADER_NAME: &str = "OS-Type";
const OS_VERSION_HEADER_NAME: &str = "OS-Version";
const DEVICE_REGION_HEADER_NAME: &str = "Device-Region";

#[derive(Debug, Clone)]
pub struct ExperimentationClaims {
    pub account_id: Option<String>,
    pub app_installation_id: Option<String>,
    pub app_version: Option<String>,
    pub os_type: Option<String>,
    pub os_version: Option<String>,
    pub device_region: Option<String>,
}

impl TryFrom<ExperimentationClaims> for ContextKey {
    type Error = ExperimentationError;

    fn try_from(v: ExperimentationClaims) -> Result<Self, Self::Error> {
        let attributes = v.to_attributes();
        if let Some(account_id) = v.account_id {
            Ok(ContextKey::Account(account_id, attributes))
        } else if let Some(app_installation_id) = v.app_installation_id {
            Ok(ContextKey::AppInstallation(app_installation_id, attributes))
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
        Ok(ExperimentationClaims {
            account_id: parts
                .headers
                .get(ACCOUNT_ID_HEADER_NAME)
                .and_then(|value| value.to_str().ok().map(String::from)),
            app_installation_id: parts
                .headers
                .get(APP_INSTALLATION_ID_HEADER_NAME)
                .and_then(|value| value.to_str().ok().map(String::from)),
            app_version: parts
                .headers
                .get(APP_VERSION_HEADER_NAME)
                .and_then(|value| value.to_str().ok().map(String::from)),
            os_type: parts
                .headers
                .get(OS_TYPE_HEADER_NAME)
                .and_then(|value| value.to_str().ok().map(String::from)),
            os_version: parts
                .headers
                .get(OS_VERSION_HEADER_NAME)
                .and_then(|value| value.to_str().ok().map(String::from)),
            device_region: parts
                .headers
                .get(DEVICE_REGION_HEADER_NAME)
                .and_then(|value| value.to_str().ok().map(String::from)),
        })
    }
}
