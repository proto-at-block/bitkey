use authn_authz::Authorization;
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
use types::currencies::CurrencyCode;
use utoipa::{OpenApi, ToSchema};

use crate::{
    metrics::{FACTORY, FACTORY_NAME},
    service::{
        authorize_privileged_action::{
            AuthorizePrivilegedActionInput, AuthorizePrivilegedActionOutput,
            PrivilegedActionRequestValidatorBuilder,
        },
        cancel_pending_instance::CancelPendingDelayAndNotifyInstanceByTokenInput,
        configure_delay_duration_for_test::ConfigureDelayDurationForTestInput,
        configure_privileged_action_delay_durations::ConfigurePrivilegedActionDelayDurationsInput,
        get_pending_instance::GetPendingInstanceInput,
        get_pending_instances::GetPendingInstancesInput,
        get_privileged_action_definitions::GetPrivilegedActionDefinitionsInput,
        increment_out_of_band_attempts::IncrementOutOfBandAttemptsResult,
        Service as PrivilegedActionService,
    },
    static_handler::{get_template_for_action, static_handler},
};

use account::service::{FetchAccountInput, Service as AccountService};
use errors::{ApiError, ErrorCode};
use http_server::swagger::{SwaggerEndpoint, Url};
use secure_site::static_handler::{html_error, inject_json_into_template};
use subtle::ConstantTimeEq;
use types::account::{
    entities::{Account, TransactionVerificationPolicy},
    spending::AttestedHardwareSerial,
};
use types::{
    account::identifiers::AccountId,
    privileged_action::{
        definition::ResolvedPrivilegedActionDefinition,
        repository::{AuthorizationStrategyRecord, RecordStatus, DEFAULT_OOB_MAX_ATTEMPTS},
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
        verify_hardware_serial::VerifyHardwareSerialSubmission,
    },
    transaction_verification::router::PutTransactionVerificationPolicyRequest,
};
use userpool::userpool::UserPoolService;

#[derive(Clone, axum_macros::FromRef)]
pub struct RouteState(
    pub UserPoolService,
    pub PrivilegedActionService,
    pub AccountService,
    pub repository::anti_replay::AntiReplayRepository,
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
            .route(
                "/api/accounts/:account_id/privileged-actions/:privileged_action_id/cancel",
                post(cancel_pending_out_of_band_instance),
            )
            .route(
                "/api/accounts/:account_id/privileged-actions/:privileged_action_id/resend",
                post(resend_out_of_band_verification),
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
                "/api/accounts/:account_id/privileged-actions/:privileged_action_id",
                get(get_pending_instance),
            )
            .route(
                "/api/accounts/:account_id/privileged-actions/instances",
                get(get_pending_instances),
            )
            .route(
                "/api/accounts/:account_id/action-proof/format-value",
                post(format_value),
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
        cancel_pending_out_of_band_instance,
        resend_out_of_band_verification,
        configure_privileged_action_delay_durations,
        get_pending_instance,
        get_pending_instances,
        get_privileged_action_definitions,
        update_delay_duration_for_test,
        format_value,
    ),
    components(
        schemas(
            FormatValueRequest,
            FormatValueResponse,
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
            GetPendingInstanceResponse,
            GetPendingInstancesResponse,
            PrivilegedActionInstance,
            CancelPendingInstanceResponse,
            ResendPendingInstanceResponse,
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
    _auth: Authorization,
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

    match authorize_result {
        AuthorizePrivilegedActionOutput::Pending(response) => Ok(Json(response)),
        AuthorizePrivilegedActionOutput::Authorized(authorized_request) => {
            privileged_action_service
                .configure_privileged_action_delay_durations(
                    ConfigurePrivilegedActionDelayDurationsInput {
                        account_id: &account_id,
                        configured_delay_durations: authorized_request.delays,
                        dry_run: false,
                    },
                )
                .await?;

            let result = PrivilegedActionResponse::Completed(
                ConfigurePrivilegedActionDelayDurationsResponse {},
            );
            Ok(Json(result))
        }
    }
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
pub struct GetPendingInstanceResponse {
    pub privileged_action_instance: PrivilegedActionInstance,
}

#[instrument(err, skip(privileged_action_service))]
#[utoipa::path(
    get,
    path = "/api/accounts/{account_id}/privileged-actions/{privileged_action_id}",
    responses(
        (status = 200, description = "Pending privileged action instance", body=GetPendingInstanceResponse),
        (status = 404, description = "Account not found or privileged action not found or not pending")
    ),
)]
pub async fn get_pending_instance(
    State(privileged_action_service): State<PrivilegedActionService>,
    Path((account_id, privileged_action_id)): Path<(AccountId, PrivilegedActionInstanceId)>,
) -> Result<Json<GetPendingInstanceResponse>, ApiError> {
    Ok(Json(GetPendingInstanceResponse {
        privileged_action_instance: privileged_action_service
            .get_pending_instance(GetPendingInstanceInput {
                account_id: &account_id,
                privileged_action_id: &privileged_action_id,
            })
            .await?
            .into(),
    }))
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
pub struct CancelPendingInstanceResponse {}

#[instrument(err, skip(privileged_action_service, request))]
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
) -> Result<Json<CancelPendingInstanceResponse>, ApiError> {
    privileged_action_service
        .cancel_pending_delay_and_notify_instance_by_token(
            CancelPendingDelayAndNotifyInstanceByTokenInput {
                cancellation_token: request.cancellation_token,
            },
        )
        .await?;

    Ok(Json(CancelPendingInstanceResponse {}))
}

#[instrument(err, skip(privileged_action_service))]
#[utoipa::path(
    post,
    path = "/api/accounts/:account_id/privileged-actions/:privileged_action_id/cancel",
    responses(
        (status = 200, description = "Privileged action instance cancelled", body=CancelPendingInstanceResponse),
        (status = 404, description = "Account not found")
    ),
)]
pub async fn cancel_pending_out_of_band_instance(
    Path((account_id, instance_id)): Path<(AccountId, PrivilegedActionInstanceId)>,
    State(privileged_action_service): State<PrivilegedActionService>,
) -> Result<Json<CancelPendingInstanceResponse>, ApiError> {
    privileged_action_service
        .cancel_pending_out_of_band_instance(&account_id, instance_id)
        .await?;
    Ok(Json(CancelPendingInstanceResponse {}))
}

#[derive(Serialize, Deserialize, Debug, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct ResendPendingInstanceResponse {}

#[instrument(err, skip(privileged_action_service))]
#[utoipa::path(
    post,
    path = "/api/accounts/:account_id/privileged-actions/:privileged_action_id/resend",
    responses(
        (status = 200, description = "Verification email resent", body=ResendPendingInstanceResponse),
        (status = 404, description = "Account or instance not found"),
        (status = 409, description = "Instance not pending, or resend limit reached"),
        (status = 429, description = "Resend requested before the cooldown elapsed")
    ),
)]
pub async fn resend_out_of_band_verification(
    Path((account_id, instance_id)): Path<(AccountId, PrivilegedActionInstanceId)>,
    State(privileged_action_service): State<PrivilegedActionService>,
) -> Result<Json<ResendPendingInstanceResponse>, ApiError> {
    privileged_action_service
        .resend_out_of_band_verification(&account_id, instance_id)
        .await?;
    Ok(Json(ResendPendingInstanceResponse {}))
}

#[derive(Serialize, Deserialize, Debug)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE", tag = "action")]
pub enum ProcessPrivilegedActionVerificationRequest {
    Cancel {
        web_auth_token: String,
    },
    Confirm {
        web_auth_token: String,
        #[serde(flatten)]
        submission: ConfirmSubmission,
    },
}

/// User-submitted payload to confirm a pending out-of-band privileged action.
///
/// Tagged by `privileged_action_type` so each privileged action can carry
/// action-specific confirmation parameters (e.g. a serial number typed by the
/// user for hardware-verification flows). Variants are struct-shaped on the
/// wire so they can be flattened into
/// [`ProcessPrivilegedActionVerificationRequest::Confirm`] without nesting.
#[derive(Serialize, Deserialize, Debug)]
#[serde(tag = "privileged_action_type", rename_all = "SCREAMING_SNAKE_CASE")]
pub enum ConfirmSubmission {
    LoosenTransactionVerificationPolicy {},
    VerifyHardwareSerial {
        submission_data: VerifyHardwareSerialSubmission,
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
    // Same lazy-expiry the confirm path runs, so a GET against an
    // expired-but-still-Pending record renders the lockout state
    // instead of letting the user enter a serial they can't submit.
    let privileged_action = privileged_action_service
        .expire_pending_out_of_band_if_overdue(privileged_action)
        .await
        .map_err(|e| html_error(StatusCode::INTERNAL_SERVER_ERROR, e))?;

    let status = privileged_action.get_record_status();
    match status {
        RecordStatus::Pending | RecordStatus::Failed => {}
        RecordStatus::Canceled | RecordStatus::Completed => {
            return Err(html_error(
                StatusCode::BAD_REQUEST,
                "Privileged action is not pending",
            ));
        }
    }

    let mut verification_params = serde_json::json!({
        "privilegedActionId": privileged_action.id,
        "privilegedActionType": privileged_action.privileged_action_type,
        // Page reads this flag and short-circuits to the lockout screen
        // on load when the record has already been terminated.
        "lockoutAtLoad": status == RecordStatus::Failed,
    });
    match privileged_action.privileged_action_type {
        PrivilegedActionType::LoosenTransactionVerificationPolicy => {
            let tx_policy_verification_payload = serde_json::from_value::<
                PutTransactionVerificationPolicyRequest,
            >(privileged_action.request)
            .map_err(|e| html_error(StatusCode::INTERNAL_SERVER_ERROR, e))?;
            verification_params["txPolicyVerification"] =
                serde_json::json!(tx_policy_verification_payload.policy);
            verification_params["useBip177"] =
                serde_json::json!(tx_policy_verification_payload.use_bip_177);
        }
        PrivilegedActionType::VerifyHardwareSerial => {
            // The expected serial is intentionally NOT injected; the
            // page submits the user-typed value to the server for
            // server-side comparison.
            let AuthorizationStrategyRecord::OutOfBand(ref out_of_band) =
                privileged_action.authorization_strategy
            else {
                return Err(html_error(
                    StatusCode::INTERNAL_SERVER_ERROR,
                    "VerifyHardwareSerial action has unexpected authorization strategy",
                ));
            };
            verification_params["attemptsRemaining"] =
                serde_json::json!(DEFAULT_OOB_MAX_ATTEMPTS.saturating_sub(out_of_band.attempts));
        }
        _ => {
            return Err(html_error(
                StatusCode::BAD_REQUEST,
                "Unsupported privileged action type",
            ));
        }
    };

    let template = get_template_for_action(&privileged_action.privileged_action_type)
        .map_err(|e| html_error(StatusCode::BAD_REQUEST, e))?;
    let html =
        inject_json_into_template(&template, "privileged-action-params", verification_params)
            .map_err(|e| html_error(StatusCode::INTERNAL_SERVER_ERROR, e))?;

    Ok((
        StatusCode::OK,
        [(header::CONTENT_TYPE, "text/html; charset=utf-8")],
        Html(html),
    ))
}

#[instrument(err, skip(privileged_action_service, account_service, request))]
async fn respond_to_out_of_band_privileged_action(
    State(privileged_action_service): State<PrivilegedActionService>,
    State(account_service): State<AccountService>,
    Json(request): Json<ProcessPrivilegedActionVerificationRequest>,
) -> Result<Json<ProcessPrivilegedActionVerificationResponse>, ApiError> {
    match request {
        ProcessPrivilegedActionVerificationRequest::Confirm {
            web_auth_token,
            submission,
        } => {
            confirm_privileged_action(
                &privileged_action_service,
                &account_service,
                submission,
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
    submission: ConfirmSubmission,
    web_auth_token: &str,
) -> Result<(), ApiError> {
    match submission {
        ConfirmSubmission::LoosenTransactionVerificationPolicy {} => {
            confirm_transaction_verification_policy(
                privileged_action_service,
                account_service,
                web_auth_token,
            )
            .await
        }
        ConfirmSubmission::VerifyHardwareSerial { submission_data } => {
            confirm_hardware_serial_verification(
                privileged_action_service,
                account_service,
                web_auth_token,
                &submission_data,
            )
            .await
        }
    }
}

async fn confirm_hardware_serial_verification(
    privileged_action_service: &PrivilegedActionService,
    account_service: &AccountService,
    web_auth_token: &str,
    submission: &VerifyHardwareSerialSubmission,
) -> Result<(), ApiError> {
    // Resolves the keyset to verify against via the active keyset (= the
    // sweep's destination at the time PR9's gate fires). No keyset id is
    // carried on the priv-action record.
    let privileged_action = privileged_action_service
        .get_by_web_auth_token::<()>(web_auth_token)
        .await?;
    let privileged_action = privileged_action_service
        .expire_pending_out_of_band_if_overdue(privileged_action)
        .await?;
    validate_out_of_band_authorization(&privileged_action.authorization_strategy)?;

    let account = account_service
        .fetch_account(FetchAccountInput {
            account_id: &privileged_action.account_id,
        })
        .await?;
    let Account::Full(full_account) = account else {
        return Err(ApiError::Specific {
            code: ErrorCode::KeysetNotAttested,
            detail: Some("account is not a FullAccount".to_string()),
            field: None,
        });
    };

    let active_keyset_id = full_account.active_keyset_id.clone();
    let private = full_account
        .active_spending_keyset()
        .and_then(|k| k.optional_private_multi_sig())
        .ok_or_else(|| ApiError::Specific {
            code: ErrorCode::KeysetNotAttested,
            detail: Some("active keyset is missing or not a PrivateMultiSig keyset".to_string()),
            field: None,
        })?;
    let attested = match &private.attested_hardware_serial {
        // Keyset is already Verified — a partial completion of a prior
        // confirm (keyset wrote, priv-action didn't). Repair by closing
        // out the still-Pending priv-action so the user gets the
        // completion email and any caller waiting on PA completion sees
        // it land.
        Some(AttestedHardwareSerial::Verified(_)) => {
            continue_out_of_band_privileged_action(
                privileged_action_service,
                &privileged_action,
                web_auth_token,
            )
            .await?;
            return Ok(());
        }
        Some(AttestedHardwareSerial::Pending(s)) => s.clone(),
        None => {
            return Err(ApiError::Specific {
                code: ErrorCode::KeysetNotAttested,
                detail: Some("active keyset has no attested hardware serial".to_string()),
                field: None,
            });
        }
    };

    let expected = normalize_serial_for_comparison(&attested);
    let submitted = normalize_serial_for_comparison(&submission.serial);
    if expected.as_bytes().ct_eq(submitted.as_bytes()).unwrap_u8() == 0 {
        let outcome = privileged_action_service
            .increment_out_of_band_attempts::<()>(&privileged_action.id)
            .await?;
        return Err(match outcome {
            IncrementOutOfBandAttemptsResult::Recorded { remaining_attempts } => {
                ApiError::Specific {
                    code: ErrorCode::HardwareSerialMismatch,
                    detail: Some(
                        serde_json::json!({ "remainingAttempts": remaining_attempts }).to_string(),
                    ),
                    field: Some("serial".to_string()),
                }
            }
            IncrementOutOfBandAttemptsResult::AttemptsExhausted => ApiError::Specific {
                code: ErrorCode::OutOfBandVerificationSessionEnded,
                detail: None,
                field: None,
            },
        });
    }

    // Keyset write first, then priv-action completion: the two aren't
    // atomic, and keyset-first leaves a benign stale-Pending priv-action
    // on failure (lazy-expiry cleans up) rather than a Completed priv-
    // action + completion email against a still-Pending keyset.
    account_service
        .mark_hardware_serial_verified(&privileged_action.account_id, &active_keyset_id)
        .await?;
    continue_out_of_band_privileged_action(
        privileged_action_service,
        &privileged_action,
        web_auth_token,
    )
    .await?;
    Ok(())
}

/// Canonicalize a hardware serial for comparison: keep alphanumeric
/// characters only, uppercase. Strips whitespace, dashes, and any
/// separators the user might have typed in addition to or instead of the
/// serial digits.
fn normalize_serial_for_comparison(serial: &str) -> String {
    serial
        .chars()
        .filter(|c| c.is_alphanumeric())
        .flat_map(|c| c.to_uppercase())
        .collect()
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
    let privileged_action = privileged_action_service
        .expire_pending_out_of_band_if_overdue(privileged_action)
        .await?;

    validate_out_of_band_authorization(&privileged_action.authorization_strategy)?;

    // The initiate route rejects unsupported currencies, but a pending request stored before
    // that check existed is only caught here. Validate before continuing the privileged
    // action: continuing marks the record Completed and sends the completion notification,
    // which must not happen for a request that will never be applied.
    let policy: TransactionVerificationPolicy = privileged_action.request.policy.clone().into();
    if !policy.uses_supported_currency() {
        return Err(ApiError::GenericForbidden(
            "valid supported currency required to set a transaction verification policy"
                .to_string(),
        ));
    }

    // Continue the privileged action flow
    continue_out_of_band_privileged_action(
        privileged_action_service,
        &privileged_action,
        web_auth_token,
    )
    .await?;

    // Apply the policy change
    let account_id = privileged_action.account_id.clone();
    account_service
        .put_transaction_verification_policy(&account_id, policy)
        .await?;

    Ok(())
}

fn validate_out_of_band_authorization(
    authorization_strategy: &AuthorizationStrategyRecord,
) -> Result<(), ApiError> {
    let AuthorizationStrategyRecord::OutOfBand(out_of_band) = authorization_strategy else {
        return Err(ApiError::GenericBadRequest(
            "Invalid authorization strategy: expected OutOfBand".to_string(),
        ));
    };

    match out_of_band.status {
        RecordStatus::Pending => Ok(()),
        // 410 so the approval page can render lockout identically for
        // max-attempts and lazy-expiry.
        RecordStatus::Failed => Err(ApiError::Specific {
            code: ErrorCode::OutOfBandVerificationSessionEnded,
            detail: None,
            field: None,
        }),
        RecordStatus::Canceled | RecordStatus::Completed => Err(ApiError::GenericBadRequest(
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
                privileged_action_request: &continue_request,
                request_validator: PrivilegedActionRequestValidatorBuilder::default().build()?,
            })
            .await?;

    match result {
        AuthorizePrivilegedActionOutput::Authorized(action) => Ok(action),
        AuthorizePrivilegedActionOutput::Pending(_) => Err(ApiError::GenericBadRequest(
            "Expected authorized response, but action is still pending".to_string(),
        )),
    }
}

#[derive(Debug, Deserialize, ToSchema)]
#[serde(tag = "action", rename_all = "snake_case")]
pub enum FormatValueRequest {
    SetSpendWithoutHardware {
        amount: u64,
        currency_code: CurrencyCode,
        locale: String,
    },
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
pub struct FormatValueResponse {
    pub formatted_value: String,
}

#[utoipa::path(
    post,
    path = "/api/accounts/{account_id}/action-proof/format-value",
    request_body = FormatValueRequest,
    responses(
        (status = 200, description = "Formatted display value", body = FormatValueResponse),
        (status = 400, description = "Unsupported action"),
    ),
)]
#[instrument(skip(request))]
async fn format_value(
    Json(request): Json<FormatValueRequest>,
) -> Result<Json<FormatValueResponse>, ApiError> {
    let formatted_value = match request {
        FormatValueRequest::SetSpendWithoutHardware {
            amount,
            currency_code,
            locale,
        } => {
            let money = types::account::money::Money {
                amount,
                currency_code,
            };
            money.format_display(&types::account::money::MoneyLocale::from_bcp47(&locale))
        }
    };
    Ok(Json(FormatValueResponse { formatted_value }))
}

#[cfg(test)]
mod tests {
    use super::*;

    /// The wire shape the existing in-browser JS uses to confirm a
    /// `LoosenTransactionVerificationPolicy` action MUST continue to parse and
    /// re-serialize identically after the `ConfirmSubmission` refactor.
    #[test]
    fn confirm_loosen_tx_verification_policy_round_trip() {
        let wire_json = serde_json::json!({
            "action": "CONFIRM",
            "privileged_action_type": "LOOSEN_TRANSACTION_VERIFICATION_POLICY",
            "web_auth_token": "abc123",
        });

        let parsed: ProcessPrivilegedActionVerificationRequest =
            serde_json::from_value(wire_json.clone()).expect("deserialize from wire");

        match &parsed {
            ProcessPrivilegedActionVerificationRequest::Confirm {
                web_auth_token,
                submission,
            } => {
                assert_eq!(web_auth_token, "abc123");
                assert!(matches!(
                    submission,
                    ConfirmSubmission::LoosenTransactionVerificationPolicy {}
                ));
            }
            other => panic!("expected Confirm, got {:?}", other),
        }

        let reserialized = serde_json::to_value(&parsed).expect("serialize");
        assert_eq!(reserialized, wire_json);
    }

    #[test]
    fn cancel_round_trip() {
        let wire_json = serde_json::json!({
            "action": "CANCEL",
            "web_auth_token": "tok-xyz",
        });

        let parsed: ProcessPrivilegedActionVerificationRequest =
            serde_json::from_value(wire_json.clone()).expect("deserialize from wire");

        match &parsed {
            ProcessPrivilegedActionVerificationRequest::Cancel { web_auth_token } => {
                assert_eq!(web_auth_token, "tok-xyz");
            }
            other => panic!("expected Cancel, got {:?}", other),
        }

        let reserialized = serde_json::to_value(&parsed).expect("serialize");
        assert_eq!(reserialized, wire_json);
    }

    /// The `ConfirmSubmission` enum is designed to be extended with new
    /// privileged action types that carry their own confirmation parameters
    /// (e.g. a serial number typed by the user). The new fields must flatten
    /// next to `web_auth_token` without nesting.
    ///
    /// To exercise that design contract without committing this PR to a
    /// concrete future variant, we mirror the production layout in a local
    /// parallel enum with a hypothetical second variant.
    #[test]
    fn confirm_supports_new_submission_variant() {
        #[derive(Serialize, Deserialize, Debug, PartialEq)]
        #[serde(tag = "privileged_action_type", rename_all = "SCREAMING_SNAKE_CASE")]
        enum FutureConfirmSubmission {
            LoosenTransactionVerificationPolicy {},
            ActivateHardwareVerification { serial_number: String },
        }

        #[derive(Serialize, Deserialize, Debug, PartialEq)]
        #[serde(rename_all = "SCREAMING_SNAKE_CASE", tag = "action")]
        enum FutureProcessRequest {
            Confirm {
                web_auth_token: String,
                #[serde(flatten)]
                submission: FutureConfirmSubmission,
            },
        }

        let wire_json = serde_json::json!({
            "action": "CONFIRM",
            "privileged_action_type": "ACTIVATE_HARDWARE_VERIFICATION",
            "web_auth_token": "tok",
            "serial_number": "BK-12345",
        });

        let parsed: FutureProcessRequest =
            serde_json::from_value(wire_json.clone()).expect("deserialize from wire");

        let FutureProcessRequest::Confirm {
            web_auth_token,
            submission,
        } = &parsed;
        assert_eq!(web_auth_token, "tok");
        assert_eq!(
            submission,
            &FutureConfirmSubmission::ActivateHardwareVerification {
                serial_number: "BK-12345".to_string(),
            },
        );

        let reserialized = serde_json::to_value(&parsed).expect("serialize");
        assert_eq!(reserialized, wire_json);
    }
}
