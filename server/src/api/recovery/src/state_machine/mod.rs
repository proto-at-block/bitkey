use account::entities::{Account, Factor, FullAccount, FullAccountAuthKeysPayload};
use account::service::FetchAccountInput;
use account::service::Service as AccountService;
use async_trait::async_trait;
use authn_authz::key_claims::KeyClaims;
use comms_verification::Service as CommsVerificationService;
use errors::ApiError;
use notification::service::Service as NotificationService;
use serde::{Deserialize, Serialize};
use serde_json::Value;
use time::serde::rfc3339;
use time::OffsetDateTime;
use tracing::{event, Level};
use types::account::identifiers::AccountId;
use userpool::userpool::UserPoolService;
use utoipa::ToSchema;

use crate::entities::{RecoveryStatus, RecoveryType};
use crate::service::social::challenge::Service as SocialChallengeService;
use crate::{
    entities::RecoveryDestination, error::RecoveryError,
    repository::Repository as RecoveryRepository,
};

use self::start_recovery::StartRecoveryState;

// Delay Notify
pub(crate) mod cancel_recovery;
pub mod completable_recovery;
pub(crate) mod current_account_recovery;
pub mod pending_recovery; // TODO: [W-774] Update visibility of struct after migration
pub mod rotated_keyset; // TODO: [W-774] Update visibility of struct after migration
pub(crate) mod start_recovery; // TODO: [W-774] Update visibility of struct after migration

pub(crate) const CONTEST_LOOKBACK_DAYS: i64 = 30;

pub struct RecoveryServices<'a> {
    pub account: &'a AccountService,
    pub recovery: &'a RecoveryRepository,
    pub notification: &'a NotificationService,
    pub challenge: &'a SocialChallengeService,
    pub comms_verification: &'a CommsVerificationService,
}

pub type BoxedRecoveryState = Box<dyn RecoveryState>;

#[derive(Clone)]
pub enum RecoveryEvent {
    CheckAccountRecoveryState,
    CreateRecovery {
        account: FullAccount,
        lost_factor: Factor,
        destination: RecoveryDestination,
        key_proof: KeyClaims,
    },
    CheckEligibleForCompletion {
        challenge: String,
        app_signature: String,
        hardware_signature: String,
    },
    CancelRecovery {
        key_proof: KeyClaims,
    },
    RotateKeyset {
        user_pool_service: UserPoolService,
    },
    UpdateDelayForTestAccountRecovery {
        delay_period_num_sec: Option<i64>,
    },
}

/// Represents result of state execution and which state to transition to next.
pub enum Transition {
    /// Transition to new state.
    Next(BoxedRecoveryState),
    /// Stop executing the state machine and report the result of the execution.
    Complete(Result<BoxedRecoveryState, RecoveryError>),
}

pub trait TransitionTo<S> {}

impl Transition {
    #[allow(clippy::boxed_local)]
    pub fn next<I: RecoveryState, O: RecoveryState>(_i: Box<I>, o: O) -> Transition
    where
        I: TransitionTo<O>,
    {
        Transition::Next(Box::new(o))
    }
}

#[async_trait]
pub trait RecoveryState: RecoveryStateResponse + Sync + Send + 'static {
    async fn next(self: Box<Self>, event: RecoveryEvent, services: &RecoveryServices)
        -> Transition;
}

#[async_trait]
impl<T> RecoveryState for T
where
    T: TransitioningRecoveryState,
{
    async fn next(
        self: Box<Self>,
        event: RecoveryEvent,
        services: &RecoveryServices,
    ) -> Transition {
        self.next_transition_or_err(event, services)
            .await
            .unwrap_or_else(|e| Transition::Complete(Err(e)))
    }
}

#[async_trait]
pub trait RecoveryStateResponse {
    fn response(self: Box<Self>) -> Value;
}

#[async_trait]
pub trait TransitioningRecoveryState: RecoveryState {
    async fn next_transition_or_err(
        self: Box<Self>,
        event: RecoveryEvent,
        services: &RecoveryServices,
    ) -> Result<Transition, RecoveryError>;
}

pub async fn run_recovery_fsm(
    account_id: AccountId,
    events: Vec<RecoveryEvent>,
    account_service: &AccountService,
    recovery_service: &RecoveryRepository,
    notification_service: &NotificationService,
    challenge: &SocialChallengeService,
    comms_verification_service: &CommsVerificationService,
) -> Result<BoxedRecoveryState, ApiError> {
    let mut state: BoxedRecoveryState = Box::new(StartRecoveryState { account_id });
    let iter = events.iter();
    let services = RecoveryServices {
        account: account_service,
        recovery: recovery_service,
        notification: notification_service,
        challenge,
        comms_verification: comms_verification_service,
    };

    for ref mut iter in iter {
        let event = iter.clone();
        let t = state.next(event, &services).await;

        match t {
            Transition::Complete(new_result) => {
                return new_result.map_err(|err| {
                    event!(
                        Level::INFO,
                        "Failed to complete Recovery FSM due to error: {}",
                        err.to_string()
                    );
                    err.into()
                });
            }
            Transition::Next(new_state) => state = new_state,
        }
    }
    Ok(state)
}

#[derive(Serialize, Deserialize, Debug, PartialEq, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct RecoveryResponse {
    #[serde(skip_serializing_if = "Option::is_none")]
    pub pending_delay_notify: Option<PendingDelayNotifyRecovery>,
    pub active_contest: bool,
}

#[derive(Serialize, Deserialize, Debug, PartialEq, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct PendingDelayNotifyRecovery {
    #[serde(with = "rfc3339")]
    pub delay_start_time: OffsetDateTime,
    #[serde(with = "rfc3339")]
    pub delay_end_time: OffsetDateTime,
    pub lost_factor: Factor,
    pub auth_keys: FullAccountAuthKeysPayload,
}

async fn has_recent_contested_delay_notify(
    services: &RecoveryServices<'_>,
    account_id: &AccountId,
    since: OffsetDateTime,
) -> Result<bool, RecoveryError> {
    let recent_contested = services
        .recovery
        .fetch_by_status_since(
            account_id,
            RecoveryType::DelayAndNotify,
            RecoveryStatus::CanceledInContest,
            since,
        )
        .await?;

    let Some(recent_contested) = recent_contested else {
        return Ok(false);
    };

    let Some(action) = recent_contested.recovery_action.delay_notify_action else {
        return Err(RecoveryError::MalformedRecoveryAction);
    };

    let account = services
        .account
        .fetch_account(FetchAccountInput { account_id })
        .await?;
    let Account::Full(full_account) = account else {
        return Err(RecoveryError::SignPSBT);
    };

    // We only consider contested recoveries if its source auth key id is the currently
    //   active auth key id
    Ok(full_account.common_fields.active_auth_keys_id == action.destination.source_auth_keys_id)
}
