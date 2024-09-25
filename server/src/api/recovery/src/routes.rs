use std::collections::{HashMap, HashSet};

use account::entities::Account;
use axum::Extension;
use instrumentation::metrics::KeyValue;

use axum::extract::Query;
use axum::routing::{delete, put};
use axum::{
    extract::{Path, State},
    routing::{get, post},
    Json, Router,
};
use serde::{Deserialize, Deserializer, Serialize};
use serde_json::Value;
use time::serde::rfc3339;
use time::{Duration, OffsetDateTime};
use tracing::{event, instrument, Level};
use types::authn_authz::cognito::CognitoUser;
use types::recovery::inheritance::claim::{
    InheritanceClaimAuthKeys, InheritanceClaimCanceledBy, InheritanceClaimId,
};
use types::recovery::inheritance::router::{
    BenefactorInheritanceClaimView, BeneficiaryInheritanceClaimView,
};
use types::recovery::social::challenge::{
    SocialChallenge, SocialChallengeId, SocialChallengeResponse, TrustedContactChallengeRequest,
};
use types::recovery::social::relationship::{
    RecoveryRelationship, RecoveryRelationshipCommonFields, RecoveryRelationshipId,
};
use types::recovery::social::PAKE_PUBLIC_KEY_STRING_LENGTH;
use userpool::userpool::UserPoolService;
use utoipa::{OpenApi, ToSchema};

use account::{
    entities::{CommsVerificationScope, Factor, FullAccountAuthKeysPayload, Touchpoint},
    error::AccountError,
    service::{
        ClearPushTouchpointsInput, CreateAndRotateAuthKeysInput, FetchAccountInput,
        Service as AccountService,
    },
};
use authn_authz::key_claims::KeyClaims;
use bdk_utils::{bdk::bitcoin::secp256k1::PublicKey, signature::check_signature};
use comms_verification::{
    error::CommsVerificationError, InitiateVerificationForScopeInput,
    Service as CommsVerificationService, VerifyForScopeInput,
};
use errors::{ApiError, ErrorCode};
use feature_flags::service::Service as FeatureFlagsService;
use http_server::swagger::{SwaggerEndpoint, Url};
use notification::{entities::NotificationTouchpoint, service::Service as NotificationService};
use types::account::identifiers::{AccountId, TouchpointId};
use wsm_rust_client::WsmClient;

use crate::ensure_pubkeys_unique;
use crate::service::inheritance::cancel_inheritance_claim::CancelInheritanceClaimInput;
use crate::service::inheritance::create_inheritance_claim::CreateInheritanceClaimInput;
use crate::service::inheritance::get_inheritance_claims::GetInheritanceClaimsInput;
use crate::service::inheritance::Service as InheritanceService;
use crate::service::social::challenge::create_social_challenge::CreateSocialChallengeInput;
use crate::service::social::challenge::fetch_social_challenge::{
    FetchSocialChallengeAsCustomerInput, FetchSocialChallengeAsTrustedContactInput,
};
use crate::service::social::challenge::respond_to_social_challenge::RespondToSocialChallengeInput;
use crate::service::social::relationship::accept_recovery_relationship_invitation::AcceptRecoveryRelationshipInvitationInput;
use crate::service::social::relationship::create_recovery_relationship_invitation::CreateRecoveryRelationshipInvitationInput;
use crate::service::social::relationship::delete_recovery_relationship::DeleteRecoveryRelationshipInput;
use crate::service::social::relationship::endorse_recovery_relationships::EndorseRecoveryRelationshipsInput;
use crate::service::social::relationship::error::ServiceError;
use crate::service::social::relationship::get_recovery_relationship_invitation_for_code::GetRecoveryRelationshipInvitationForCodeInput;
use crate::service::social::relationship::get_recovery_relationships::GetRecoveryRelationshipsInput;
use crate::service::social::relationship::reissue_recovery_relationship_invitation::ReissueRecoveryRelationshipInvitationInput;
use crate::{
    entities::{
        DelayNotifyRecoveryAction, DelayNotifyRequirements, RecoveryAction, RecoveryDestination,
        RecoveryRequirements, RecoveryType, ToActor, ToActorStrategy, WalletRecovery,
    },
    error::RecoveryError,
    metrics,
    repository::RecoveryRepository,
    service::social::challenge::Service as SocialChallengeService,
    service::social::relationship::Service as RecoveryRelationshipService,
    state_machine::{
        cancel_recovery::CanceledRecoveryState, pending_recovery::PendingRecoveryResponse,
        run_recovery_fsm, PendingDelayNotifyRecovery, RecoveryEvent, RecoveryResponse,
    },
};
use types::recovery::inheritance::package::Package;
use types::recovery::social::relationship::RecoveryRelationshipEndorsement;
use types::recovery::trusted_contacts::TrustedContactRole::SocialRecoveryContact;
use types::recovery::trusted_contacts::{TrustedContactInfo, TrustedContactRole};

#[derive(Clone, axum_macros::FromRef)]
pub struct RouteState(
    pub AccountService,
    pub NotificationService,
    pub CommsVerificationService,
    pub UserPoolService,
    pub RecoveryRepository,
    pub WsmClient,
    pub RecoveryRelationshipService,
    pub SocialChallengeService,
    pub FeatureFlagsService,
    pub InheritanceService,
);

impl RouteState {
    pub fn unauthed_router(&self) -> Router {
        Router::new()
            .route(
                "/api/accounts/:account_id/delay-notify/complete",
                post(complete_delay_notify_transaction),
            )
            .route_layer(metrics::FACTORY.route_layer("recovery".to_owned()))
            .with_state(self.to_owned())
    }

    pub fn authed_router(&self) -> Router {
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
            .route(
                "/api/accounts/:account_id/recovery/relationships",
                post(create_recovery_relationship),
            )
            .route(
                "/api/accounts/:account_id/relationships",
                post(create_relationship),
            )
            .route(
                "/api/accounts/:account_id/recovery/relationships",
                put(endorse_recovery_relationships),
            )
            .route(
                "/api/accounts/:account_id/recovery/inheritance/claims",
                post(create_inheritance_claim),
            )
            .route(
                "/api/accounts/:account_id/recovery/inheritance/packages",
                post(upload_inheritance_packages),
            )
            .route(
                "/api/accounts/:account_id/recovery/inheritance/claims/:inheritance_claim_id/cancel",
                post(cancel_inheritance_claim),
            )
            .route_layer(metrics::FACTORY.route_layer("recovery".to_owned()))
            .with_state(self.to_owned())
    }

    pub fn recovery_authed_router(&self) -> Router {
        Router::new()
            .route(
                "/api/accounts/:account_id/recovery/social-challenges",
                post(start_social_challenge),
            )
            .route(
                "/api/accounts/:account_id/recovery/verify-social-challenge",
                post(verify_social_challenge),
            )
            .route(
                "/api/accounts/:account_id/recovery/social-challenges/:social_challenge_id",
                put(respond_to_social_challenge),
            )
            .route(
                "/api/accounts/:account_id/recovery/social-challenges/:social_challenge_id",
                get(fetch_social_challenge),
            )
            .route(
                "/api/accounts/:account_id/recovery/inheritance/claims",
                get(get_inheritance_claims),
            )
            .route_layer(metrics::FACTORY.route_layer("recovery".to_owned()))
            .with_state(self.to_owned())
    }

    pub fn account_or_recovery_authed_router(&self) -> Router {
        Router::new()
            .route(
                "/api/accounts/:account_id/recovery/relationships/:recovery_relationship_id",
                delete(delete_recovery_relationship),
            )
            .route(
                "/api/accounts/:account_id/recovery/relationships",
                get(get_recovery_relationships),
            )
            .route(
                "/api/accounts/:account_id/relationships",
                get(get_relationships),
            )
            .route(
                "/api/accounts/:account_id/recovery/relationships/:recovery_relationship_id",
                put(update_recovery_relationship),
            )
            .route(
                "/api/accounts/:account_id/recovery/relationship-invitations/:code",
                get(get_recovery_relationship_invitation_for_code),
            )
            .route_layer(metrics::FACTORY.route_layer("recovery".to_owned()))
            .with_state(self.to_owned())
    }
}

impl From<RouteState> for SwaggerEndpoint {
    fn from(_: RouteState) -> Self {
        (
            Url::new("Recovery", "/docs/recovery/openapi.json"),
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
        create_recovery_relationship,
        create_relationship,
        delete_recovery_relationship,
        endorse_recovery_relationships,
        fetch_social_challenge,
        get_recovery_relationship_invitation_for_code,
        get_recovery_relationships,
        get_recovery_status,
        respond_to_social_challenge,
        rotate_authentication_keys,
        send_verification_code,
        create_inheritance_claim,
        start_social_challenge,
        update_recovery_relationship,
        upload_inheritance_packages,
        verify_code,
        verify_social_challenge,
        get_inheritance_claims,
    ),
    components(
        schemas(
            AuthenticationKey,
            CanceledRecoveryState,
            CompleteDelayNotifyRequest,
            CompleteDelayNotifyResponse,
            CreateAccountDelayNotifyRequest,
            CreateRecoveryRelationshipRequest,
            CreateRelationshipRequest,
            CreateRelationshipResponse,
            CustomerRecoveryRelationshipView,
            CustomerSocialChallenge,
            CustomerSocialChallengeResponse,
            DelayNotifyRecoveryAction,
            DelayNotifyRequirements,
            EndorseRecoveryRelationshipsRequest,
            EndorseRecoveryRelationshipsResponse,
            EndorsedTrustedContact,
            Factor,
            FetchSocialChallengeResponse,
            FullAccountAuthKeysPayload,
            GetRecoveryRelationshipInvitationForCodeResponse,
            GetRecoveryRelationshipsResponse,
            InboundInvitation,
            OutboundInvitation,
            PendingDelayNotifyRecovery,
            PendingRecoveryResponse,
            RecoveryAction,
            RecoveryRequirements,
            RecoveryResponse,
            RecoveryRelationshipEndorsement,
            RecoveryType,
            RespondToSocialChallengeRequest,
            RespondToSocialChallengeResponse,
            RotateAuthenticationKeysRequest,
            RotateAuthenticationKeysResponse,
            SendAccountVerificationCodeRequest,
            SendAccountVerificationCodeResponse,
            StartChallengeTrustedContactRequest,
            StartSocialChallengeRequest,
            StartSocialChallengeResponse,
            CreateInheritanceClaimRequest,
            CreateInheritanceClaimResponse,
            TrustedContactRecoveryRelationshipView,
            TrustedContactSocialChallenge,
            UnendorsedTrustedContact,
            UpdateRecoveryRelationshipRequest,
            UpdateRecoveryRelationshipResponse,
            UploadInheritancePackagesRequest,
            UploadInheritancePackagesResponse,
            VerifyAccountVerificationCodeRequest,
            VerifyAccountVerificationCodeResponse,
            VerifySocialChallengeRequest,
            VerifySocialChallengeResponse,
            WalletRecovery,
            GetInheritanceClaimsResponse,
        )
    ),
    tags(
        (name = "Recovery", description = "Recovery for Account and Keysets")
    )
)]
struct ApiDoc;

fn deserialize_pake_pubkey<'de, D>(deserializer: D) -> Result<String, D::Error>
where
    D: Deserializer<'de>,
{
    let s: String = match Deserialize::deserialize(deserializer) {
        Ok(s) => s,
        Err(_) => {
            return Ok(String::default());
        }
    };
    hex::decode(&s).map_err(|_| serde::de::Error::custom("Invalid PAKE public key format"))?;
    if s.len() != PAKE_PUBLIC_KEY_STRING_LENGTH {
        return Err(serde::de::Error::custom("Invalid PAKE public key length"));
    }
    Ok(s)
}

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
        notification_service,
        recovery_service,
        social_challenge_service,
        comms_verification_service
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
    State(notification_service): State<NotificationService>,
    State(recovery_service): State<RecoveryRepository>,
    State(social_challenge_service): State<SocialChallengeService>,
    State(comms_verification_service): State<CommsVerificationService>,
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
        &recovery_service,
        &notification_service,
        &social_challenge_service,
        &comms_verification_service,
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
            notification_service,
            recovery_service,
            social_challenge_service,
            comms_verification_service,
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
    notification_service: NotificationService,
    recovery_service: RecoveryRepository,
    social_challenge_service: SocialChallengeService,
    comms_verification_service: CommsVerificationService,
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
        &recovery_service,
        &notification_service,
        &social_challenge_service,
        &comms_verification_service,
    )
    .await
    .map(|r| Json(r.response()))
}

#[instrument(
    err,
    skip(
        account_service,
        notification_service,
        recovery_service,
        social_challenge_service,
        comms_verification_service
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
    State(notification_service): State<NotificationService>,
    State(recovery_service): State<RecoveryRepository>,
    State(social_challenge_service): State<SocialChallengeService>,
    State(comms_verification_service): State<CommsVerificationService>,
    key_proof: KeyClaims,
    Json(request): Json<UpdateDelayForTestRecoveryRequest>,
) -> Result<Json<Value>, ApiError> {
    update_recovery_delay_for_test_account(
        account_id,
        account_service,
        notification_service,
        recovery_service,
        social_challenge_service,
        comms_verification_service,
        request,
    )
    .await
}

#[instrument(
    err,
    skip(
        account_service,
        notification_service,
        recovery_service,
        social_challenge_service,
        comms_verification_service
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
    State(notification_service): State<NotificationService>,
    State(recovery_service): State<RecoveryRepository>,
    State(social_challenge_service): State<SocialChallengeService>,
    State(comms_verification_service): State<CommsVerificationService>,
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
        &recovery_service,
        &notification_service,
        &social_challenge_service,
        &comms_verification_service,
    )
    .await?;

    Ok(())
}

#[instrument(
    err,
    skip(
        account_service,
        notification_service,
        recovery_service,
        social_challenge_service,
        comms_verification_service
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
    State(notification_service): State<NotificationService>,
    State(recovery_service): State<RecoveryRepository>,
    State(social_challenge_service): State<SocialChallengeService>,
    State(comms_verification_service): State<CommsVerificationService>,
) -> Result<Json<Value>, ApiError> {
    let events = vec![RecoveryEvent::CheckAccountRecoveryState];
    run_recovery_fsm(
        account_id,
        events,
        &account_service,
        &recovery_service,
        &notification_service,
        &social_challenge_service,
        &comms_verification_service,
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

#[derive(Serialize, ToSchema)]
pub struct CompleteDelayNotifyResponse {}

#[instrument(
    err,
    skip(
        account_service,
        recovery_service,
        notification_service,
        social_challenge_service,
        user_pool_service,
        comms_verification_service,
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
    State(recovery_service): State<RecoveryRepository>,
    State(social_challenge_service): State<SocialChallengeService>,
    State(comms_verification_service): State<CommsVerificationService>,
    State(user_pool_service): State<UserPoolService>,
    Json(request): Json<CompleteDelayNotifyRequest>,
) -> Result<Json<Value>, ApiError> {
    let events = vec![
        RecoveryEvent::CheckAccountRecoveryState,
        RecoveryEvent::CheckEligibleForCompletion {
            challenge: request.challenge,
            app_signature: request.app_signature,
            hardware_signature: request.hardware_signature,
        },
        RecoveryEvent::RotateKeyset { user_pool_service },
    ];
    run_recovery_fsm(
        account_id,
        events,
        &account_service,
        &recovery_service,
        &notification_service,
        &social_challenge_service,
        &comms_verification_service,
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
        recovery_service,
        social_challenge_service,
        comms_verification_service,
        user_pool_service,
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
    State(recovery_service): State<RecoveryRepository>,
    State(social_challenge_service): State<SocialChallengeService>,
    State(comms_verification_service): State<CommsVerificationService>,
    State(user_pool_service): State<UserPoolService>,
    key_proof: KeyClaims,
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
        &recovery_service,
        &notification_service,
        &social_challenge_service,
        &comms_verification_service,
    )
    .await
    {
        if !matches!(e.clone(), ApiError::Specific{code, ..} if code == ErrorCode::NoRecoveryExists)
        {
            return Err(e);
        }
    }

    account_service
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
            request.recovery.map(|f| f.key),
        )
        .await
        .map_err(RecoveryError::RotateAuthKeys)?;

    account_service
        .clear_push_touchpoints(ClearPushTouchpointsInput {
            account_id: &account_id,
        })
        .await?;

    metrics::AUTH_KEYS_ROTATED.add(1, &[]);

    Ok(Json(RotateAuthenticationKeysResponse {}))
}

// Recovery Relationships

#[derive(Serialize, Deserialize, Debug, ToSchema, PartialEq)]
pub struct OutboundInvitation {
    #[serde(flatten)]
    pub recovery_relationship_info: TrustedContactRecoveryRelationshipView,
    pub code: String,
    pub code_bit_length: usize,
    #[serde(with = "rfc3339")]
    pub expires_at: OffsetDateTime,
}

impl TryFrom<RecoveryRelationship> for OutboundInvitation {
    type Error = RecoveryError;

    fn try_from(value: RecoveryRelationship) -> Result<Self, Self::Error> {
        match value {
            RecoveryRelationship::Invitation(invitation) => Ok(Self {
                recovery_relationship_info: invitation.common_fields.into(),
                code: invitation.code,
                code_bit_length: invitation.code_bit_length,
                expires_at: invitation.expires_at,
            }),
            _ => Err(RecoveryError::InvalidRecoveryRelationshipType),
        }
    }
}

#[derive(Serialize, Deserialize, Debug, ToSchema)]
pub struct InboundInvitation {
    pub recovery_relationship_id: RecoveryRelationshipId,
    #[serde(with = "rfc3339")]
    pub expires_at: OffsetDateTime,
    pub protected_customer_enrollment_pake_pubkey: String,
}

impl TryFrom<RecoveryRelationship> for InboundInvitation {
    type Error = RecoveryError;

    fn try_from(value: RecoveryRelationship) -> Result<Self, Self::Error> {
        match value {
            RecoveryRelationship::Invitation(invitation) => Ok(Self {
                recovery_relationship_id: invitation.common_fields.id,
                expires_at: invitation.expires_at,
                protected_customer_enrollment_pake_pubkey: invitation
                    .protected_customer_enrollment_pake_pubkey,
            }),
            _ => Err(RecoveryError::InvalidRecoveryRelationshipType),
        }
    }
}

#[derive(Serialize, Deserialize, Debug, ToSchema, PartialEq)]
pub struct TrustedContactRecoveryRelationshipView {
    pub recovery_relationship_id: RecoveryRelationshipId,
    pub trusted_contact_alias: String,
    pub trusted_contact_roles: Vec<TrustedContactRole>,
}

impl From<RecoveryRelationshipCommonFields> for TrustedContactRecoveryRelationshipView {
    fn from(value: RecoveryRelationshipCommonFields) -> Self {
        Self {
            recovery_relationship_id: value.id,
            trusted_contact_alias: value.trusted_contact_info.alias,
            trusted_contact_roles: value.trusted_contact_info.roles,
        }
    }
}

#[derive(Serialize, Deserialize, Debug, ToSchema, PartialEq)]
pub struct UnendorsedTrustedContact {
    #[serde(flatten)]
    pub recovery_relationship_info: TrustedContactRecoveryRelationshipView,
    pub sealed_delegated_decryption_pubkey: String,
    pub trusted_contact_enrollment_pake_pubkey: String,
    pub enrollment_pake_confirmation: String,
}

impl TryFrom<RecoveryRelationship> for UnendorsedTrustedContact {
    type Error = RecoveryError;

    fn try_from(value: RecoveryRelationship) -> Result<Self, Self::Error> {
        match value {
            RecoveryRelationship::Unendorsed(connection) => Ok(Self {
                recovery_relationship_info: connection.common_fields.into(),
                sealed_delegated_decryption_pubkey: connection.sealed_delegated_decryption_pubkey,
                trusted_contact_enrollment_pake_pubkey: connection
                    .trusted_contact_enrollment_pake_pubkey,
                enrollment_pake_confirmation: connection.enrollment_pake_confirmation,
            }),
            _ => Err(RecoveryError::InvalidRecoveryRelationshipType),
        }
    }
}

#[derive(Serialize, Deserialize, Debug, ToSchema, PartialEq)]
pub struct EndorsedTrustedContact {
    #[serde(flatten)]
    pub recovery_relationship_info: TrustedContactRecoveryRelationshipView,
    pub delegated_decryption_pubkey_certificate: String,
}

impl TryFrom<RecoveryRelationship> for EndorsedTrustedContact {
    type Error = RecoveryError;

    fn try_from(value: RecoveryRelationship) -> Result<Self, Self::Error> {
        match value {
            RecoveryRelationship::Endorsed(connection) => Ok(Self {
                recovery_relationship_info: connection.common_fields.into(),
                delegated_decryption_pubkey_certificate: connection
                    .delegated_decryption_pubkey_certificate,
            }),
            _ => Err(RecoveryError::InvalidRecoveryRelationshipType),
        }
    }
}

#[derive(Serialize, Deserialize, Debug, ToSchema, PartialEq)]
pub struct CustomerRecoveryRelationshipView {
    pub recovery_relationship_id: RecoveryRelationshipId,
    pub customer_alias: String,
    pub trusted_contact_roles: Vec<TrustedContactRole>,
}

impl TryFrom<RecoveryRelationship> for CustomerRecoveryRelationshipView {
    type Error = RecoveryError;

    fn try_from(value: RecoveryRelationship) -> Result<Self, Self::Error> {
        match value {
            RecoveryRelationship::Endorsed(connection) => Ok(Self {
                recovery_relationship_id: connection.common_fields.id,
                customer_alias: connection.connection_fields.customer_alias,
                trusted_contact_roles: connection.common_fields.trusted_contact_info.roles,
            }),
            RecoveryRelationship::Unendorsed(connection) => Ok(Self {
                recovery_relationship_id: connection.common_fields.id,
                customer_alias: connection.connection_fields.customer_alias,
                trusted_contact_roles: connection.common_fields.trusted_contact_info.roles,
            }),
            _ => Err(RecoveryError::InvalidRecoveryRelationshipType),
        }
    }
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
#[serde(rename_all = "snake_case")]
#[deprecated = "use CreateRelationshipRequest instead"]
pub struct CreateRecoveryRelationshipRequest {
    pub trusted_contact_alias: String,
    #[serde(deserialize_with = "deserialize_pake_pubkey")]
    pub protected_customer_enrollment_pake_pubkey: String,
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct CreateRelationshipRequest {
    pub trusted_contact_alias: String,
    pub trusted_contact_roles: Vec<TrustedContactRole>,
    #[serde(deserialize_with = "deserialize_pake_pubkey")]
    pub protected_customer_enrollment_pake_pubkey: String,
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct CreateRelationshipResponse {
    pub invitation: OutboundInvitation,
}

///
/// Used by FullAccounts to create a recovery relationship which is sent to
/// a Trusted Contact (either a FullAccount or a LiteAccount). The trusted contact
/// can then accept the relationship and become a trusted contact for the account.
///
#[instrument(
    err,
    skip(account_service, recovery_relationship_service, _feature_flags_service)
)]
#[utoipa::path(
    post,
    path = "/api/accounts/{account_id}/relationships",
    params(
        ("account_id" = AccountId, Path, description = "AccountId"),
    ),
    request_body = CreateRecoveryRelationshipRequest,
    responses(
    (status = 200, description = "Account creates a recovery relationship", body=CreateRecoveryRelationshipResponse),
    ),
)]
pub async fn create_relationship(
    Path(account_id): Path<AccountId>,
    State(account_service): State<AccountService>,
    State(recovery_relationship_service): State<RecoveryRelationshipService>,
    State(_feature_flags_service): State<FeatureFlagsService>,
    key_proof: KeyClaims,
    Json(request): Json<CreateRelationshipRequest>,
) -> Result<Json<CreateRelationshipResponse>, ApiError> {
    let trusted_contact =
        TrustedContactInfo::new(request.trusted_contact_alias, request.trusted_contact_roles)
            .map_err(ServiceError::from)?;

    let response = create_relationship_common(
        &account_id,
        &account_service,
        &recovery_relationship_service,
        &key_proof,
        trusted_contact,
        &request.protected_customer_enrollment_pake_pubkey,
    )
    .await?;

    Ok(Json(response))
}

#[instrument(
    err,
    skip(account_service, recovery_relationship_service, _feature_flags_service)
)]
#[utoipa::path(
    post,
    path = "/api/accounts/{account_id}/recovery/relationships",
    params(
        ("account_id" = AccountId, Path, description = "AccountId"),
    ),
    request_body = CreateRecoveryRelationshipRequestForSocrec,
    responses(
        (status = 200, description = "Account creates a recovery relationship", body=CreateRecoveryRelationshipResponse),
    ),
)]
#[deprecated = "use create_relationship instead"]
pub async fn create_recovery_relationship(
    Path(account_id): Path<AccountId>,
    State(account_service): State<AccountService>,
    State(recovery_relationship_service): State<RecoveryRelationshipService>,
    State(_feature_flags_service): State<FeatureFlagsService>,
    key_proof: KeyClaims,
    Json(request): Json<CreateRecoveryRelationshipRequest>,
) -> Result<Json<CreateRelationshipResponse>, ApiError> {
    let trusted_contact =
        TrustedContactInfo::new(request.trusted_contact_alias, vec![SocialRecoveryContact])
            .map_err(ServiceError::from)?;

    let response = create_relationship_common(
        &account_id,
        &account_service,
        &recovery_relationship_service,
        &key_proof,
        trusted_contact,
        &request.protected_customer_enrollment_pake_pubkey,
    )
    .await?;

    Ok(Json(response))
}

async fn create_relationship_common(
    account_id: &AccountId,
    account_service: &AccountService,
    recovery_relationship_service: &RecoveryRelationshipService,
    key_proof: &KeyClaims,
    trusted_contact: TrustedContactInfo,
    protected_customer_enrollment_pake_pubkey: &str,
) -> Result<CreateRelationshipResponse, ApiError> {
    if !(key_proof.hw_signed && key_proof.app_signed) {
        event!(
            Level::WARN,
            "valid signature over access token required by both app and hw auth keys"
        );
        return Err(ApiError::GenericForbidden(
            "valid signature over access token required by both app and hw auth keys".to_string(),
        ));
    }

    let full_account = account_service
        .fetch_full_account(FetchAccountInput { account_id })
        .await?;

    let result = recovery_relationship_service
        .create_recovery_relationship_invitation(CreateRecoveryRelationshipInvitationInput {
            customer_account: &full_account,
            trusted_contact: &trusted_contact,
            protected_customer_enrollment_pake_pubkey,
        })
        .await?;
    Ok(CreateRelationshipResponse {
        invitation: result.try_into()?,
    })
}

///
/// This route is used by either the Customer or the Trusted Contact to delete a pending
/// or an established recovery relationship.
///
/// For Customers, they will need to provide:
/// - Account access token
/// - Both App and Hardware keyproofs
///
/// For Trusted Contacts, they will need to provide:
/// - Recovery access token
///
#[instrument(err, skip(recovery_relationship_service, _feature_flags_service))]
#[utoipa::path(
    delete,
    path = "/api/accounts/{account_id}/recovery/relationships/{recovery_relationship_id}",
    params(
        ("account_id" = AccountId, Path, description = "AccountId"),
        ("recovery_relationship_id" = AccountId, Path, description = "RecoveryRelationshipId"),
    ),
    responses(
        (status = 200, description = "Recovery relationship deleted"),
    ),
)]
pub async fn delete_recovery_relationship(
    Path((account_id, recovery_relationship_id)): Path<(AccountId, RecoveryRelationshipId)>,
    State(recovery_relationship_service): State<RecoveryRelationshipService>,
    State(_feature_flags_service): State<FeatureFlagsService>,
    key_proof: KeyClaims,
    Extension(cognito_user): Extension<CognitoUser>,
) -> Result<(), ApiError> {
    recovery_relationship_service
        .delete_recovery_relationship(DeleteRecoveryRelationshipInput {
            acting_account_id: &account_id,
            recovery_relationship_id: &recovery_relationship_id,
            key_proof: &key_proof,
            cognito_user: &cognito_user,
        })
        .await?;

    Ok(())
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct GetRecoveryRelationshipsResponse {
    pub invitations: Vec<OutboundInvitation>,
    pub unendorsed_trusted_contacts: Vec<UnendorsedTrustedContact>,
    pub endorsed_trusted_contacts: Vec<EndorsedTrustedContact>,
    pub customers: Vec<CustomerRecoveryRelationshipView>,
}

///
/// This route is used by both Customers and Trusted Contacts to retrieve
/// recovery relationships.
///
/// Only returns relationships having a trusted_contact_role of SocialRecoveryContact
///
/// For Customers, we will show:
/// - All the Trusted Contacts that are protecting their account
/// - All the pending outbound invitations
/// - All the accounts that they are protecting as a Trusted Contact
///
/// For Trusted Contacts, we will show:
/// - All the accounts that they are protecting
///
#[instrument(err, skip(recovery_relationship_service, _feature_flags_service))]
#[utoipa::path(
    get,
    path = "/api/accounts/{account_id}/recovery/relationships",
    params(
        ("account_id" = AccountId, Path, description = "AccountId"),
    ),
    responses(
        (status = 200, description = "Recovery relationships", body=GetRecoveryRelationshipsResponse),
    ),
)]
#[deprecated = "use get_relationships instead"]
pub async fn get_recovery_relationships(
    Path(account_id): Path<AccountId>,
    State(recovery_relationship_service): State<RecoveryRelationshipService>,
    State(_feature_flags_service): State<FeatureFlagsService>,
) -> Result<Json<GetRecoveryRelationshipsResponse>, ApiError> {
    let result = recovery_relationship_service
        .get_recovery_relationships(GetRecoveryRelationshipsInput {
            account_id: &account_id,
            trusted_contact_role: Some(SocialRecoveryContact),
        })
        .await?;

    let unendorsed_trusted_contacts = result
        .unendorsed_trusted_contacts
        .into_iter()
        .map(|i| i.try_into())
        .collect::<Result<Vec<UnendorsedTrustedContact>, _>>()?;
    Ok(Json(GetRecoveryRelationshipsResponse {
        invitations: result
            .invitations
            .into_iter()
            .map(|i| i.try_into())
            .collect::<Result<Vec<_>, _>>()?,
        unendorsed_trusted_contacts,
        endorsed_trusted_contacts: result
            .endorsed_trusted_contacts
            .into_iter()
            .map(|i| i.try_into())
            .collect::<Result<Vec<_>, _>>()?,
        customers: result
            .customers
            .into_iter()
            .map(|i| i.try_into())
            .collect::<Result<Vec<_>, _>>()?,
    }))
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
pub struct GetRelationshipsRequest {
    trusted_contact_role: Option<TrustedContactRole>,
}

///
/// This route is used by both Customers and Trusted Contacts to retrieve relationships.
///
#[instrument(err, skip(recovery_relationship_service, _feature_flags_service))]
#[utoipa::path(
    get,
    path = "/api/accounts/{account_id}/relationships",
    params(
        ("account_id" = AccountId, Path, description = "AccountId"),
        ("trusted_contact_role" = TrustedContactRole, Query, description = "filter by trusted_contact_role"),
    ),
    responses(
        (status = 200, description = "Relationships", body=GetRecoveryRelationshipsResponse),
    ),
)]
pub async fn get_relationships(
    Path(account_id): Path<AccountId>,
    State(recovery_relationship_service): State<RecoveryRelationshipService>,
    State(_feature_flags_service): State<FeatureFlagsService>,
    request: Query<GetRelationshipsRequest>,
) -> Result<Json<GetRecoveryRelationshipsResponse>, ApiError> {
    let result = recovery_relationship_service
        .get_recovery_relationships(GetRecoveryRelationshipsInput {
            account_id: &account_id,
            trusted_contact_role: request.trusted_contact_role,
        })
        .await?;

    Ok(Json(GetRecoveryRelationshipsResponse {
        invitations: try_into_vec(result.invitations)?,
        unendorsed_trusted_contacts: try_into_vec(result.unendorsed_trusted_contacts)?,
        endorsed_trusted_contacts: try_into_vec(result.endorsed_trusted_contacts)?,
        customers: try_into_vec(result.customers)?,
    }))
}

fn try_into_vec<T, U>(items: Vec<T>) -> Result<Vec<U>, <T as TryInto<U>>::Error>
where
    T: TryInto<U>,
{
    items.into_iter().map(TryInto::try_into).collect()
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
#[serde(tag = "action")]
pub enum UpdateRecoveryRelationshipRequest {
    Accept {
        code: String,
        customer_alias: String,
        #[serde(deserialize_with = "deserialize_pake_pubkey")]
        trusted_contact_enrollment_pake_pubkey: String,
        enrollment_pake_confirmation: String,
        sealed_delegated_decryption_pubkey: String,
    },
    Reissue,
}

#[derive(Debug, Serialize, Deserialize, ToSchema, PartialEq)]
#[serde(untagged)]
pub enum UpdateRecoveryRelationshipResponse {
    Accept {
        customer: CustomerRecoveryRelationshipView,
    },
    Reissue {
        invitation: OutboundInvitation,
    },
}

///
/// This route is used by either Full Accounts or LiteAccounts to accept
/// an pending outbound invitation and to become a Trusted Contact.
///
#[instrument(
    err,
    skip(account_service, recovery_relationship_service, _feature_flags_service)
)]
#[utoipa::path(
    put,
    path = "/api/accounts/{account_id}/recovery/relationships/{recovery_relationship_id}",
    params(
        ("account_id" = AccountId, Path, description = "AccountId"),
        ("recovery_relationship_id" = AccountId, Path, description = "RecoveryRelationshipId"),
    ),
    request_body = UpdateRecoveryRelationshipRequest,
    responses(
        (status = 200, description = "Recovery relationship updated", body=UpdateRecoveryRelationshipResponse),
    ),
)]
pub async fn update_recovery_relationship(
    Path((account_id, recovery_relationship_id)): Path<(AccountId, RecoveryRelationshipId)>,
    State(account_service): State<AccountService>,
    State(recovery_relationship_service): State<RecoveryRelationshipService>,
    State(_feature_flags_service): State<FeatureFlagsService>,
    key_proof: KeyClaims,
    Extension(cognito_user): Extension<CognitoUser>,
    Json(request): Json<UpdateRecoveryRelationshipRequest>,
) -> Result<Json<UpdateRecoveryRelationshipResponse>, ApiError> {
    let account = account_service
        .fetch_account(FetchAccountInput {
            account_id: &account_id,
        })
        .await?;

    match request {
        UpdateRecoveryRelationshipRequest::Accept {
            code,
            customer_alias,
            trusted_contact_enrollment_pake_pubkey,
            enrollment_pake_confirmation,
            sealed_delegated_decryption_pubkey,
        } => {
            if CognitoUser::Recovery(account_id.clone()) != cognito_user {
                event!(
                    Level::ERROR,
                    "The provided access token is for the incorrect domain."
                );
                return Err(ApiError::GenericForbidden(
                    "The provided access token is for the incorrect domain.".to_string(),
                ));
            }
            let result = recovery_relationship_service
                .accept_recovery_relationship_invitation(
                    AcceptRecoveryRelationshipInvitationInput {
                        trusted_contact_account_id: &account_id,
                        recovery_relationship_id: &recovery_relationship_id,
                        code: &code,
                        customer_alias: &customer_alias,
                        trusted_contact_enrollment_pake_pubkey:
                            &trusted_contact_enrollment_pake_pubkey,
                        enrollment_pake_confirmation: &enrollment_pake_confirmation,
                        sealed_delegated_decryption_pubkey: &sealed_delegated_decryption_pubkey,
                    },
                )
                .await?;

            Ok(Json(UpdateRecoveryRelationshipResponse::Accept {
                customer: result.try_into()?,
            }))
        }
        UpdateRecoveryRelationshipRequest::Reissue => {
            let Account::Full(full_account) = account else {
                return Err(ApiError::GenericForbidden(
                    "Incorrect calling account type".to_string(),
                ));
            };

            if !cognito_user.is_app(&account_id) && !cognito_user.is_hardware(&account_id) {
                event!(
                    Level::ERROR,
                    "The provided access token is for the incorrect domain."
                );
                return Err(ApiError::GenericForbidden(
                    "The provided access token is for the incorrect domain.".to_string(),
                ));
            }

            if !key_proof.hw_signed || !key_proof.app_signed {
                event!(
                    Level::WARN,
                    "valid signature over access token requires both app and hw auth keys"
                );
                return Err(ApiError::GenericBadRequest(
                    "valid signature over access token requires both app and hw auth keys"
                        .to_string(),
                ));
            }

            let result = recovery_relationship_service
                .reissue_recovery_relationship_invitation(
                    ReissueRecoveryRelationshipInvitationInput {
                        customer_account: &full_account,
                        recovery_relationship_id: &recovery_relationship_id,
                    },
                )
                .await?;

            Ok(Json(UpdateRecoveryRelationshipResponse::Reissue {
                invitation: result.try_into()?,
            }))
        }
    }
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
pub struct EndorseRecoveryRelationshipsRequest {
    pub endorsements: Vec<RecoveryRelationshipEndorsement>,
}

#[derive(Debug, Serialize, Deserialize, ToSchema, PartialEq)]
pub struct EndorseRecoveryRelationshipsResponse {
    pub unendorsed_trusted_contacts: Vec<UnendorsedTrustedContact>,
    pub endorsed_trusted_contacts: Vec<EndorsedTrustedContact>,
}

///
/// This route is used by Full Accounts to endorse recovery relationships
/// that are accepted by the Trusted Contact
///
#[instrument(
    err,
    skip(account_service, recovery_relationship_service, _feature_flags_service)
)]
#[utoipa::path(
    put,
    path = "/api/accounts/{account_id}/recovery/relationships",
    params(
        ("account_id" = AccountId, Path, description = "AccountId"),
    ),
    request_body = EndorseRecoveryRelationshipsRequest,
    responses(
        (status = 200, description = "Recovery relationships endorsed", body=EndorseRecoveryRelationshipsResponse),
    ),
)]
pub async fn endorse_recovery_relationships(
    Path(account_id): Path<AccountId>,
    State(account_service): State<AccountService>,
    State(recovery_relationship_service): State<RecoveryRelationshipService>,
    State(_feature_flags_service): State<FeatureFlagsService>,
    key_proof: KeyClaims,
    Json(request): Json<EndorseRecoveryRelationshipsRequest>,
) -> Result<Json<EndorseRecoveryRelationshipsResponse>, ApiError> {
    let account = account_service
        .fetch_account(FetchAccountInput {
            account_id: &account_id,
        })
        .await?;

    let Account::Full(_) = account else {
        return Err(ApiError::GenericForbidden(
            "Incorrect calling account type".to_string(),
        ));
    };

    recovery_relationship_service
        .endorse_recovery_relationships(EndorseRecoveryRelationshipsInput {
            customer_account_id: &account_id,
            endorsements: request.endorsements,
        })
        .await?;
    let result = recovery_relationship_service
        .get_recovery_relationships(GetRecoveryRelationshipsInput {
            account_id: &account_id,
            trusted_contact_role: Some(SocialRecoveryContact),
        })
        .await?;

    Ok(Json(EndorseRecoveryRelationshipsResponse {
        unendorsed_trusted_contacts: result
            .unendorsed_trusted_contacts
            .into_iter()
            .map(|i| i.try_into())
            .collect::<Result<Vec<_>, _>>()?,
        endorsed_trusted_contacts: result
            .endorsed_trusted_contacts
            .into_iter()
            .map(|i| i.try_into())
            .collect::<Result<Vec<_>, _>>()?,
    }))
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct GetRecoveryRelationshipInvitationForCodeResponse {
    pub invitation: InboundInvitation,
}

///
/// This route is used by either FullAccounts or LiteAccounts to retrieve
/// the details of a pending inbound invitation.
///
#[instrument(err, skip(recovery_relationship_service, _feature_flags_service))]
#[utoipa::path(
    get,
    path = "/api/accounts/{account_id}/recovery/relationship-invitations/{code}",
    params(
        ("account_id" = AccountId, Path, description = "AccountId"),
        ("code" = String, Path, description = "Code"),
    ),
    responses(
        (status = 200, description = "Recovery relationship invitation", body=GetRecoveryRelationshipInvitationForCodeResponse),
    ),
)]
pub async fn get_recovery_relationship_invitation_for_code(
    Path((account_id, code)): Path<(AccountId, String)>,
    State(recovery_relationship_service): State<RecoveryRelationshipService>,
    State(_feature_flags_service): State<FeatureFlagsService>,
) -> Result<Json<GetRecoveryRelationshipInvitationForCodeResponse>, ApiError> {
    let result = recovery_relationship_service
        .get_recovery_relationship_invitation_for_code(
            GetRecoveryRelationshipInvitationForCodeInput { code: &code },
        )
        .await?;

    Ok(Json(GetRecoveryRelationshipInvitationForCodeResponse {
        invitation: result.try_into()?,
    }))
}

// SocialChallenges

#[derive(Debug, Serialize, Deserialize, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct CustomerSocialChallengeResponse {
    pub recovery_relationship_id: RecoveryRelationshipId,
    pub trusted_contact_recovery_pake_pubkey: String,
    pub recovery_pake_confirmation: String,
    pub resealed_dek: String,
}

impl From<SocialChallengeResponse> for CustomerSocialChallengeResponse {
    fn from(value: SocialChallengeResponse) -> Self {
        Self {
            recovery_relationship_id: value.recovery_relationship_id,
            trusted_contact_recovery_pake_pubkey: value.trusted_contact_recovery_pake_pubkey,
            recovery_pake_confirmation: value.recovery_pake_confirmation,
            resealed_dek: value.resealed_dek,
        }
    }
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct CustomerSocialChallenge {
    pub social_challenge_id: SocialChallengeId,
    pub counter: u32,
    pub responses: Vec<CustomerSocialChallengeResponse>,
}

impl From<SocialChallenge> for CustomerSocialChallenge {
    fn from(value: SocialChallenge) -> Self {
        Self {
            social_challenge_id: value.id,
            counter: value.counter,
            responses: value
                .responses
                .into_iter()
                .map(|r| r.into())
                .collect::<Vec<_>>(),
        }
    }
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct TrustedContactSocialChallenge {
    pub social_challenge_id: SocialChallengeId,
    pub protected_customer_recovery_pake_pubkey: String,
    pub sealed_dek: String,
}

impl TryFrom<(RecoveryRelationshipId, SocialChallenge)> for TrustedContactSocialChallenge {
    type Error = RecoveryError;

    fn try_from(
        (recovery_relationship_id, challenge): (RecoveryRelationshipId, SocialChallenge),
    ) -> Result<Self, Self::Error> {
        let info = challenge
            .trusted_contact_challenge_requests
            .get(&recovery_relationship_id)
            .map(|r| r.to_owned())
            .ok_or(RecoveryError::ChallengeRequestNotFound)?;

        Ok(Self {
            social_challenge_id: challenge.id,
            protected_customer_recovery_pake_pubkey: info.protected_customer_recovery_pake_pubkey,
            sealed_dek: info.sealed_dek,
        })
    }
}

#[derive(Serialize, Deserialize, Debug, Clone, ToSchema)]
pub struct StartChallengeTrustedContactRequest {
    pub recovery_relationship_id: RecoveryRelationshipId,
    #[serde(flatten)]
    pub challenge_request: TrustedContactChallengeRequest,
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct StartSocialChallengeRequest {
    pub trusted_contacts: Vec<StartChallengeTrustedContactRequest>,
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct StartSocialChallengeResponse {
    pub social_challenge: CustomerSocialChallenge,
}

///
/// Used by the Customer to initiate a Social challenge.
///
/// The customer must provide a valid recovery authentication token to start
/// the challenge
///
#[instrument(
    err,
    skip(account_service, social_challenge_service, _feature_flags_service)
)]
#[utoipa::path(
    post,
    path = "/api/accounts/{account_id}/recovery/social-challenges",
    params(
        ("account_id" = AccountId, Path, description = "AccountId"),
    ),
    request_body = StartSocialChallengeRequest,
    responses(
        (status = 200, description = "Social challenge started", body=StartSocialChallengeResponse),
    ),
)]
pub async fn start_social_challenge(
    Path(account_id): Path<AccountId>,
    State(account_service): State<AccountService>,
    State(social_challenge_service): State<SocialChallengeService>,
    State(_feature_flags_service): State<FeatureFlagsService>,
    Json(request): Json<StartSocialChallengeRequest>,
) -> Result<Json<StartSocialChallengeResponse>, ApiError> {
    let customer_account = account_service
        .fetch_full_account(FetchAccountInput {
            account_id: &account_id,
        })
        .await?;

    let requests = request
        .trusted_contacts
        .into_iter()
        .map(|t| {
            let recovery_relationship_id = t.recovery_relationship_id;
            let challenge = t.challenge_request.clone();
            (recovery_relationship_id, challenge)
        })
        .collect::<HashMap<_, _>>();
    let result = social_challenge_service
        .create_social_challenge(CreateSocialChallengeInput {
            customer_account: &customer_account,
            requests,
        })
        .await?;

    Ok(Json(StartSocialChallengeResponse {
        social_challenge: result.into(),
    }))
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct VerifySocialChallengeRequest {
    pub recovery_relationship_id: RecoveryRelationshipId,
    pub counter: u32,
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct VerifySocialChallengeResponse {
    pub social_challenge: TrustedContactSocialChallenge,
}

///
/// This route is used by Trusted Contacts to retrieve the social challenge
/// given the code and the recovery relationship. The code was given to them
/// by the Customer who's account they're protecting.
///
#[instrument(err, skip(social_challenge_service, _feature_flags_service))]
#[utoipa::path(
    post,
    path = "/api/accounts/{account_id}/recovery/verify-social-challenge",
    params(
        ("account_id" = AccountId, Path, description = "AccountId"),
    ),
    request_body = VerifySocialChallengeRequest,
    responses(
        (status = 200, description = "Social challenge code verified", body=VerifySocialChallengeResponse),
    ),
)]
pub async fn verify_social_challenge(
    Path(account_id): Path<AccountId>,
    State(social_challenge_service): State<SocialChallengeService>,
    State(_feature_flags_service): State<FeatureFlagsService>,
    Json(request): Json<VerifySocialChallengeRequest>,
) -> Result<Json<VerifySocialChallengeResponse>, ApiError> {
    let challenge = social_challenge_service
        .fetch_social_challenge_as_trusted_contact(FetchSocialChallengeAsTrustedContactInput {
            trusted_contact_account_id: &account_id,
            recovery_relationship_id: &request.recovery_relationship_id,
            counter: request.counter,
        })
        .await?;
    let social_challenge = (request.recovery_relationship_id, challenge).try_into()?;
    Ok(Json(VerifySocialChallengeResponse { social_challenge }))
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct RespondToSocialChallengeRequest {
    #[serde(deserialize_with = "deserialize_pake_pubkey")]
    pub trusted_contact_recovery_pake_pubkey: String,
    pub recovery_pake_confirmation: String,
    pub resealed_dek: String,
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct RespondToSocialChallengeResponse {}

///
/// This route is used by Trusted Contacts to attest the social challenge
/// and to provide the shared secret that the Customer will use to recover
/// their account.
///
#[instrument(err, skip(social_challenge_service, _feature_flags_service))]
#[utoipa::path(
    put,
    path = "/api/accounts/{account_id}/recovery/social-challenges/{social_challenge_id}",
    params(
        ("account_id" = AccountId, Path, description = "AccountId"),
        ("social_challenge_id" = AccountId, Path, description = "SocialChallengeId"),
    ),
    request_body = RespondToSocialChallengeRequest,
    responses(
        (status = 200, description = "Responded to social challenge", body=RespondToSocialChallengeResponse),
    ),
)]
pub async fn respond_to_social_challenge(
    Path((account_id, social_challenge_id)): Path<(AccountId, SocialChallengeId)>,
    State(social_challenge_service): State<SocialChallengeService>,
    State(_feature_flags_service): State<FeatureFlagsService>,
    Json(request): Json<RespondToSocialChallengeRequest>,
) -> Result<Json<RespondToSocialChallengeResponse>, ApiError> {
    social_challenge_service
        .respond_to_social_challenge(RespondToSocialChallengeInput {
            trusted_contact_account_id: &account_id,
            social_challenge_id: &social_challenge_id,
            trusted_contact_recovery_pake_pubkey: &request.trusted_contact_recovery_pake_pubkey,
            recovery_pake_confirmation: &request.recovery_pake_confirmation,
            resealed_dek: &request.resealed_dek,
        })
        .await?;

    Ok(Json(RespondToSocialChallengeResponse {}))
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct FetchSocialChallengeResponse {
    pub social_challenge: CustomerSocialChallenge,
}

///
/// Used by the Customer to fetch a Pending Social challenge.
///
/// The customer must provide a valid recovery authentication token
/// to check the status of the challenge.
///
#[instrument(
    err,
    skip(account_service, social_challenge_service, _feature_flags_service)
)]
#[utoipa::path(
    get,
    path = "/api/accounts/{account_id}/recovery/social-challenges/{social_challenge_id}",
    params(
        ("account_id" = AccountId, Path, description = "AccountId"),
        ("social_challenge_id" = AccountId, Path, description = "SocialChallengeId"),
    ),
    responses(
        (status = 200, description = "Social challenge", body=FetchSocialChallengeResponse),
    ),
)]
pub async fn fetch_social_challenge(
    Path((account_id, social_challenge_id)): Path<(AccountId, SocialChallengeId)>,
    State(account_service): State<AccountService>,
    State(social_challenge_service): State<SocialChallengeService>,
    State(_feature_flags_service): State<FeatureFlagsService>,
) -> Result<Json<FetchSocialChallengeResponse>, ApiError> {
    let customer_account = account_service
        .fetch_full_account(FetchAccountInput {
            account_id: &account_id,
        })
        .await?;

    let result = social_challenge_service
        .fetch_social_challenge_as_customer(FetchSocialChallengeAsCustomerInput {
            customer_account_id: &customer_account.id,
            social_challenge_id: &social_challenge_id,
        })
        .await?;

    Ok(Json(FetchSocialChallengeResponse {
        social_challenge: result.into(),
    }))
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
pub struct CreateInheritanceClaimRequest {
    pub recovery_relationship_id: RecoveryRelationshipId,
    pub auth: InheritanceClaimAuthKeys,
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
pub struct CreateInheritanceClaimResponse {
    pub claim: BeneficiaryInheritanceClaimView,
}

///
/// This route is used by a beneficiary to start an inheritance claim
/// to claim funds from a deceased benefactor. The beneficiary must provide
/// the recovery relationship id and the auth keys to start the claim.
///
#[instrument(err, skip(account_service, inheritance_service))]
#[utoipa::path(
    put,
    path = "/api/accounts/{account_id}/recovery/inheritance/claims",
    params(
        ("account_id" = AccountId, Path, description = "AccountId"),
    ),
    request_body = CreateInheritanceClaimRequest,
    responses(
        (status = 200, description = "Created a new inheritance claim", body=CreateInheritanceClaimResponse),
    ),
)]
pub async fn create_inheritance_claim(
    Path(beneficiary_account_id): Path<AccountId>,
    State(account_service): State<AccountService>,
    State(inheritance_service): State<InheritanceService>,
    Json(request): Json<CreateInheritanceClaimRequest>,
) -> Result<Json<CreateInheritanceClaimResponse>, ApiError> {
    let beneficiary_account = account_service
        .fetch_account(FetchAccountInput {
            account_id: &beneficiary_account_id,
        })
        .await?;
    if !matches!(beneficiary_account, Account::Full(_)) {
        return Err(ApiError::GenericForbidden(
            "Incorrect calling account type".to_string(),
        ));
    }

    let claim = inheritance_service
        .create_claim(CreateInheritanceClaimInput {
            beneficiary_account: &beneficiary_account,
            recovery_relationship_id: request.recovery_relationship_id,
            auth_keys: request.auth,
        })
        .await?;

    Ok(Json(CreateInheritanceClaimResponse {
        claim: claim.into(),
    }))
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct GetInheritanceClaimsResponse {
    pub claims_as_benefactor: Vec<BenefactorInheritanceClaimView>,
    pub claims_as_beneficiary: Vec<BeneficiaryInheritanceClaimView>,
}

///
/// This route is used by both Benefactors and Beneficiaries to retrieve
/// inheritance claims.
///
/// For Benefactors, we will show:
/// - All the inheritance claims for which they are a benefactor
///
/// For Trusted Contacts, we will show:
/// - All the inheritance claims for which they are a beneficiary
///
#[instrument(err, skip(inheritance_service))]
#[utoipa::path(
    get,
    path = "/api/accounts/{account_id}/recovery/inheritance/claims",
    params(
        ("account_id" = AccountId, Path, description = "AccountId"),
    ),
    responses(
        (status = 200, description = "Inheritance claims", body=GetInheritanceClaimsResponse),
    ),
)]
pub async fn get_inheritance_claims(
    Path(account_id): Path<AccountId>,
    State(inheritance_service): State<InheritanceService>,
) -> Result<Json<GetInheritanceClaimsResponse>, ApiError> {
    let result = inheritance_service
        .get_inheritance_claims(GetInheritanceClaimsInput {
            account_id: &account_id,
        })
        .await?;

    Ok(Json(GetInheritanceClaimsResponse {
        claims_as_benefactor: result
            .claims_as_benefactor
            .into_iter()
            .map(|c| c.into())
            .collect(),
        claims_as_beneficiary: result
            .claims_as_beneficiary
            .into_iter()
            .map(|c| c.into())
            .collect(),
    }))
}

#[derive(Serialize, Deserialize, ToSchema, Debug, Clone)]
pub struct InheritancePackage {
    pub recovery_relationship_id: RecoveryRelationshipId,
    pub sealed_dek: String,
    pub sealed_mobile_key: String,
}
impl From<InheritancePackage> for Package {
    fn from(value: InheritancePackage) -> Self {
        Self {
            recovery_relationship_id: value.recovery_relationship_id,
            sealed_dek: value.sealed_dek,
            sealed_mobile_key: value.sealed_mobile_key,

            updated_at: OffsetDateTime::now_utc(),
            created_at: OffsetDateTime::now_utc(),
        }
    }
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
pub struct UploadInheritancePackagesRequest {
    pub packages: Vec<InheritancePackage>,
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
pub struct UploadInheritancePackagesResponse {}

#[utoipa::path(
    post,
    path = "/api/accounts/{account_id}/recovery/inheritance/packages",
    params(
        ("account_id" = AccountId, Path, description = "AccountId"),
    ),
    request_body = UploadInheritancePackagesRequest,
    responses(
        (status = 200, description = "Upload successful", body=UploadInheritancePackagesResponse),
    ),
)]
pub async fn upload_inheritance_packages(
    Path(account_id): Path<AccountId>,
    State(account_service): State<AccountService>,
    State(inheritance_service): State<InheritanceService>,
    Json(request): Json<UploadInheritancePackagesRequest>,
) -> Result<Json<UploadInheritancePackagesResponse>, ApiError> {
    let account = account_service
        .fetch_account(FetchAccountInput {
            account_id: &account_id,
        })
        .await?;

    let Account::Full(_) = account else {
        return Err(ApiError::GenericForbidden(
            "Incorrect calling account type".to_string(),
        ));
    };

    inheritance_service
        .upload_packages(request.packages.into_iter().map(Package::from).collect())
        .await?;

    Ok(Json(UploadInheritancePackagesResponse {}))
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
pub struct CancelInheritanceClaimRequest {}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
#[serde(untagged)]
pub enum CancelInheritanceClaimResponse {
    Benefactor {
        claim: BenefactorInheritanceClaimView,
    },
    Beneficiary {
        claim: BeneficiaryInheritanceClaimView,
    },
}

///
/// This route is used by the benefactor or beneficiary to cancel an
/// inheritance claim. For both, an inheritance claim id must be provided.
///
#[instrument(err, skip(account_service, inheritance_service))]
#[utoipa::path(
    put,
    path = "/api/accounts/{account_id}/recovery/inheritance/claims/{inheritance_claim_id}/cancel",
    params(
        ("account_id" = AccountId, Path, description = "AccountId"),
        ("inheritance_claim_id" = InheritanceClaimId, Path, description = "Identifier for the inheritance claim"),
    ),
    request_body = CancelInheritanceClaimRequest,
    responses(
        (status = 200, description = "Cancel the inheritance claim", body=CancelInheritanceClaimResponse),
    ),
)]
pub async fn cancel_inheritance_claim(
    Path((account_id, inheritance_claim_id)): Path<(AccountId, InheritanceClaimId)>,
    State(account_service): State<AccountService>,
    State(inheritance_service): State<InheritanceService>,
    Json(request): Json<CancelInheritanceClaimRequest>,
) -> Result<Json<CancelInheritanceClaimResponse>, ApiError> {
    let account = account_service
        .fetch_account(FetchAccountInput {
            account_id: &account_id,
        })
        .await?;
    if !matches!(account, Account::Full(_)) {
        return Err(ApiError::GenericForbidden(
            "Incorrect calling account type".to_string(),
        ));
    }
    let (canceled_by, claim) = inheritance_service
        .cancel_claim(CancelInheritanceClaimInput {
            account: &account,
            inheritance_claim_id,
        })
        .await?;

    match canceled_by {
        InheritanceClaimCanceledBy::Benefactor => {
            return Ok(Json(CancelInheritanceClaimResponse::Benefactor {
                claim: claim.into(),
            }));
        }
        InheritanceClaimCanceledBy::Beneficiary => {
            return Ok(Json(CancelInheritanceClaimResponse::Beneficiary {
                claim: claim.into(),
            }));
        }
    }
}
