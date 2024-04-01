use std::collections::HashMap;

use axum::extract::Path;
use axum::routing::post;
use axum::Router;
use axum::{extract::State, Json};
use feature_flags::flag::ContextKey;
use serde::{Deserialize, Serialize};
use tracing::instrument;
use utoipa::{OpenApi, ToSchema};

use account::service::Service as AccountService;
use analytics::routes::definitions::PlatformInfo;
use errors::ApiError;
use feature_flags::{
    flag::{evaluate_flags, FeatureFlag},
    service::Service as FeatureFlagsService,
};
use http_server::swagger::{SwaggerEndpoint, Url};
use types::account::identifiers::AccountId;

use crate::error::ExperimentationError;

const DEVICE_ID_ATTRIBUTE_NAME: &str = "device_id";
const CLIENT_TYPE_ATTRIBUTE_NAME: &str = "client_type";
const APPLICATION_VERSION_ATTRIBUTE_NAME: &str = "application_version";
const OS_TYPE_ATTRIBUTE_NAME: &str = "os_type";
const OS_VERSION_ATTRIBUTE_NAME: &str = "os_version";
const DEVICE_MAKE_ATTRIBUTE_NAME: &str = "device_make";
const DEVICE_MODEL_ATTRIBUTE_NAME: &str = "device_model";
const APP_ID_ATTRIBUTE_NAME: &str = "app_id";
const APP_INSTALLATION_ID_ATTRIBUTE_NAME: &str = "app_installation_id";
const DEVICE_REGION_ATTRIBUTE_NAME: &str = "device_region";
const DEVICE_LANGUAGE_ATTRIBUTE_NAME: &str = "device_language";
const HARDWARE_ID_ATTRIBUTE_NAME: &str = "hardware_id";

#[derive(Clone, Deserialize)]
pub struct Config {}

#[derive(Clone, axum_macros::FromRef)]
pub struct RouteState(pub Config, pub AccountService, pub FeatureFlagsService);

impl RouteState {
    pub fn authed_router(&self) -> Router {
        Router::new()
            .route(
                "/api/accounts/:account_id/feature-flags",
                post(get_account_feature_flags),
            )
            .with_state(self.to_owned())
    }

    pub fn unauthed_router(&self) -> Router {
        Router::new()
            .route(
                "/api/feature-flags",
                post(get_app_installation_feature_flags),
            )
            .with_state(self.to_owned())
    }
}

impl From<RouteState> for SwaggerEndpoint {
    fn from(_: RouteState) -> Self {
        (
            Url::new("Experimentation", "/docs/experimentation/openapi.json"),
            ApiDoc::openapi(),
        )
    }
}

#[derive(OpenApi)]
#[openapi(
    paths(
        get_account_feature_flags,
        get_app_installation_feature_flags,
    ),
    components(
        schemas(
            CommonFeatureFlagsAttributes,
            GetAccountFeatureFlagsRequest,
            GetAppInstallationFeatureFlagsRequest,
            GetFeatureFlagsResponse,
            PlatformInfo,
        ),
    ),
    tags(
        (name = "Experimentation", description = "Experimentation endpoints")
    )
)]
struct ApiDoc;

trait ToLaunchDarklyAttributes {
    fn to_attributes(&self) -> HashMap<&'static str, String>;
}

#[derive(Debug, Serialize, Deserialize, ToSchema, Clone)]
pub struct CommonFeatureFlagsAttributes {
    pub app_installation_id: String,
    pub device_region: String,
    pub device_language: String,
    pub platform_info: PlatformInfo,
}

impl ToLaunchDarklyAttributes for PlatformInfo {
    fn to_attributes(&self) -> HashMap<&'static str, String> {
        let mut attributes = HashMap::new();
        attributes.insert(DEVICE_ID_ATTRIBUTE_NAME, self.device_id.clone());
        attributes.insert(CLIENT_TYPE_ATTRIBUTE_NAME, self.client_type.to_string());
        attributes.insert(
            APPLICATION_VERSION_ATTRIBUTE_NAME,
            self.application_version.clone(),
        );
        attributes.insert(OS_TYPE_ATTRIBUTE_NAME, self.os_type.to_string());
        attributes.insert(OS_VERSION_ATTRIBUTE_NAME, self.os_version.clone());
        attributes.insert(DEVICE_MAKE_ATTRIBUTE_NAME, self.device_make.clone());
        attributes.insert(DEVICE_MODEL_ATTRIBUTE_NAME, self.device_model.clone());
        attributes.insert(APP_ID_ATTRIBUTE_NAME, self.app_id.clone());
        attributes
    }
}

impl ToLaunchDarklyAttributes for CommonFeatureFlagsAttributes {
    fn to_attributes(&self) -> HashMap<&'static str, String> {
        let mut attributes = HashMap::new();
        attributes.insert(
            APP_INSTALLATION_ID_ATTRIBUTE_NAME,
            self.app_installation_id.clone(),
        );
        attributes.insert(DEVICE_REGION_ATTRIBUTE_NAME, self.device_region.clone());
        attributes.insert(DEVICE_LANGUAGE_ATTRIBUTE_NAME, self.device_language.clone());
        attributes.extend(self.platform_info.to_attributes());
        attributes
    }
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
pub struct GetAccountFeatureFlagsRequest {
    pub flag_keys: Vec<String>,
    #[serde(default)]
    pub hardware_id: Option<String>,
    #[serde(flatten)]
    pub common: CommonFeatureFlagsAttributes,
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
pub struct GetFeatureFlagsResponse {
    pub flags: Vec<FeatureFlag>,
}

#[instrument(fields(account_id), skip(request, feature_flags_service))]
#[utoipa::path(
    post,
    path = "/api/accounts/{account_id}/feature-flags",
    request_body = GetAccountFeatureFlagsRequest,
    responses(
        (status = 200, description = "Returns the feature flags for the account", body=GetAccountFeatureFlagsResponse),
        (status = 500, description = "Internal server error when retrieving feature flags")
    ),
)]
async fn get_account_feature_flags(
    Path(account_id): Path<AccountId>,
    State(feature_flags_service): State<FeatureFlagsService>,
    Json(request): Json<GetAccountFeatureFlagsRequest>,
) -> Result<Json<GetFeatureFlagsResponse>, ApiError> {
    let mut attrs = request.common.to_attributes();
    if let Some(hardware_id) = request.hardware_id {
        attrs.insert(HARDWARE_ID_ATTRIBUTE_NAME, hardware_id);
    }
    let flags = evaluate_flags(
        &feature_flags_service,
        request.flag_keys,
        ContextKey::Account(account_id.to_string(), attrs),
    )
    .map_err(ExperimentationError::from)?;
    Ok(Json(GetFeatureFlagsResponse { flags }))
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
pub struct GetAppInstallationFeatureFlagsRequest {
    pub flag_keys: Vec<String>,
    #[serde(flatten)]
    pub common: CommonFeatureFlagsAttributes,
}

#[instrument(skip(request, feature_flags_service))]
#[utoipa::path(
    post,
    path = "/api/feature-flags",
    request_body = GetAppInstallationFeatureFlagsRequest,
    responses(
        (status = 200, description = "Returns the feature flags for the app installation", body=GetFeatureFlagsResponse),
        (status = 500, description = "Internal server error when retrieving feature flags")
    ),
)]
async fn get_app_installation_feature_flags(
    State(feature_flags_service): State<FeatureFlagsService>,
    Json(request): Json<GetAppInstallationFeatureFlagsRequest>,
) -> Result<Json<GetFeatureFlagsResponse>, ApiError> {
    let attrs = request.common.to_attributes();
    let flags = evaluate_flags(
        &feature_flags_service,
        request.flag_keys,
        ContextKey::AppInstallation(request.common.app_installation_id, attrs),
    )
    .map_err(ExperimentationError::from)?;
    Ok(Json(GetFeatureFlagsResponse { flags }))
}
