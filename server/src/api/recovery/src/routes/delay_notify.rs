use std::collections::HashSet;

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
use authn_authz::key_claims::KeyClaims;
use axum::{
    extract::{Path, State},
    routing::{delete, get, post, put},
    Json, Router,
};
use bdk_utils::{bdk::bitcoin::secp256k1::PublicKey, signature::check_signature};
use comms_verification::{
    error::CommsVerificationError, InitiateVerificationForScopeInput,
    Service as CommsVerificationService, VerifyForScopeInput,
};
use errors::{ApiError, ErrorCode};
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
use time::Duration;
use tracing::{event, instrument, Level};
use types::account::{
    entities::{Account, CommsVerificationScope, Factor, FullAccountAuthKeysPayload, Touchpoint},
    identifiers::{AccountId, TouchpointId},
};
use userpool::userpool::UserPoolService;
use utoipa::{OpenApi, ToSchema};

use crate::{
    ensure_pubkeys_unique,
    entities::{RecoveryDestination, ToActor, ToActorStrategy},
    error::RecoveryError,
    metrics,
    repository::RecoveryRepository,
    service::social::challenge::Service as SocialChallengeService,
    state_machine::{run_recovery_fsm, RecoveryEvent},
};

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
            FullAccountAuthKeysPayload,
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

#[derive(Debug, Serialize, Deserialize, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct CreateAccountDelayNotifyRequest {
    pub lost_factor: Factor, // TODO: [W-774] Update visibility of struct after migration
    pub delay_period_num_sec: Option<i64>, // TODO: [W-774] Update visibility of struct after migration
    pub auth: FullAccountAuthKeysPayload, // TODO: [W-774] Update visibility of struct after migration, [W-973] Remove this before beta
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
    key_proof: KeyClaims,
    Json(request): Json<CreateAccountDelayNotifyRequest>,
) -> Result<Json<Value>, ApiError> {
    let full_account = account_service
        .fetch_full_account(FetchAccountInput {
            account_id: &account_id,
        })
        .await?;

    let destination = RecoveryDestination {
        source_auth_keys_id: full_account.common_fields.active_auth_keys_id.to_owned(),
        app_auth_pubkey: request.auth.app,
        hardware_auth_pubkey: request.auth.hardware,
        recovery_auth_pubkey: request.auth.recovery,
    };
    let events = vec![
        RecoveryEvent::CheckAccountRecoveryState,
        RecoveryEvent::CreateRecovery {
            account: full_account.clone(),
            lost_factor: request.lost_factor,
            destination,
            key_proof,
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
    )
    .await
    .map(|r| Json(r.response()))?;

    //TODO: Remove this once recovery syncer on mobile isn't running always
    if request.delay_period_num_sec.is_some()
        && full_account.common_fields.properties.is_test_account
    {
        update_recovery_delay_for_test_account(
            account_id,
            account_service,
            inheritance_service,
            notification_service,
            recovery_service,
            social_challenge_service,
            comms_verification_service,
            feature_flags_service,
            UpdateDelayForTestRecoveryRequest {
                delay_period_num_sec: request.delay_period_num_sec,
            },
        )
        .await
    } else {
        Ok(create_response)
    }
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
    key_proof: KeyClaims,
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
    key_proof: KeyClaims,
) -> Result<(), ApiError> {
    let events = vec![
        RecoveryEvent::CheckAccountRecoveryState,
        RecoveryEvent::CancelRecovery { key_proof },
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
    )
    .await?;

    Ok(())
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
    key_proof: KeyClaims,
    Json(request): Json<SendAccountVerificationCodeRequest>,
) -> Result<Json<SendAccountVerificationCodeResponse>, ApiError> {
    let account = account_service
        .fetch_account(account::service::FetchAccountInput {
            account_id: &account_id,
        })
        .await?;

    let actor = key_proof.to_actor(ToActorStrategy::ExclusiveOr)?;
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

#[instrument(err, skip(comms_verification_service))]
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
    key_proof: KeyClaims,
    Json(request): Json<VerifyAccountVerificationCodeRequest>,
) -> Result<Json<VerifyAccountVerificationCodeResponse>, ApiError> {
    let actor = key_proof.to_actor(ToActorStrategy::ExclusiveOr)?;
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
    key_proof: KeyClaims,
    experimentation_claims: ExperimentationClaims,
    Json(request): Json<RotateAuthenticationKeysRequest>,
) -> Result<Json<RotateAuthenticationKeysResponse>, ApiError> {
    if !(key_proof.hw_signed && key_proof.app_signed) {
        event!(
            Level::WARN,
            "valid signature over access token required by both app and hw auth keys"
        );
        return Err(ApiError::GenericForbidden(
            "valid signature over access token required by both app and hw auth keys".to_string(),
        ));
    }

    let account = account_service
        .fetch_full_account(FetchAccountInput {
            account_id: &account_id,
        })
        .await?;
    let current_auth = account
        .active_auth_keys()
        .ok_or(AccountError::InvalidKeysetState)?;

    check_signature(
        &account_id.to_string(),
        &request.application.signature,
        request.application.key,
    )?;
    check_signature(
        &account_id.to_string(),
        &request.hardware.signature,
        request.hardware.key,
    )?;

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

    ensure_pubkeys_unique(
        &account_service,
        &recovery_service,
        Some(request.application.key),
        None,
        request.recovery.as_ref().map(|r| r.key),
    )
    .await?;

    //TODO: Remove this when the endpoint should allow hw key rotations
    if request.hardware.key != current_auth.hardware_pubkey {
        return Err(ApiError::GenericBadRequest(
            "Hardware Authentication key mismatch".to_string(),
        ));
    }

    // Cancel D+N if exists
    let events = vec![
        RecoveryEvent::CheckAccountRecoveryState,
        RecoveryEvent::CancelRecovery { key_proof },
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
    )
    .await
    {
        if !matches!(e.clone(), ApiError::Specific{code, ..} if code == ErrorCode::NoRecoveryExists)
        {
            return Err(e);
        }
    }

    let updated_account = account_service
        .create_and_rotate_auth_keys(CreateAndRotateAuthKeysInput {
            account_id: &account_id,
            app_auth_pubkey: request.application.key,
            hardware_auth_pubkey: request.hardware.key,
            recovery_auth_pubkey: request.recovery.as_ref().map(|r| r.key),
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
                .recreate_pending_claims_for_beneficiary(RecreatePendingClaimsForBeneficiaryInput {
                    beneficiary: &updated_full_account,
                })
                .await?;
        }
    }

    metrics::AUTH_KEYS_ROTATED.add(1, &[]);
    Ok(Json(RotateAuthenticationKeysResponse {}))
}
