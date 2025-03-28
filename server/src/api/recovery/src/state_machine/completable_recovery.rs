use account::service::ClearPushTouchpointsInput;
use account::service::CreateAndRotateAuthKeysInput;
use async_trait::async_trait;
use feature_flags::flag::evaluate_flag_value;
use instrumentation::metrics::KeyValue;
use types::account::entities::Account;
use types::account::entities::{Factor, FullAccount, FullAccountAuthKeysPayload};

use super::{
    rotated_keyset::RotatedKeysetState, RecoveryEvent, RecoveryServices, RecoveryStateResponse,
    Transition, TransitionTo, TransitioningRecoveryState,
};
use crate::entities::RecoveryStatus;
use crate::entities::WalletRecovery;
use crate::error::RecoveryError;
use crate::metrics;

use crate::routes::INHERITANCE_ENABLED_FLAG_KEY;
use crate::service::inheritance::recreate_pending_claims_for_beneficiary::RecreatePendingClaimsForBeneficiaryInput;
use crate::service::social::challenge::clear_social_challenges::ClearSocialChallengesInput;
use crate::state_machine::{PendingDelayNotifyRecovery, RecoveryResponse};

pub(crate) struct CompletableRecoveryState {
    pub(crate) account: FullAccount,
    pub(crate) recovery: WalletRecovery,
    pub(crate) active_contest: bool,
}

#[async_trait]
impl RecoveryStateResponse for CompletableRecoveryState {
    fn response(self: Box<Self>) -> serde_json::Value {
        let requirements = self
            .recovery
            .requirements
            .delay_notify_requirements
            .unwrap();
        let action = self.recovery.recovery_action.delay_notify_action.unwrap();
        serde_json::json!(RecoveryResponse {
            pending_delay_notify: Some(PendingDelayNotifyRecovery {
                delay_start_time: self.recovery.created_at,
                delay_end_time: requirements.delay_end_time,
                lost_factor: requirements.lost_factor,
                auth_keys: FullAccountAuthKeysPayload {
                    app: action.destination.app_auth_pubkey,
                    hardware: action.destination.hardware_auth_pubkey,
                    recovery: action.destination.recovery_auth_pubkey
                }
            }),
            active_contest: self.active_contest,
        })
    }
}

#[async_trait]
impl TransitioningRecoveryState for CompletableRecoveryState {
    async fn next_transition_or_err(
        self: Box<Self>,
        event: RecoveryEvent,
        services: &RecoveryServices,
    ) -> Result<Transition, RecoveryError> {
        tracing::info!(
            recovery_state = std::any::type_name_of_val(&self),
            recovery_event = event.to_string(),
            "Processing recovery event"
        );

        if let RecoveryEvent::RotateKeyset {
            user_pool_service,
            experimentation_claims,
        } = &event
        {
            let recovery = &self.recovery;
            let account = self.account.clone();

            let action = &recovery
                .recovery_action
                .delay_notify_action
                .as_ref()
                .ok_or_else(|| {
                    tracing::error!(
                        recovery_state = std::any::type_name_of_val(&self),
                        recovery_event = event.to_string(),
                        "No pending recovery destination"
                    );
                    RecoveryError::NoPendingRecoveryDestination
                })?;
            if account.common_fields.active_auth_keys_id != action.destination.source_auth_keys_id {
                tracing::error!(
                    recovery_state = std::any::type_name_of_val(&self),
                    recovery_event = event.to_string(),
                    "Invalid recovery destination"
                );
                return Err(RecoveryError::InvalidRecoveryDestination);
            }

            user_pool_service
                .create_or_update_account_users_if_necessary(
                    &account.id,
                    Some(action.destination.app_auth_pubkey),
                    Some(action.destination.hardware_auth_pubkey),
                    action.destination.recovery_auth_pubkey,
                )
                .await?;

            let updated_account = services
                .account
                .create_and_rotate_auth_keys(CreateAndRotateAuthKeysInput {
                    account_id: &account.id,
                    app_auth_pubkey: action.destination.app_auth_pubkey,
                    hardware_auth_pubkey: action.destination.hardware_auth_pubkey,
                    recovery_auth_pubkey: action.destination.recovery_auth_pubkey,
                })
                .await?;

            if matches!(recovery.get_lost_factor(), Some(Factor::Hw)) {
                services
                    .challenge
                    .clear_social_challenges(ClearSocialChallengesInput {
                        customer_account_id: &account.id,
                    })
                    .await?;
            }

            services
                .account
                .clear_push_touchpoints(ClearPushTouchpointsInput {
                    account_id: &account.id,
                })
                .await?;

            services
                .recovery
                .complete(
                    (recovery.account_id.clone(), recovery.created_at),
                    RecoveryStatus::Complete,
                )
                .await?;

            // We already performed the check above that the account is a full account
            if let Account::Full(updated_full_account) = updated_account {
                let context_key = experimentation_claims
                    .overridden_account_context_key(updated_full_account.id.to_owned());

                if evaluate_flag_value(
                    services.feature_flags,
                    INHERITANCE_ENABLED_FLAG_KEY,
                    &context_key,
                )
                .unwrap_or(false)
                {
                    services
                        .inheritance
                        .recreate_pending_claims_for_beneficiary(
                            RecreatePendingClaimsForBeneficiaryInput {
                                beneficiary: &updated_full_account,
                            },
                        )
                        .await?;
                }
            }

            let mut attributes = vec![KeyValue::new(
                metrics::CREATED_DURING_CONTEST_KEY,
                self.active_contest,
            )];
            if let Some(lost_factor) = recovery.get_lost_factor() {
                attributes.push(KeyValue::new(
                    metrics::LOST_FACTOR_KEY,
                    lost_factor.to_string(),
                ));
            }
            metrics::DELAY_NOTIFY_COMPLETED.add(1, &attributes);
            metrics::DELAY_NOTIFY_TIME_TO_COMPLETE.record(
                (services.recovery.cur_time() - recovery.created_at).whole_minutes() as u64,
                &attributes,
            );

            Ok(Transition::next(
                self,
                RotatedKeysetState {
                    active_keyset_id: account.active_keyset_id,
                },
            ))
        } else {
            tracing::error!(
                recovery_state = std::any::type_name_of_val(&self),
                recovery_event = event.to_string(),
                "Invalid transition"
            );
            Err(RecoveryError::InvalidTransition)
        }
    }
}

impl TransitionTo<RotatedKeysetState> for CompletableRecoveryState {}
