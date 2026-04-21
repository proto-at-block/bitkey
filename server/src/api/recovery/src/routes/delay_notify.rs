use std::collections::HashSet;

use crate::helpers::{check_hw_signature_with_domain_tag, DOMAIN_TAG_AUTH_ROTATION};
use crate::{
    ensure_pubkeys_unique,
    entities::{RecoveryDestination, RecoveryType},
    error::RecoveryError,
    metrics,
    repository::RecoveryRepository,
    service::social::challenge::Service as SocialChallengeService,
    state_machine::{run_recovery_fsm, RecoveryEvent},
};
use crate::{
    service::inheritance::{
        recreate_pending_claims_for_beneficiary::RecreatePendingClaimsForBeneficiaryInput,
        Service as InheritanceService,
    },
    state_machine::{
        pending_recovery::PendingRecoveryResponse, PendingDelayNotifyRecovery, RecoveryResponse,
    },
};
use account::{
    error::AccountError,
    service::{
        ClearPushTouchpointsInput, CreateAndRotateAuthKeysInput, FetchAccountInput,
        Service as AccountService,
    },
};
use authn_authz::{Action, Authorization, AuthorizationRequirements};
use axum::{
    extract::{Path, State},
    routing::{delete, get, post, put},
    Extension, Json, Router,
};
use bdk_utils::{bdk::bitcoin::secp256k1::PublicKey, signature::check_signature};
use comms_verification::{
    error::CommsVerificationError, InitiateVerificationForScopeInput,
    Service as CommsVerificationService, VerifyForScopeInput,
};
use errors::{ApiError, ErrorCode};
use instrumentation::middleware::HardwareSerialHeader;
use experimentation::claims::ExperimentationClaims;
use feature_flags::{flag::evaluate_flag_value, service::Service as FeatureFlagsService};
use http_server::{
    router::RouterBuilder,
    swagger::{SwaggerEndpoint, Url},
};
use instrumentation::metrics::KeyValue;
use notification::{entities::NotificationTouchpoint, service::Service as NotificationService};
use serde::{Deserialize, Serialize};
use serde_json::Value;
use serde_with::{base64::Base64, serde_as};
use time::Duration;
use tracing::{event, instrument, Level};
use types::account::{
    entities::{
        Account, CommsVerificationScope, Factor, FullAccountAuthKeysInput, HardwareType, Touchpoint,
    },
    identifiers::{AccountId, TouchpointId},
};
use types::authn_authz::cognito::CognitoUser;
use userpool::userpool::UserPoolService;
use utoipa::{OpenApi, ToSchema};
use wsm_rust_client::{SigningService, WsmClient};

use super::INHERITANCE_ENABLED_FLAG_KEY;

#[derive(Clone, axum_macros::FromRef)]
pub struct RouteState(
    pub AccountService,
    pub InheritanceService,
    pub NotificationService,
    pub CommsVerificationService,
    pub UserPoolService,
    pub RecoveryRepository,
    pub SocialChallengeService,
    pub FeatureFlagsService,
    pub WsmClient,
    pub repository::anti_replay::AntiReplayRepository,
    pub repository::public_key::PublicKeyRepository,
);

impl RouterBuilder for RouteState {
    fn unauthed_router(&self) -> Router {
        Router::new()
            .route(
                "/api/accounts/:account_id/delay-notify/complete",
                post(complete_delay_notify_transaction),
            )
            .route_layer(metrics::FACTORY.route_layer("recovery".to_owned()))
            .with_state(self.to_owned())
    }

    fn recovery_authed_router(&self) -> Router {
        Router::new()
            .route(
                "/api/accounts/:account_id/recovery/evaluate-pin",
                post(evaluate_pin),
            )
            .route_layer(metrics::FACTORY.route_layer("recovery".to_owned()))
            .with_state(self.to_owned())
    }

    fn account_authed_router(&self) -> Router {
        Router::new()
            .route(
                "/api/accounts/:account_id/delay-notify",
                post(create_delay_notify),
            )
            .route(
                "/api/accounts/:account_id/delay-notify/test",
                put(update_delay_for_test_account),
            )
            .route(
                "/api/accounts/:account_id/delay-notify",
                delete(cancel_delay_notify),
            )
            .route(
                "/api/accounts/:account_id/recovery",
                get(get_recovery_status),
            )
            .route(
                "/api/accounts/:account_id/delay-notify/send-verification-code",
                post(send_verification_code),
            )
            .route(
                "/api/accounts/:account_id/delay-notify/verify-code",
                post(verify_code),
            )
            .route(
                "/api/accounts/:account_id/authentication-keys",
                post(rotate_authentication_keys),
            )
            .route_layer(metrics::FACTORY.route_layer("recovery".to_owned()))
            .with_state(self.to_owned())
    }
}

impl From<RouteState> for SwaggerEndpoint {
    fn from(_: RouteState) -> Self {
        (
            Url::new("Delay & Notify", "/docs/delay_notify/openapi.json"),
            ApiDoc::openapi(),
        )
    }
}

#[derive(OpenApi)]
#[openapi(
    paths(
        cancel_delay_notify,
        complete_delay_notify_transaction,
        create_delay_notify,
        get_recovery_status,
        rotate_authentication_keys,
        send_verification_code,
        verify_code,
    ),
    components(
        schemas(
            CompleteDelayNotifyRequest,
            CompleteDelayNotifyResponse,
            CreateAccountDelayNotifyRequest,
            Factor,
            FullAccountAuthKeysInput,
            PendingRecoveryResponse,
            PendingDelayNotifyRecovery,
            RecoveryResponse,
            RotateAuthenticationKeysRequest,
            RotateAuthenticationKeysResponse,
            SendAccountVerificationCodeRequest,
            SendAccountVerificationCodeResponse,
            VerifyAccountVerificationCodeRequest,
            VerifyAccountVerificationCodeResponse,
        )
    ),
    tags(
        (name = "Delay & Notify", description = "Endpoints for our Delay & Notify feature to recover full access to an account."),
    )
)]
struct ApiDoc;

/// Maps a lost factor to the corresponding create-recovery action proof.
fn create_recovery_action(lost_factor: Factor) -> Action {
    match lost_factor {
        Factor::App => Action::CreateLostAppRecovery,
        Factor::Hw => Action::CreateLostHardwareRecovery,
    }
}

/// Maps a lost factor to the corresponding cancel-recovery action proof.
fn cancel_recovery_action(lost_factor: Factor) -> Action {
    match lost_factor {
        Factor::App => Action::CancelLostAppRecovery,
        Factor::Hw => Action::CancelLostHardwareRecovery,
    }
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct CreateAccountDelayNotifyRequest {
    pub lost_factor: Factor, // TODO: [W-774] Update visibility of struct after migration
    pub delay_period_num_sec: Option<i64>, // TODO: [W-774] Update visibility of struct after migration
    pub auth: FullAccountAuthKeysInput, // TODO: [W-774] Update visibility of struct after migration, [W-973] Remove this before beta
}

#[instrument(
    err,
    skip(
        account_service,
        inheritance_service,
        notification_service,
        recovery_service,
        social_challenge_service,
        comms_verification_service,
        feature_flags_service,
        anti_replay_repository,
        public_key_repository,
    )
)]
#[utoipa::path(
    post,
    path = "/api/accounts/{account_id}/delay-notify",
    params(
        ("account_id" = AccountId, Path, description = "AccountId"),
    ),
    request_body = CreateAccountDelayNotifyRequest,
    responses(
        (status = 200, description = "D&N Recovery was created", body=PendingRecoveryResponse),
        (status = 404, description = "Account not found")
    ),
)]
pub async fn create_delay_notify(
    Path(account_id): Path<AccountId>,
    State(account_service): State<AccountService>,
    State(inheritance_service): State<InheritanceService>,
    State(notification_service): State<NotificationService>,
    State(recovery_service): State<RecoveryRepository>,
    State(social_challenge_service): State<SocialChallengeService>,
    State(comms_verification_service): State<CommsVerificationService>,
    State(feature_flags_service): State<FeatureFlagsService>,
    State(anti_replay_repository): State<repository::anti_replay::AntiReplayRepository>,
    State(public_key_repository): State<repository::public_key::PublicKeyRepository>,
    auth: Authorization,
    hw_serial: HardwareSerialHeader,
    Json(request): Json<CreateAccountDelayNotifyRequest>,
) -> Result<Json<Value>, ApiError> {
    // Block W3 hardware claiming to be W1
    HardwareType::reject_w3_claiming_w1(hw_serial.as_deref(), request.auth.hardware_type)
        .map_err(|e| ApiError::GenericForbidden(e.to_string()))?;
    // Require at least one factor for creating a recovery.
    // The state machine's to_actor logic enforces the signing factor isn't the lost factor.
    let full_account = account_service
        .fetch_full_account(FetchAccountInput {
            account_id: &account_id,
        })
        .await?;
    let hardware_type = full_account
        .active_hardware_type()
        .map_err(|e| ApiError::GenericInternalApplicationError(e.to_string()))?;

    let result =
        AuthorizationRequirements::new(create_recovery_action(request.lost_factor), hardware_type)
            .proof(authn_authz::ProofRequirement::AnyFactor)
            .execute(
                &auth,
                &anti_replay_repository,
                move |authorized_request| async move {
                    let destination = RecoveryDestination {
                        source_auth_keys_id: full_account
                            .common_fields
                            .active_auth_keys_id
                            .to_owned(),
                        app_auth_pubkey: request.auth.app,
                        hardware_auth_pubkey: request.auth.hardware,
                        recovery_auth_pubkey: request.auth.recovery,
                        hardware_type: request.auth.hardware_type,
                    };
                    let events = vec![
                        RecoveryEvent::CheckAccountRecoveryState,
                        RecoveryEvent::CreateRecovery {
                            account: full_account.clone(),
                            lost_factor: request.lost_factor,
                            destination,
                            authorized_request,
                        },
                    ];
                    let create_response = run_recovery_fsm(
                        account_id.clone(),
                        events,
                        &account_service,
                        &inheritance_service,
                        &recovery_service,
                        &notification_service,
                        &social_challenge_service,
                        &comms_verification_service,
                        &feature_flags_service,
                        &public_key_repository,
                    )
                    .await
                    .map(|r| r.response())?;

                    //TODO: Remove this once recovery syncer on mobile isn't running always
                    if request.delay_period_num_sec.is_some()
                        && full_account.common_fields.properties.is_test_account
                    {
                        let Json(v) = update_recovery_delay_for_test_account(
                            account_id,
                            account_service,
                            inheritance_service,
                            notification_service,
                            recovery_service,
                            social_challenge_service,
                            comms_verification_service,
                            feature_flags_service,
                            public_key_repository,
                            UpdateDelayForTestRecoveryRequest {
                                delay_period_num_sec: request.delay_period_num_sec,
                            },
                        )
                        .await?;
                        Ok::<_, ApiError>(v)
                    } else {
                        Ok(create_response)
                    }
                },
            )
            .await?;

    Ok(Json(result))
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct UpdateDelayForTestRecoveryRequest {
    pub delay_period_num_sec: Option<i64>, // TODO: [W-774] Update visibility of struct after migration
}

async fn update_recovery_delay_for_test_account(
    account_id: AccountId,
    account_service: AccountService,
    inheritance_service: InheritanceService,
    notification_service: NotificationService,
    recovery_service: RecoveryRepository,
    social_challenge_service: SocialChallengeService,
    comms_verification_service: CommsVerificationService,
    feature_flags_service: FeatureFlagsService,
    public_key_repository: repository::public_key::PublicKeyRepository,
    request: UpdateDelayForTestRecoveryRequest,
) -> Result<Json<Value>, ApiError> {
    let events = vec![
        RecoveryEvent::CheckAccountRecoveryState,
        RecoveryEvent::UpdateDelayForTestAccountRecovery {
            delay_period_num_sec: request.delay_period_num_sec,
        },
    ];
    run_recovery_fsm(
        account_id,
        events,
        &account_service,
        &inheritance_service,
        &recovery_service,
        &notification_service,
        &social_challenge_service,
        &comms_verification_service,
        &feature_flags_service,
        &public_key_repository,
    )
    .await
    .map(|r| Json(r.response()))
}

#[instrument(
    err,
    skip(
        account_service,
        inheritance_service,
        notification_service,
        recovery_service,
        social_challenge_service,
        comms_verification_service,
        feature_flags_service,
        public_key_repository,
    )
)]
#[utoipa::path(
    put,
    path = "/api/accounts/{account_id}/delay-notify/test",
    params(
        ("account_id" = AccountId, Path, description = "AccountId"),
    ),
    request_body = UpdateDelayForTestRecoveryRequest,
    responses(
        (status = 200, description = "D&N Recovery was updated for test accounts only", body=PendingRecoveryResponse),
        (status = 400, description = "Could not update the delay for this account"),
        (status = 404, description = "Account not found")
    ),
)]
pub async fn update_delay_for_test_account(
    Path(account_id): Path<AccountId>,
    State(account_service): State<AccountService>,
    State(inheritance_service): State<InheritanceService>,
    State(notification_service): State<NotificationService>,
    State(recovery_service): State<RecoveryRepository>,
    State(social_challenge_service): State<SocialChallengeService>,
    State(comms_verification_service): State<CommsVerificationService>,
    State(feature_flags_service): State<FeatureFlagsService>,
    State(public_key_repository): State<repository::public_key::PublicKeyRepository>,
    _auth: Authorization,
    Json(request): Json<UpdateDelayForTestRecoveryRequest>,
) -> Result<Json<Value>, ApiError> {
    update_recovery_delay_for_test_account(
        account_id,
        account_service,
        inheritance_service,
        notification_service,
        recovery_service,
        social_challenge_service,
        comms_verification_service,
        feature_flags_service,
        public_key_repository,
        request,
    )
    .await
}

#[instrument(
    err,
    skip(
        account_service,
        inheritance_service,
        notification_service,
        recovery_service,
        social_challenge_service,
        comms_verification_service,
        feature_flags_service,
        anti_replay_repository,
        public_key_repository,
    )
)]
#[utoipa::path(
    delete,
    path = "/api/accounts/{account_id}/delay-notify",
    params(
        ("account_id" = AccountId, Path, description = "AccountId"),
    ),
    responses(
        (status = 200, description = "D&N Recovery was canceled"),
        (status = 404, description = "Account not found")
    ),
)]
pub async fn cancel_delay_notify(
    Path(account_id): Path<AccountId>,
    State(account_service): State<AccountService>,
    State(inheritance_service): State<InheritanceService>,
    State(notification_service): State<NotificationService>,
    State(recovery_service): State<RecoveryRepository>,
    State(social_challenge_service): State<SocialChallengeService>,
    State(comms_verification_service): State<CommsVerificationService>,
    State(feature_flags_service): State<FeatureFlagsService>,
    State(anti_replay_repository): State<repository::anti_replay::AntiReplayRepository>,
    State(public_key_repository): State<repository::public_key::PublicKeyRepository>,
    auth: Authorization,
) -> Result<(), ApiError> {
    // Require at least one factor for canceling a recovery.
    // The state machine's to_actor logic determines which factor is acting.
    let full_account = account_service
        .fetch_full_account(FetchAccountInput {
            account_id: &account_id,
        })
        .await?;
    let hardware_type = full_account
        .active_hardware_type()
        .map_err(|e| ApiError::GenericInternalApplicationError(e.to_string()))?;

    // Look up the active recovery to determine which cancel action proof to require.
    // This adds a DB call before the FSM execution, but cancellation is not a hot path
    // and the server must be authoritative about which factor was lost (cannot trust client).
    let cancel_action = match recovery_service
        .fetch_pending(&account_id, RecoveryType::DelayAndNotify)
        .await
    {
        Ok(Some(recovery)) => {
            cancel_recovery_action(recovery.get_lost_factor().ok_or_else(|| {
                ApiError::GenericInternalApplicationError(
                    "Active recovery has no lost factor".to_string(),
                )
            })?)
        }
        Ok(None) => return Err(RecoveryError::NoExistingRecovery.into()),
        Err(e) => return Err(ApiError::GenericInternalApplicationError(e.to_string())),
    };

    AuthorizationRequirements::new(cancel_action, hardware_type)
        .or_action(Action::CancelConflictingRecovery)
        .proof(authn_authz::ProofRequirement::AnyFactor)
        .execute(
            &auth,
            &anti_replay_repository,
            |authorized_request| async move {
                let events = vec![
                    RecoveryEvent::CheckAccountRecoveryState,
                    RecoveryEvent::CancelRecovery { authorized_request },
                ];
                run_recovery_fsm(
                    account_id,
                    events,
                    &account_service,
                    &inheritance_service,
                    &recovery_service,
                    &notification_service,
                    &social_challenge_service,
                    &comms_verification_service,
                    &feature_flags_service,
                    &public_key_repository,
                )
                .await?;

                Ok::<_, ApiError>(())
            },
        )
        .await
}

#[instrument(
    err,
    skip(
        account_service,
        inheritance_service,
        notification_service,
        recovery_service,
        social_challenge_service,
        comms_verification_service,
        feature_flags_service,
        public_key_repository,
    )
)]
#[utoipa::path(
    get,
    path = "/api/accounts/{account_id}/recovery",
    params(
        ("account_id" = AccountId, Path, description = "AccountId"),
    ),
    responses(
        (status = 200, description = "D&N Recovery fetched status", body=RecoveryResponse),
        (status = 404, description = "Account or D&N recovery not found")
    ),
)]
pub async fn get_recovery_status(
    Path(account_id): Path<AccountId>,
    State(account_service): State<AccountService>,
    State(inheritance_service): State<InheritanceService>,
    State(notification_service): State<NotificationService>,
    State(recovery_service): State<RecoveryRepository>,
    State(social_challenge_service): State<SocialChallengeService>,
    State(comms_verification_service): State<CommsVerificationService>,
    State(feature_flags_service): State<FeatureFlagsService>,
    State(public_key_repository): State<repository::public_key::PublicKeyRepository>,
) -> Result<Json<Value>, ApiError> {
    let events = vec![RecoveryEvent::CheckAccountRecoveryState];
    run_recovery_fsm(
        account_id,
        events,
        &account_service,
        &inheritance_service,
        &recovery_service,
        &notification_service,
        &social_challenge_service,
        &comms_verification_service,
        &feature_flags_service,
        &public_key_repository,
    )
    .await
    .map(|r| Json(r.response()))
}

#[derive(Debug, Serialize, Deserialize, Clone, ToSchema)]
pub struct CompleteDelayNotifyRequest {
    pub challenge: String,
    pub app_signature: String,
    pub hardware_signature: String,
}

#[derive(Deserialize, Serialize, ToSchema)]
pub struct CompleteDelayNotifyResponse {}

#[instrument(
    err,
    skip(
        account_service,
        inheritance_service,
        recovery_service,
        notification_service,
        social_challenge_service,
        user_pool_service,
        comms_verification_service,
        feature_flags_service,
        public_key_repository,
    )
)]
#[utoipa::path(
    post,
    path = "/api/accounts/{account_id}/delay-notify/complete",
    params(
        ("account_id" = AccountId, Path, description = "AccountId"),
    ),
    request_body = CompleteDelayNotifyRequest,
    responses(
        (status = 200, description = "D&N Recovery transaction was completed", body=CompleteDelayNotifyResponse),
        (status = 400, description = "D&N Recovery not found or recovery still pending."),
        (status = 404, description = "Account not found, D&N recovery not found or D&N recovery still pending.")
    ),
)]
pub async fn complete_delay_notify_transaction(
    Path(account_id): Path<AccountId>,
    State(notification_service): State<NotificationService>,
    State(account_service): State<AccountService>,
    State(inheritance_service): State<InheritanceService>,
    State(recovery_service): State<RecoveryRepository>,
    State(social_challenge_service): State<SocialChallengeService>,
    State(comms_verification_service): State<CommsVerificationService>,
    State(user_pool_service): State<UserPoolService>,
    State(feature_flags_service): State<FeatureFlagsService>,
    State(public_key_repository): State<repository::public_key::PublicKeyRepository>,
    experimentation_claims: ExperimentationClaims,
    Json(request): Json<CompleteDelayNotifyRequest>,
) -> Result<Json<Value>, ApiError> {
    let events = vec![
        RecoveryEvent::CheckAccountRecoveryState,
        RecoveryEvent::CheckEligibleForCompletion {
            challenge: request.challenge,
            app_signature: request.app_signature,
            hardware_signature: request.hardware_signature,
        },
        RecoveryEvent::RotateKeyset {
            user_pool_service,
            experimentation_claims,
        },
    ];
    run_recovery_fsm(
        account_id,
        events,
        &account_service,
        &inheritance_service,
        &recovery_service,
        &notification_service,
        &social_challenge_service,
        &comms_verification_service,
        &feature_flags_service,
        &public_key_repository,
    )
    .await
    .map(|r| Json(r.response()))
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct SendAccountVerificationCodeRequest {
    pub touchpoint_id: TouchpointId, // TODO: [W-774] Update visibility of struct after migration
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct SendAccountVerificationCodeResponse {}

fn cognito_user_to_factor(cognito_user: &CognitoUser) -> Result<Factor, ApiError> {
    match cognito_user {
        CognitoUser::App(_) => Ok(Factor::App),
        CognitoUser::Hardware(_) => Ok(Factor::Hw),
        CognitoUser::Recovery(_) => Err(ApiError::GenericForbidden(
            "Recovery factor cannot perform this action".to_string(),
        )),
    }
}

#[instrument(err, skip(account_service, comms_verification_service))]
#[utoipa::path(
    post,
    path = "/api/accounts/{account_id}/delay-notify/send-verification-code",
    params(
        ("account_id" = AccountId, Path, description = "AccountId"),
    ),
    request_body = SendAccountVerificationCodeRequest,
    responses(
        (status = 200, description = "Verification code sent", body=SendAccountVerificationCodeResponse),
        (status = 404, description = "Touchpoint not found")
    ),
)]
pub async fn send_verification_code(
    Path(account_id): Path<AccountId>,
    State(account_service): State<AccountService>,
    State(comms_verification_service): State<CommsVerificationService>,
    Extension(cognito_user): Extension<CognitoUser>,
    Json(request): Json<SendAccountVerificationCodeRequest>,
) -> Result<Json<SendAccountVerificationCodeResponse>, ApiError> {
    let full_account = account_service
        .fetch_full_account(FetchAccountInput {
            account_id: &account_id,
        })
        .await?;
    let account = Account::Full(full_account);

    let actor = cognito_user_to_factor(&cognito_user)?;
    let scope = CommsVerificationScope::DelayNotifyActor(actor);

    let touchpoint = account
        .get_touchpoint_by_id(request.touchpoint_id.clone())
        .ok_or(AccountError::TouchpointNotFound)?;

    match touchpoint {
        Touchpoint::Email { active, .. } | Touchpoint::Phone { active, .. } => {
            if !*active {
                return Err(RecoveryError::TouchpointStatusMismatch.into());
            }
        }
        _ => {
            return Err(RecoveryError::TouchpointTypeMismatch.into());
        }
    }

    comms_verification_service
        .initiate_verification_for_scope(InitiateVerificationForScopeInput {
            account_id: &account_id,
            account_properties: &account.get_common_fields().properties,
            scope,
            only_touchpoints: Some(HashSet::from([NotificationTouchpoint::from(
                touchpoint.to_owned(),
            )])),
        })
        .await?;

    Ok(Json(SendAccountVerificationCodeResponse {}))
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct VerifyAccountVerificationCodeRequest {
    pub verification_code: String, // TODO: [W-774] Update visibility of struct after migration
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct VerifyAccountVerificationCodeResponse {}

#[instrument(err, skip(comms_verification_service, request))]
#[utoipa::path(
    post,
    path = "/api/accounts/{account_id}/delay-notify/verify-code",
    params(
        ("account_id" = AccountId, Path, description = "AccountId"),
    ),
    request_body = VerifyAccountVerificationCodeRequest,
    responses(
        (status = 200, description = "Verification code sent", body=VerifyAccountVerificationCodeResponse),
        (status = 404, description = "Touchpoint not found")
    ),
)]
pub async fn verify_code(
    Path(account_id): Path<AccountId>,
    State(comms_verification_service): State<CommsVerificationService>,
    Extension(cognito_user): Extension<CognitoUser>,
    Json(request): Json<VerifyAccountVerificationCodeRequest>,
) -> Result<Json<VerifyAccountVerificationCodeResponse>, ApiError> {
    let actor = cognito_user_to_factor(&cognito_user)?;
    let scope = CommsVerificationScope::DelayNotifyActor(actor);

    comms_verification_service
        .verify_for_scope(VerifyForScopeInput {
            account_id: &account_id,
            scope,
            code: request.verification_code,
            duration: Duration::minutes(10),
        })
        .await
        .map_err(|e| {
            if matches!(e, CommsVerificationError::CodeMismatch) {
                metrics::DELAY_NOTIFY_CODE_SUBMITTED
                    .add(1, &[KeyValue::new(metrics::CODE_MATCHED_KEY, false)]);
            }
            e
        })?;

    metrics::DELAY_NOTIFY_CODE_SUBMITTED.add(1, &[KeyValue::new(metrics::CODE_MATCHED_KEY, true)]);

    Ok(Json(VerifyAccountVerificationCodeResponse {}))
}

#[derive(Debug, Serialize, Deserialize, Clone, ToSchema)]
pub struct AuthenticationKey {
    pub key: PublicKey,
    pub signature: String,
}

#[derive(Debug, Serialize, Deserialize, Clone, ToSchema)]
pub struct RotateAuthenticationKeysRequest {
    pub application: AuthenticationKey,
    pub hardware: AuthenticationKey,
    pub recovery: Option<AuthenticationKey>,
    #[serde(default)]
    pub hardware_type: HardwareType,
}

#[derive(Serialize, Deserialize, ToSchema)]
pub struct RotateAuthenticationKeysResponse {}

#[instrument(
    err,
    skip(
        notification_service,
        account_service,
        inheritance_service,
        recovery_service,
        social_challenge_service,
        comms_verification_service,
        user_pool_service,
        feature_flags_service,
        anti_replay_repository,
        public_key_repository,
    )
)]
#[utoipa::path(
    post,
    path = "/api/accounts/{account_id}/authentication-keys",
    params(
        ("account_id" = AccountId, Path, description = "AccountId"),
    ),
    request_body = RotateAuthenticationKeysRequest,
    responses(
        (status = 200, description = "Rotation of app authentication key was completed.", body=RotateAuthenticationKeysResponse),
        (status = 400, description = "Rotation of app authentication key failed due to invalid signature or keyset."),
        (status = 404, description = "Account not found.")
    ),
)]
pub async fn rotate_authentication_keys(
    Path(account_id): Path<AccountId>,
    State(notification_service): State<NotificationService>,
    State(account_service): State<AccountService>,
    State(inheritance_service): State<InheritanceService>,
    State(recovery_service): State<RecoveryRepository>,
    State(social_challenge_service): State<SocialChallengeService>,
    State(comms_verification_service): State<CommsVerificationService>,
    State(user_pool_service): State<UserPoolService>,
    State(feature_flags_service): State<FeatureFlagsService>,
    State(anti_replay_repository): State<repository::anti_replay::AntiReplayRepository>,
    State(public_key_repository): State<repository::public_key::PublicKeyRepository>,
    auth: Authorization,
    experimentation_claims: ExperimentationClaims,
    hw_serial: HardwareSerialHeader,
    Json(request): Json<RotateAuthenticationKeysRequest>,
) -> Result<Json<RotateAuthenticationKeysResponse>, ApiError> {
    // Block W3 hardware claiming to be W1
    HardwareType::reject_w3_claiming_w1(hw_serial.as_deref(), request.hardware_type)
        .map_err(|e| ApiError::GenericForbidden(e.to_string()))?;
    let account = account_service
        .fetch_full_account(FetchAccountInput {
            account_id: &account_id,
        })
        .await?;
    let hardware_type = account
        .active_hardware_type()
        .map_err(|e| ApiError::GenericInternalApplicationError(e.to_string()))?;

    let result = AuthorizationRequirements::new(Action::RotateAppAuthKeys, hardware_type)
        .execute(&auth, &anti_replay_repository, move |authorized_request| async move {
            let current_auth = account
                .active_auth_keys()
                .ok_or(AccountError::InvalidKeysetState)?;

            check_signature(
                &account_id.to_string(),
                &request.application.signature,
                request.application.key,
            )?;
            // The hardware signature is produced by the hw auth key signing the account ID.
            // Use domain-tagged verification with fallback for firmware that doesn't tag yet.
            if !check_hw_signature_with_domain_tag(
                DOMAIN_TAG_AUTH_ROTATION,
                &account_id.to_string(),
                &request.hardware.signature,
                request.hardware.key,
            ) {
                return Err(ApiError::GenericBadRequest(
                    "Invalid hardware signature".to_string(),
                ));
            }

            // Ensure we aren't going from having a recovery authkey to having none
            let existing_recovery_key = current_auth.recovery_pubkey.is_some();
            let rotate_to_new_recovery_key = request.recovery.is_some();
            if existing_recovery_key && !rotate_to_new_recovery_key {
                return Err(ApiError::GenericBadRequest(
                    "Recovery Authentication key required".to_string(),
                ));
            }

            // Check signature for recovery authkey if it exists
            if let Some(recovery_auth) = request.recovery.as_ref() {
                check_signature(
                    &account_id.to_string(),
                    &recovery_auth.signature,
                    recovery_auth.key,
                )?;
            }

            // Only check uniqueness for keys that are actually being rotated
            ensure_pubkeys_unique(
                &account_service,
                &recovery_service,
                &public_key_repository,
                &account_id,
                Some(request.application.key)
                    .filter(|&key| key != current_auth.app_pubkey),
                Some(request.hardware.key)
                    .filter(|&key| key != current_auth.hardware_pubkey),
                request.recovery.as_ref().map(|r| r.key)
                    .filter(|&key| Some(key) != current_auth.recovery_pubkey),
            )
            .await?;

            // Cancel D+N if exists
            let events = vec![
                RecoveryEvent::CheckAccountRecoveryState,
                RecoveryEvent::CancelRecovery { authorized_request },
            ];
            if let Err(e) = run_recovery_fsm(
                account_id.clone(),
                events,
                &account_service,
                &inheritance_service,
                &recovery_service,
                &notification_service,
                &social_challenge_service,
                &comms_verification_service,
                &feature_flags_service,
                &public_key_repository,
            )
            .await
            {
                if !matches!(e.clone(), ApiError::Specific{code, ..} if code == ErrorCode::NoRecoveryExists)
                {
                    return Err(e);
                }
            }

            // Record hw auth pubkey in public_keys table if key is rotating
            if request.hardware.key != current_auth.hardware_pubkey {
                if !public_key_repository
                    .persist_public_key(
                        &request.hardware.key.to_string(),
                        &account_id,
                        repository::public_key::KeyType::HardwareAuth,
                    )
                    .await
                    .map_err(RecoveryError::from)?
                {
                    return Err(RecoveryError::HwAuthPubkeyReuseAccount.into());
                }
            }

            let updated_account = account_service
                .create_and_rotate_auth_keys(CreateAndRotateAuthKeysInput {
                    account_id: &account_id,
                    app_auth_pubkey: request.application.key,
                    hardware_auth_pubkey: request.hardware.key,
                    recovery_auth_pubkey: request.recovery.as_ref().map(|r| r.key),
                    hardware_type: request.hardware_type,
                })
                .await?;

            user_pool_service
                .create_or_update_account_users_if_necessary(
                    &account_id,
                    Some(request.application.key),
                    Some(request.hardware.key),
                    request.recovery.as_ref().map(|f| f.key),
                )
                .await
                .map_err(RecoveryError::RotateAuthKeys)?;

            account_service
                .clear_push_touchpoints(ClearPushTouchpointsInput {
                    account_id: &account_id,
                })
                .await?;

            // Recreate pending claims for beneficiary if account is full
            if let Account::Full(updated_full_account) = updated_account {
                let is_inheritance_enabled = experimentation_claims
                    .account_context_key()
                    .ok()
                    .and_then(|context_key| {
                        evaluate_flag_value(
                            &feature_flags_service,
                            INHERITANCE_ENABLED_FLAG_KEY,
                            &context_key,
                        )
                        .ok()
                    })
                    .unwrap_or(false);

                if is_inheritance_enabled {
                    inheritance_service
                        .recreate_pending_claims_for_beneficiary(
                            RecreatePendingClaimsForBeneficiaryInput {
                                beneficiary: &updated_full_account,
                            },
                        )
                        .await?;
                }
            }

            metrics::AUTH_KEYS_ROTATED.add(1, &[]);
            Ok::<_, ApiError>(RotateAuthenticationKeysResponse {})
        })
        .await?;

    Ok(Json(result))
}

#[serde_as]
#[derive(Serialize, Deserialize, Debug, ToSchema)]
pub struct EvaluatePinRequest {
    #[serde_as(as = "Base64")]
    pub sealed_request: Vec<u8>,
    #[serde_as(as = "Base64")]
    pub noise_session: Vec<u8>,
}

#[serde_as]
#[derive(Serialize, Deserialize, Debug, ToSchema)]
pub struct EvaluatePinResponse {
    #[serde_as(as = "Base64")]
    pub sealed_response: Vec<u8>,
}

#[instrument(err, skip(wsm_client, request))]
pub async fn evaluate_pin(
    State(wsm_client): State<WsmClient>,
    Json(request): Json<EvaluatePinRequest>,
) -> Result<Json<EvaluatePinResponse>, ApiError> {
    let wsm_response = wsm_client
        .evaluate_pin(request.sealed_request, request.noise_session)
        .await
        .map_err(|e| {
            event!(Level::ERROR, "Failed to evaluate OPRF PIN: {:?}", e);
            ApiError::GenericInternalApplicationError("Failed to evaluate OPRF PIN".to_string())
        })?;

    Ok(Json(EvaluatePinResponse {
        sealed_response: wsm_response.sealed_response,
    }))
}
