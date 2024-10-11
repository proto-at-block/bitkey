use authn_authz::key_claims::KeyClaims;
use axum::extract::Path;
use axum::routing::{get, post, put};
use axum::Router;
use axum::{extract::State, Json};
use http_server::router::RouterBuilder;
use serde::{Deserialize, Serialize};
use tracing::instrument;
use types::privileged_action::definition::ResolvedPrivilegedActionDefinition;
use types::privileged_action::router::generic::{
    AuthorizationStrategyInput, AuthorizationStrategyOutput, ContinuePrivilegedActionRequest,
    DelayAndNotifyInput, DelayAndNotifyOutput, PendingPrivilegedActionResponse,
    PrivilegedActionInstanceInput, PrivilegedActionInstanceOutput, PrivilegedActionRequest,
    PrivilegedActionResponse,
};
use types::privileged_action::router::PrivilegedActionInstance;
use types::privileged_action::shared::{PrivilegedActionDelayDuration, PrivilegedActionType};
use utoipa::{OpenApi, ToSchema};

use errors::ApiError;
use http_server::swagger::{SwaggerEndpoint, Url};
use types::account::identifiers::AccountId;
use userpool::userpool::UserPoolService;

use crate::metrics::{FACTORY, FACTORY_NAME};
use crate::service::authorize_privileged_action::{
    AuthorizePrivilegedActionInput, AuthorizePrivilegedActionOutput,
    PrivilegedActionRequestValidatorBuilder,
};
use crate::service::cancel_pending_delay_and_notify_instance::CancelPendingDelayAndNotifyInstanceByTokenInput;
use crate::service::configure_privileged_action_delay_durations::ConfigurePrivilegedActionDelayDurationsInput;
use crate::service::get_pending_delay_and_notify_instances::GetPendingDelayAndNotifyInstancesInput;
use crate::service::get_privileged_action_definitions::GetPrivilegedActionDefinitionsInput;
use crate::service::Service as PrivilegedActionService;

#[derive(Clone, axum_macros::FromRef)]
pub struct RouteState(pub UserPoolService, pub PrivilegedActionService);

impl RouterBuilder for RouteState {
    fn account_authed_router(&self) -> Router {
        Router::new()
            .route(
                "/api/accounts/:account_id/privileged-actions/delays",
                put(configure_privileged_action_delay_durations),
            )
            .route_layer(FACTORY.route_layer(FACTORY_NAME.to_owned()))
            .with_state(self.to_owned())
    }

    fn account_or_recovery_authed_router(&self) -> Router {
        Router::new()
            .route(
                "/api/accounts/:account_id/privileged-actions/definitions",
                get(get_privileged_action_definitions),
            )
            .route(
                "/api/accounts/:account_id/privileged-actions/instances",
                get(get_pending_delay_and_notify_instances),
            )
            .route_layer(FACTORY.route_layer(FACTORY_NAME.to_owned()))
            .with_state(self.to_owned())
    }

    fn unauthed_router(&self) -> Router {
        Router::new()
            .route(
                "/api/privileged-actions/cancel",
                post(cancel_pending_delay_and_notify_instance_by_token),
            )
            .route_layer(FACTORY.route_layer(FACTORY_NAME.to_owned()))
            .with_state(self.to_owned())
    }
}

impl From<RouteState> for SwaggerEndpoint {
    fn from(_: RouteState) -> Self {
        (
            Url::new("Privileged Action", "/docs/privileged_action/openapi.json"),
            ApiDoc::openapi(),
        )
    }
}

#[derive(OpenApi)]
#[openapi(
    paths(
        get_privileged_action_definitions,
        configure_privileged_action_delay_durations,
        get_pending_delay_and_notify_instances,
        cancel_pending_delay_and_notify_instance_by_token,
    ),
    components(
        schemas(
            GetPrivilegedActionDefinitionsResponse,
            ResolvedPrivilegedActionDefinition,
            PrivilegedActionDelayDuration,
            DelayAndNotifyInput,
            AuthorizationStrategyInput,
            PrivilegedActionInstanceInput,
            ContinuePrivilegedActionRequest,
            PrivilegedActionType,
            DelayAndNotifyOutput,
            AuthorizationStrategyOutput,
            PrivilegedActionInstanceOutput,
            PendingPrivilegedActionResponse,
            ConfigurePrivilegedActionDelayDurationsRequest,
            PrivilegedActionRequest<ConfigurePrivilegedActionDelayDurationsRequest>,
            ConfigurePrivilegedActionDelayDurationsResponse,
            PrivilegedActionResponse<ConfigurePrivilegedActionDelayDurationsResponse>,
            GetPendingDelayAndNotifyInstancesResponse,
            PrivilegedActionInstance,
            CancelPendingDelayAndNotifyInstanceByTokenRequest,
        ),
    ),
    tags(
        (name = "Privileged Action", description = "Privileged Action Management")
    )
)]
struct ApiDoc;

#[derive(Serialize, Deserialize, Debug, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct GetPrivilegedActionDefinitionsResponse {
    pub definitions: Vec<ResolvedPrivilegedActionDefinition>,
}

#[instrument(err, skip(privileged_action_service))]
#[utoipa::path(
    get,
    path = "/api/accounts/{account_id}/privileged-actions/definitions",
    responses(
        (status = 200, description = "Privileged action definitions", body=GetPrivilegedActionDefinitionsResponse),
        (status = 404, description = "Account not found")
    ),
)]
pub async fn get_privileged_action_definitions(
    State(privileged_action_service): State<PrivilegedActionService>,
    Path(account_id): Path<AccountId>,
) -> Result<Json<GetPrivilegedActionDefinitionsResponse>, ApiError> {
    Ok(Json(GetPrivilegedActionDefinitionsResponse {
        definitions: privileged_action_service
            .get_privileged_action_definitions(GetPrivilegedActionDefinitionsInput {
                account_id: &account_id,
            })
            .await?,
    }))
}

#[derive(Serialize, Deserialize, Clone, Debug, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct ConfigurePrivilegedActionDelayDurationsRequest {
    pub delays: Vec<PrivilegedActionDelayDuration>,
}

#[derive(Serialize, Deserialize, Debug, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct ConfigurePrivilegedActionDelayDurationsResponse {}

#[instrument(err, skip(privileged_action_service))]
#[utoipa::path(
    put,
    path = "/api/accounts/{account_id}/privileged-actions/delays",
    request_body = PrivilegedActionRequest<ConfigurePrivilegedActionDelayDurationsRequest>,
    responses(
        (status = 200, description = "Privileged action delays configured", body=PrivilegedActionResponse<ConfigurePrivilegedActionDelayDurationsResponse>),
        (status = 404, description = "Account not found")
    ),
)]
pub async fn configure_privileged_action_delay_durations(
    State(privileged_action_service): State<PrivilegedActionService>,
    Path(account_id): Path<AccountId>,
    key_proof: KeyClaims,
    Json(privileged_action_request): Json<
        PrivilegedActionRequest<ConfigurePrivilegedActionDelayDurationsRequest>,
    >,
) -> Result<Json<PrivilegedActionResponse<ConfigurePrivilegedActionDelayDurationsResponse>>, ApiError>
{
    let cloned_privileged_action_service = privileged_action_service.clone();
    let cloned_account_id = account_id.clone();

    let authorize_result = privileged_action_service
        .authorize_privileged_action(AuthorizePrivilegedActionInput {
            account_id: &account_id,
            privileged_action_definition: &PrivilegedActionType::ConfigurePrivilegedActionDelays
                .into(),
            key_proof: &key_proof,
            privileged_action_request: &privileged_action_request,
            request_validator: PrivilegedActionRequestValidatorBuilder::default()
                .on_initiate_delay_and_notify(Box::new(
                    |r: ConfigurePrivilegedActionDelayDurationsRequest| {
                        Box::pin(async move {
                            cloned_privileged_action_service
                                .configure_privileged_action_delay_durations(
                                    ConfigurePrivilegedActionDelayDurationsInput {
                                        account_id: &cloned_account_id,
                                        configured_delay_durations: r.delays,
                                        dry_run: true,
                                    },
                                )
                                .await?;

                            Ok::<(), ApiError>(())
                        })
                    },
                ))
                .build()?,
        })
        .await?;

    let authorized_request = match authorize_result {
        AuthorizePrivilegedActionOutput::Pending(response) => {
            return Ok(Json(response));
        }
        AuthorizePrivilegedActionOutput::Authorized(initial_request) => initial_request,
    };

    privileged_action_service
        .configure_privileged_action_delay_durations(ConfigurePrivilegedActionDelayDurationsInput {
            account_id: &account_id,
            configured_delay_durations: authorized_request.delays,
            dry_run: false,
        })
        .await?;

    Ok(Json(PrivilegedActionResponse::Completed(
        ConfigurePrivilegedActionDelayDurationsResponse {},
    )))
}

#[derive(Serialize, Deserialize, Debug, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct GetPendingDelayAndNotifyInstancesResponse {
    pub instances: Vec<PrivilegedActionInstance>,
}

#[instrument(err, skip(privileged_action_service))]
#[utoipa::path(
    get,
    path = "/api/accounts/{account_id}/privileged-actions/instances",
    responses(
        (status = 200, description = "Privileged action instances", body=GetPendingDelayAndNotifyInstancesResponse),
        (status = 404, description = "Account not found")
    ),
)]
pub async fn get_pending_delay_and_notify_instances(
    State(privileged_action_service): State<PrivilegedActionService>,
    Path(account_id): Path<AccountId>,
) -> Result<Json<GetPendingDelayAndNotifyInstancesResponse>, ApiError> {
    Ok(Json(GetPendingDelayAndNotifyInstancesResponse {
        instances: privileged_action_service
            .get_pending_delay_and_notify_instances(GetPendingDelayAndNotifyInstancesInput {
                account_id: &account_id,
            })
            .await?
            .into_iter()
            .map(Into::into)
            .collect(),
    }))
}

#[derive(Serialize, Deserialize, Debug, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct CancelPendingDelayAndNotifyInstanceByTokenRequest {
    pub cancellation_token: String,
}

#[derive(Serialize, Deserialize, Debug, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct CancelPendingDelayAndNotifyInstanceByTokenResponse {}

#[instrument(err, skip(privileged_action_service))]
#[utoipa::path(
    post,
    path = "/api/privileged-actions/cancel",
    request_body = CancelPendingDelayAndNotifyInstanceByTokenRequest,
    responses(
        (status = 200, description = "Privileged action instance cancelled", body=CancelPendingDelayAndNotifyInstanceByTokenResponse),
        (status = 404, description = "Account not found")
    ),
)]
pub async fn cancel_pending_delay_and_notify_instance_by_token(
    State(privileged_action_service): State<PrivilegedActionService>,
    Json(request): Json<CancelPendingDelayAndNotifyInstanceByTokenRequest>,
) -> Result<Json<CancelPendingDelayAndNotifyInstanceByTokenResponse>, ApiError> {
    privileged_action_service
        .cancel_pending_delay_and_notify_instance_by_token(
            CancelPendingDelayAndNotifyInstanceByTokenInput {
                cancellation_token: request.cancellation_token,
            },
        )
        .await?;

    Ok(Json(CancelPendingDelayAndNotifyInstanceByTokenResponse {}))
}
