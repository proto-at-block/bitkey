use authn_authz::key_claims::KeyClaims;
use axum::{
    extract::{Path, Query, State},
    http::{header, StatusCode},
    response::{Html, IntoResponse},
    routing::{get, post, put},
    Json, Router,
};
use http_server::router::RouterBuilder;
use serde::{Deserialize, Serialize};
use serde_json::Value;
use tracing::instrument;
use utoipa::{OpenApi, ToSchema};

use crate::{
    metrics::{FACTORY, FACTORY_NAME},
    service::{
        authorize_privileged_action::{
            AuthenticationContext, AuthorizePrivilegedActionInput, AuthorizePrivilegedActionOutput,
            PrivilegedActionRequestValidatorBuilder,
        },
        cancel_pending_instance::CancelPendingDelayAndNotifyInstanceByTokenInput,
        configure_delay_duration_for_test::ConfigureDelayDurationForTestInput,
        configure_privileged_action_delay_durations::ConfigurePrivilegedActionDelayDurationsInput,
        get_pending_instances::GetPendingInstancesInput,
        get_privileged_action_definitions::GetPrivilegedActionDefinitionsInput,
        Service as PrivilegedActionService,
    },
    static_handler::{get_template, static_handler},
};

use account::service::Service as AccountService;
use errors::ApiError;
use http_server::swagger::{SwaggerEndpoint, Url};
use secure_site::static_handler::{html_error, inject_json_into_template};
use types::{
    account::identifiers::AccountId,
    privileged_action::{
        definition::ResolvedPrivilegedActionDefinition,
        repository::{AuthorizationStrategyRecord, RecordStatus},
        router::{
            generic::{
                AuthorizationStrategyInput, AuthorizationStrategyOutput,
                ContinuePrivilegedActionRequest, DelayAndNotifyInput, DelayAndNotifyOutput,
                OutOfBandInput, PendingPrivilegedActionResponse, PrivilegedActionInstanceInput,
                PrivilegedActionInstanceOutput, PrivilegedActionRequest, PrivilegedActionResponse,
            },
            PrivilegedActionInstance,
        },
        shared::{PrivilegedActionDelayDuration, PrivilegedActionInstanceId, PrivilegedActionType},
    },
    transaction_verification::router::PutTransactionVerificationPolicyRequest,
};
use userpool::userpool::UserPoolService;

#[derive(Clone, axum_macros::FromRef)]
pub struct RouteState(
    pub UserPoolService,
    pub PrivilegedActionService,
    pub AccountService,
);

impl RouterBuilder for RouteState {
    fn account_authed_router(&self) -> Router {
        Router::new()
            .route(
                "/api/accounts/:account_id/privileged-actions/delays",
                put(configure_privileged_action_delay_durations),
            )
            .route(
                "/api/accounts/:account_id/privileged-actions/:privileged_action_id/test",
                put(update_delay_duration_for_test),
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
                get(get_pending_instances),
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

    fn secure_site_router(&self) -> Router {
        Router::new()
            .route(
                "/privileged-action",
                get(get_privileged_action_verification_interface),
            )
            .route(
                "/api/privileged-action/respond",
                put(respond_to_out_of_band_privileged_action),
            )
            .route("/privileged-action/static/*file", get(static_handler))
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
        cancel_pending_delay_and_notify_instance_by_token,
        configure_privileged_action_delay_durations,
        get_pending_instances,
        get_privileged_action_definitions,
        update_delay_duration_for_test,
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
            GetPendingInstancesResponse,
            PrivilegedActionInstance,
            CancelPendingDelayAndNotifyInstanceByTokenRequest,
            UpdateDelayDurationForTestRequest,
            UpdateDelayDurationForTestResponse,
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
            authentication: AuthenticationContext::KeyClaims(&key_proof),
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

#[derive(Serialize, Deserialize, Clone, Debug, ToSchema)]
pub struct UpdateDelayDurationForTestRequest {
    pub delay_duration: i64,
}

#[derive(Serialize, Deserialize, Debug, ToSchema)]
pub struct UpdateDelayDurationForTestResponse {}

#[instrument(err, skip(privileged_action_service))]
#[utoipa::path(
    put,
    path = "/api/accounts/{account_id}/privileged-actions/:privileged_action_id/test",
    request_body = UpdateDelayDurationForTestRequest,
    responses(
        (status = 200, description = "Privileged action delay duration updated", body=UpdateDelayDurationForTestResponse),
        (status = 404, description = "Account not found")
    ),
)]
pub async fn update_delay_duration_for_test(
    Path((account_id, privileged_action_id)): Path<(AccountId, PrivilegedActionInstanceId)>,
    State(privileged_action_service): State<PrivilegedActionService>,
    Json(request): Json<UpdateDelayDurationForTestRequest>,
) -> Result<Json<UpdateDelayDurationForTestResponse>, ApiError> {
    privileged_action_service
        .configure_delay_duration_for_test(ConfigureDelayDurationForTestInput {
            account_id: &account_id,
            privilege_action_id: &privileged_action_id,
            delay_duration: request.delay_duration,
        })
        .await?;
    Ok(Json(UpdateDelayDurationForTestResponse {}))
}

#[derive(Serialize, Deserialize, Debug, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct GetPendingInstancesResponse {
    pub instances: Vec<PrivilegedActionInstance>,
}

#[derive(Serialize, Deserialize, Debug, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct GetPendingInstancesParams {
    #[serde(default)]
    pub privileged_action_type: Option<PrivilegedActionType>,
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
pub async fn get_pending_instances(
    State(privileged_action_service): State<PrivilegedActionService>,
    Path(account_id): Path<AccountId>,
    Query(params): Query<GetPendingInstancesParams>,
) -> Result<Json<GetPendingInstancesResponse>, ApiError> {
    Ok(Json(GetPendingInstancesResponse {
        instances: privileged_action_service
            .get_pending_instances(GetPendingInstancesInput {
                account_id: &account_id,
                authorization_strategy: None,
                privileged_action_type: params.privileged_action_type,
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

#[derive(Serialize, Deserialize, Debug, ToSchema)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE", tag = "action")]
pub enum ProcessPrivilegedActionVerificationRequest {
    Cancel {
        web_auth_token: String,
    },
    Confirm {
        privileged_action_type: PrivilegedActionType,
        web_auth_token: String,
    },
}

#[derive(Debug, Serialize, Deserialize)]
pub struct ProcessPrivilegedActionVerificationResponse {}

#[derive(Deserialize)]
pub struct PrivilegedActionOutOfBandVerificationInterfaceParams {
    web_auth_token: String,
}

pub async fn get_privileged_action_verification_interface(
    State(privileged_action_service): State<PrivilegedActionService>,
    Query(params): Query<PrivilegedActionOutOfBandVerificationInterfaceParams>,
) -> Result<impl IntoResponse, impl IntoResponse> {
    let privileged_action = privileged_action_service
        .get_by_web_auth_token::<Value>(&params.web_auth_token)
        .await
        .map_err(|e| html_error(StatusCode::INTERNAL_SERVER_ERROR, e))?;

    if privileged_action.get_record_status() != RecordStatus::Pending {
        return Err(html_error(
            StatusCode::BAD_REQUEST,
            "Privileged action is not pending",
        ));
    }

    let html_template = get_template();
    let verification_params = serde_json::json!({
        "privilegedActionId": privileged_action.id,
        "privilegedActionType": privileged_action.privileged_action_type,
    });

    let html = inject_json_into_template(
        &html_template,
        "privileged-action-params",
        verification_params,
    )
    .map_err(|e| html_error(StatusCode::INTERNAL_SERVER_ERROR, e))?;

    Ok((
        StatusCode::OK,
        [(header::CONTENT_TYPE, "text/html; charset=utf-8")],
        Html(html),
    ))
}

#[instrument(err, skip(privileged_action_service, account_service))]
#[utoipa::path(
    put,
    path = "/api/privileged-action/respond",
    request_body = ProcessPrivilegedActionVerificationRequest,
    responses(
        (status = 200, description = "Privileged action was either cancelled or succeeded", body=ProcessPrivilegedActionVerificationResponse),
        (status = 404, description = "Account not found")
    ),
)]
async fn respond_to_out_of_band_privileged_action(
    State(privileged_action_service): State<PrivilegedActionService>,
    State(account_service): State<AccountService>,
    Json(request): Json<ProcessPrivilegedActionVerificationRequest>,
) -> Result<Json<ProcessPrivilegedActionVerificationResponse>, ApiError> {
    match request {
        ProcessPrivilegedActionVerificationRequest::Confirm {
            privileged_action_type,
            web_auth_token,
        } => {
            confirm_privileged_action(
                &privileged_action_service,
                &account_service,
                privileged_action_type,
                &web_auth_token,
            )
            .await?;
        }
        ProcessPrivilegedActionVerificationRequest::Cancel { web_auth_token } => {
            privileged_action_service
                .cancel_pending_instance_by_web_auth_token(&web_auth_token)
                .await?;
        }
    }

    Ok(Json(ProcessPrivilegedActionVerificationResponse {}))
}

async fn confirm_privileged_action(
    privileged_action_service: &PrivilegedActionService,
    account_service: &AccountService,
    privileged_action_type: PrivilegedActionType,
    web_auth_token: &str,
) -> Result<(), ApiError> {
    match privileged_action_type {
        PrivilegedActionType::LoosenTransactionVerificationPolicy => {
            confirm_transaction_verification_policy(
                privileged_action_service,
                account_service,
                web_auth_token,
            )
            .await
        }
        _ => Err(ApiError::GenericBadRequest(format!(
            "Unsupported privileged action type: {:?}",
            privileged_action_type
        ))),
    }
}

async fn confirm_transaction_verification_policy(
    privileged_action_service: &PrivilegedActionService,
    account_service: &AccountService,
    web_auth_token: &str,
) -> Result<(), ApiError> {
    // Fetch and validate the privileged action
    let privileged_action = privileged_action_service
        .get_by_web_auth_token::<PutTransactionVerificationPolicyRequest>(web_auth_token)
        .await?;

    validate_out_of_band_authorization(&privileged_action.authorization_strategy)?;

    // Continue the privileged action flow
    let policy_request = continue_out_of_band_privileged_action(
        privileged_action_service,
        &privileged_action,
        web_auth_token,
    )
    .await?;

    // Apply the policy change
    account_service
        .put_transaction_verification_policy(&privileged_action.account_id, policy_request.policy)
        .await?;

    Ok(())
}

fn validate_out_of_band_authorization(
    authorization_strategy: &AuthorizationStrategyRecord,
) -> Result<(), ApiError> {
    let out_of_band = match authorization_strategy {
        AuthorizationStrategyRecord::OutOfBand(out_of_band) => out_of_band,
        _ => {
            return Err(ApiError::GenericBadRequest(
                "Invalid authorization strategy: expected OutOfBand".to_string(),
            ))
        }
    };
    match out_of_band.status {
        RecordStatus::Pending => Ok(()),
        _ => Err(ApiError::GenericBadRequest(
            "Privileged action is not pending".to_string(),
        )),
    }
}

async fn continue_out_of_band_privileged_action<T>(
    privileged_action_service: &PrivilegedActionService,
    privileged_action: &types::privileged_action::repository::PrivilegedActionInstanceRecord<T>,
    web_auth_token: &str,
) -> Result<T, ApiError>
where
    T: serde::Serialize + serde::de::DeserializeOwned + Clone,
{
    let continue_request = PrivilegedActionRequest::Continue(ContinuePrivilegedActionRequest {
        privileged_action_instance: PrivilegedActionInstanceInput {
            id: privileged_action.id.clone(),
            authorization_strategy: AuthorizationStrategyInput::OutOfBand(OutOfBandInput {
                web_auth_token: web_auth_token.to_string(),
            }),
        },
    });

    let result: AuthorizePrivilegedActionOutput<T, ProcessPrivilegedActionVerificationResponse> =
        privileged_action_service
            .authorize_privileged_action(AuthorizePrivilegedActionInput::<T, ApiError> {
                account_id: &privileged_action.account_id,
                privileged_action_definition: &privileged_action
                    .privileged_action_type
                    .clone()
                    .into(),
                authentication: AuthenticationContext::Standard,
                privileged_action_request: &continue_request,
                request_validator: PrivilegedActionRequestValidatorBuilder::default().build()?,
            })
            .await?;

    match result {
        AuthorizePrivilegedActionOutput::Authorized(request) => Ok(request),
        AuthorizePrivilegedActionOutput::Pending(_) => Err(ApiError::GenericBadRequest(
            "Expected authorized response, but action is still pending".to_string(),
        )),
    }
}
