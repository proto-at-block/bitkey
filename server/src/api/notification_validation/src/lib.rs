use async_trait::async_trait;
use error::NotificationValidationError;
use notification::payloads::privileged_action_canceled_oob_verification::PrivilegedActionCanceledOutOfBandVerificationPayload;
use notification::payloads::privileged_action_completed_oob_verification::PrivilegedActionCompletedOutOfBandVerificationPayload;
use notification::payloads::privileged_action_pending_oob_verification::PrivilegedActionPendingOutOfBandVerificationPayload;
use notification::payloads::security_hub::SecurityHubPayload;
use notification::payloads::transaction_verification::TransactionVerificationPayload;
use notification::{
    entities::NotificationCompositeKey,
    payloads::{
        comms_verification::CommsVerificationPayload,
        inheritance_claim_canceled::InheritanceClaimCanceledPayload,
        inheritance_claim_period_almost_over::InheritanceClaimPeriodAlmostOverPayload,
        inheritance_claim_period_completed::InheritanceClaimPeriodCompletedPayload,
        inheritance_claim_period_initiated::InheritanceClaimPeriodInitiatedPayload,
        payment::{ConfirmedPaymentPayload, PendingPaymentPayload},
        privileged_action_canceled_delay_period::PrivilegedActionCanceledDelayPeriodPayload,
        privileged_action_completed_delay_period::PrivilegedActionCompletedDelayPeriodPayload,
        privileged_action_pending_delay_period::PrivilegedActionPendingDelayPeriodPayload,
        push_blast::PushBlastPayload,
        recovery_canceled_delay_period::RecoveryCanceledDelayPeriodPayload,
        recovery_completed_delay_period::RecoveryCompletedDelayPeriodPayload,
        recovery_pending_delay_period::RecoveryPendingDelayPeriodPayload,
        recovery_relationship_benefactor_invitation_pending::RecoveryRelationshipBenefactorInvitationPendingPayload,
        recovery_relationship_deleted::RecoveryRelationshipDeletedPayload,
        recovery_relationship_invitation_accepted::RecoveryRelationshipInvitationAcceptedPayload,
        social_challenge_response_received::SocialChallengeResponseReceivedPayload,
        test_notification::TestNotificationPayload,
    },
    NotificationPayload, NotificationPayloadType,
};
use recovery::{entities::RecoveryStatus, repository::RecoveryRepository};
use repository::{
    account::AccountRepository,
    privileged_action::PrivilegedActionRepository,
    recovery::{inheritance::InheritanceRepository, social::SocialRecoveryRepository},
};
use serde_json::Value;
use time::{Duration, OffsetDateTime};
use types::{
    privileged_action::repository::{AuthorizationStrategyRecord, OutOfBandRecord, RecordStatus},
    recovery::{inheritance::claim::InheritanceClaim, social::relationship::RecoveryRelationship},
};

mod error;

#[derive(Clone)]
pub struct NotificationValidationState {
    recovery_repository: RecoveryRepository,
    privileged_action_repository: PrivilegedActionRepository,
    inheritance_repository: InheritanceRepository,
    social_recovery_repository: SocialRecoveryRepository,
    account_repository: AccountRepository,
}

impl NotificationValidationState {
    pub fn new(
        recovery_repository: RecoveryRepository,
        privileged_action_repository: PrivilegedActionRepository,
        inheritance_repository: InheritanceRepository,
        social_recovery_repository: SocialRecoveryRepository,
        account_repository: AccountRepository,
    ) -> Self {
        Self {
            recovery_repository,
            privileged_action_repository,
            inheritance_repository,
            social_recovery_repository,
            account_repository,
        }
    }
}

#[async_trait]
pub trait ValidateNotificationDelivery: Send + Sync {
    async fn validate_delivery(
        &self,
        _state: &NotificationValidationState,
        _composite_key: &NotificationCompositeKey,
    ) -> bool {
        true
    }
}

pub fn to_validator(
    payload_type: NotificationPayloadType,
    payload: &NotificationPayload,
) -> Result<&dyn ValidateNotificationDelivery, NotificationValidationError> {
    let validator: &dyn ValidateNotificationDelivery =
        match payload_type {
            NotificationPayloadType::TestPushNotification => payload
                .test_notification_payload
                .as_ref()
                .ok_or(NotificationValidationError::ToValidatorError)?,
            NotificationPayloadType::RecoveryPendingDelayPeriod => payload
                .recovery_pending_delay_period_payload
                .as_ref()
                .ok_or(NotificationValidationError::ToValidatorError)?,
            NotificationPayloadType::RecoveryCompletedDelayPeriod => payload
                .recovery_completed_delay_period_payload
                .as_ref()
                .ok_or(NotificationValidationError::ToValidatorError)?,
            NotificationPayloadType::RecoveryCanceledDelayPeriod => payload
                .recovery_canceled_delay_period_payload
                .as_ref()
                .ok_or(NotificationValidationError::ToValidatorError)?,
            NotificationPayloadType::CommsVerification => payload
                .comms_verification_payload
                .as_ref()
                .ok_or(NotificationValidationError::ToValidatorError)?,
            NotificationPayloadType::ConfirmedPaymentNotification => payload
                .confirmed_payment_payload
                .as_ref()
                .ok_or(NotificationValidationError::ToValidatorError)?,
            NotificationPayloadType::RecoveryRelationshipInvitationAccepted => payload
                .recovery_relationship_invitation_accepted_payload
                .as_ref()
                .ok_or(NotificationValidationError::ToValidatorError)?,
            NotificationPayloadType::RecoveryRelationshipDeleted => payload
                .recovery_relationship_deleted_payload
                .as_ref()
                .ok_or(NotificationValidationError::ToValidatorError)?,
            NotificationPayloadType::SocialChallengeResponseReceived => payload
                .social_challenge_response_received_payload
                .as_ref()
                .ok_or(NotificationValidationError::ToValidatorError)?,
            NotificationPayloadType::PushBlast => payload
                .push_blast_payload
                .as_ref()
                .ok_or(NotificationValidationError::ToValidatorError)?,
            NotificationPayloadType::PendingPaymentNotification => payload
                .pending_payment_payload
                .as_ref()
                .ok_or(NotificationValidationError::ToValidatorError)?,
            NotificationPayloadType::PrivilegedActionCanceledDelayPeriod => payload
                .privileged_action_canceled_delay_period_payload
                .as_ref()
                .ok_or(NotificationValidationError::ToValidatorError)?,
            NotificationPayloadType::PrivilegedActionCanceledOutOfBandVerification => payload
                .privileged_action_canceled_oob_verification_payload
                .as_ref()
                .ok_or(NotificationValidationError::ToValidatorError)?,
            NotificationPayloadType::PrivilegedActionCompletedDelayPeriod => payload
                .privileged_action_completed_delay_period_payload
                .as_ref()
                .ok_or(NotificationValidationError::ToValidatorError)?,
            NotificationPayloadType::PrivilegedActionCompletedOutOfBandVerification => payload
                .privileged_action_completed_oob_verification_payload
                .as_ref()
                .ok_or(NotificationValidationError::ToValidatorError)?,
            NotificationPayloadType::PrivilegedActionPendingOutOfBandVerification => payload
                .privileged_action_pending_oob_verification_payload
                .as_ref()
                .ok_or(NotificationValidationError::ToValidatorError)?,
            NotificationPayloadType::PrivilegedActionPendingDelayPeriod => payload
                .privileged_action_pending_delay_period_payload
                .as_ref()
                .ok_or(NotificationValidationError::ToValidatorError)?,
            NotificationPayloadType::InheritanceClaimPeriodAlmostOver => payload
                .inheritance_claim_period_almost_over_payload
                .as_ref()
                .ok_or(NotificationValidationError::ToValidatorError)?,
            NotificationPayloadType::InheritanceClaimPeriodInitiated => payload
                .inheritance_claim_period_initiated_payload
                .as_ref()
                .ok_or(NotificationValidationError::ToValidatorError)?,
            NotificationPayloadType::InheritanceClaimCanceled => payload
                .inheritance_claim_canceled_payload
                .as_ref()
                .ok_or(NotificationValidationError::ToValidatorError)?,
            NotificationPayloadType::InheritanceClaimPeriodCompleted => payload
                .inheritance_claim_period_completed_payload
                .as_ref()
                .ok_or(NotificationValidationError::ToValidatorError)?,
            NotificationPayloadType::RecoveryRelationshipBenefactorInvitationPending => payload
                .recovery_relationship_benefactor_invitation_pending_payload
                .as_ref()
                .ok_or(NotificationValidationError::ToValidatorError)?,
            NotificationPayloadType::TransactionVerification => payload
                .transaction_verification_payload
                .as_ref()
                .ok_or(NotificationValidationError::ToValidatorError)?,
            NotificationPayloadType::SecurityHub => payload
                .security_hub_payload
                .as_ref()
                .ok_or(NotificationValidationError::ToValidatorError)?,
        };
    Ok(validator)
}

#[async_trait]
impl ValidateNotificationDelivery for RecoveryCompletedDelayPeriodPayload {
    async fn validate_delivery(
        &self,
        state: &NotificationValidationState,
        composite_key: &NotificationCompositeKey,
    ) -> bool {
        let (account_id, _) = composite_key;
        let recovery_result = state
            .recovery_repository
            .fetch(account_id, self.initiation_time)
            .await;

        if let Ok(recovery) = recovery_result {
            return recovery.recovery_status == RecoveryStatus::Pending;
        }
        false
    }
}

#[async_trait]
impl ValidateNotificationDelivery for RecoveryCanceledDelayPeriodPayload {
    async fn validate_delivery(
        &self,
        state: &NotificationValidationState,
        composite_key: &NotificationCompositeKey,
    ) -> bool {
        let (account_id, _) = composite_key;
        let recovery_result = state
            .recovery_repository
            .fetch(account_id, self.initiation_time)
            .await;

        if let Ok(recovery) = recovery_result {
            return recovery.recovery_status == RecoveryStatus::Canceled
                || recovery.recovery_status == RecoveryStatus::CanceledInContest;
        }
        false
    }
}

#[async_trait]
impl ValidateNotificationDelivery for RecoveryPendingDelayPeriodPayload {
    async fn validate_delivery(
        &self,
        state: &NotificationValidationState,
        composite_key: &NotificationCompositeKey,
    ) -> bool {
        let (account_id, _) = composite_key;
        let recovery_result = state
            .recovery_repository
            .fetch(account_id, self.initiation_time)
            .await;

        if let Ok(recovery) = recovery_result {
            if let Some(requirements) = recovery.requirements.delay_notify_requirements.as_ref() {
                return OffsetDateTime::now_utc() < requirements.delay_end_time
                    && recovery.recovery_status == RecoveryStatus::Pending;
            }
        }
        false
    }
}

#[async_trait]
impl ValidateNotificationDelivery for TestNotificationPayload {}

#[async_trait]
impl ValidateNotificationDelivery for CommsVerificationPayload {}

#[async_trait]
impl ValidateNotificationDelivery for ConfirmedPaymentPayload {}

#[async_trait]
impl ValidateNotificationDelivery for PendingPaymentPayload {}

#[async_trait]
impl ValidateNotificationDelivery for RecoveryRelationshipInvitationAcceptedPayload {
    async fn validate_delivery(
        &self,
        state: &NotificationValidationState,
        _composite_key: &NotificationCompositeKey,
    ) -> bool {
        let recovery_relationship_result = state
            .social_recovery_repository
            .fetch_recovery_relationship(&self.recovery_relationship_id)
            .await;
        matches!(
            recovery_relationship_result,
            Ok(RecoveryRelationship::Unendorsed(_))
        )
    }
}

#[async_trait]
impl ValidateNotificationDelivery for RecoveryRelationshipDeletedPayload {}

#[async_trait]
impl ValidateNotificationDelivery for SocialChallengeResponseReceivedPayload {}

#[async_trait]
impl ValidateNotificationDelivery for PushBlastPayload {}

#[async_trait]
impl ValidateNotificationDelivery for PrivilegedActionCanceledDelayPeriodPayload {}

#[async_trait]
impl ValidateNotificationDelivery for PrivilegedActionCompletedDelayPeriodPayload {
    async fn validate_delivery(
        &self,
        state: &NotificationValidationState,
        composite_key: &NotificationCompositeKey,
    ) -> bool {
        let (account_id, _) = composite_key;
        let privileged_action_result = state
            .privileged_action_repository
            .fetch_by_id::<Value>(&self.privileged_action_instance_id)
            .await;

        if let Ok(privileged_action_instance) = privileged_action_result {
            if let AuthorizationStrategyRecord::DelayAndNotify(delay_and_notify_definition) =
                privileged_action_instance.authorization_strategy
            {
                return privileged_action_instance.account_id == *account_id
                    && OffsetDateTime::now_utc() >= delay_and_notify_definition.delay_end_time
                    && delay_and_notify_definition.status == RecordStatus::Pending;
            }
        }
        false
    }
}

#[async_trait]
impl ValidateNotificationDelivery for PrivilegedActionPendingDelayPeriodPayload {
    async fn validate_delivery(
        &self,
        state: &NotificationValidationState,
        composite_key: &NotificationCompositeKey,
    ) -> bool {
        let (account_id, _) = composite_key;
        let privileged_action_result = state
            .privileged_action_repository
            .fetch_by_id::<Value>(&self.privileged_action_instance_id)
            .await;

        if let Ok(privileged_action_instance) = privileged_action_result {
            if let AuthorizationStrategyRecord::DelayAndNotify(delay_and_notify_definition) =
                privileged_action_instance.authorization_strategy
            {
                return privileged_action_instance.account_id == *account_id
                    && OffsetDateTime::now_utc() < delay_and_notify_definition.delay_end_time
                    && delay_and_notify_definition.status == RecordStatus::Pending;
            }
        }
        false
    }
}

#[async_trait]
impl ValidateNotificationDelivery for InheritanceClaimPeriodInitiatedPayload {
    async fn validate_delivery(
        &self,
        state: &NotificationValidationState,
        _: &NotificationCompositeKey,
    ) -> bool {
        let claim_result = state
            .inheritance_repository
            .fetch_inheritance_claim(&self.inheritance_claim_id)
            .await;

        if let Ok(InheritanceClaim::Pending(pending_claim)) = claim_result {
            return OffsetDateTime::now_utc() < pending_claim.delay_end_time;
        }
        false
    }
}

#[async_trait]
impl ValidateNotificationDelivery for InheritanceClaimPeriodAlmostOverPayload {
    async fn validate_delivery(
        &self,
        state: &NotificationValidationState,
        _: &NotificationCompositeKey,
    ) -> bool {
        let claim_result = state
            .inheritance_repository
            .fetch_inheritance_claim(&self.inheritance_claim_id)
            .await;

        if let Ok(InheritanceClaim::Pending(pending_claim)) = claim_result {
            return OffsetDateTime::now_utc() >= (pending_claim.delay_end_time - Duration::days(3));
        }
        false
    }
}

#[async_trait]
impl ValidateNotificationDelivery for InheritanceClaimCanceledPayload {}

#[async_trait]
impl ValidateNotificationDelivery for InheritanceClaimPeriodCompletedPayload {
    async fn validate_delivery(
        &self,
        state: &NotificationValidationState,
        _: &NotificationCompositeKey,
    ) -> bool {
        let claim_result = state
            .inheritance_repository
            .fetch_inheritance_claim(&self.inheritance_claim_id)
            .await;

        if let Ok(InheritanceClaim::Pending(pending_claim)) = claim_result {
            return OffsetDateTime::now_utc() >= pending_claim.delay_end_time;
        }
        false
    }
}

#[async_trait]
impl ValidateNotificationDelivery for RecoveryRelationshipBenefactorInvitationPendingPayload {
    async fn validate_delivery(
        &self,
        state: &NotificationValidationState,
        _: &NotificationCompositeKey,
    ) -> bool {
        let relationship_result = state
            .social_recovery_repository
            .fetch_recovery_relationship(&self.recovery_relationship_id)
            .await;

        matches!(relationship_result, Ok(RecoveryRelationship::Invitation(_)))
    }
}

#[async_trait]
impl ValidateNotificationDelivery for TransactionVerificationPayload {}

#[async_trait]
impl ValidateNotificationDelivery for SecurityHubPayload {
    async fn validate_delivery(
        &self,
        state: &NotificationValidationState,
        composite_key: &NotificationCompositeKey,
    ) -> bool {
        let (account_id, _) = composite_key;

        // Validates if the trigger is still active for the account
        // AND it's the same instance of the trigger (else a separate sequence of notifications will
        // have been kicked off))
        state
            .account_repository
            .fetch(account_id)
            .await
            .ok()
            .map(|account| {
                account
                    .get_common_fields()
                    .notifications_triggers
                    .iter()
                    .any(|t| t.trigger_type == self.trigger_type && t.created_at == self.created_at)
            })
            .unwrap_or_default()
    }
}

#[async_trait]
impl ValidateNotificationDelivery for PrivilegedActionPendingOutOfBandVerificationPayload {
    async fn validate_delivery(
        &self,
        state: &NotificationValidationState,
        _: &NotificationCompositeKey,
    ) -> bool {
        let privileged_action_result = state
            .privileged_action_repository
            .fetch_by_id::<Value>(&self.privileged_action_instance_id)
            .await;

        matches!(
            privileged_action_result,
            Ok(instance) if matches!(
                instance.authorization_strategy,
                AuthorizationStrategyRecord::OutOfBand(OutOfBandRecord {
                    status: RecordStatus::Pending,
                    ..
                })
            )
        )
    }
}

#[async_trait]
impl ValidateNotificationDelivery for PrivilegedActionCanceledOutOfBandVerificationPayload {
    async fn validate_delivery(
        &self,
        state: &NotificationValidationState,
        _: &NotificationCompositeKey,
    ) -> bool {
        let privileged_action_result = state
            .privileged_action_repository
            .fetch_by_id::<Value>(&self.privileged_action_instance_id)
            .await;

        matches!(
            privileged_action_result,
            Ok(instance) if matches!(
                instance.authorization_strategy,
                AuthorizationStrategyRecord::OutOfBand(OutOfBandRecord {
                    status: RecordStatus::Canceled,
                    ..
                })
            )
        )
    }
}

#[async_trait]
impl ValidateNotificationDelivery for PrivilegedActionCompletedOutOfBandVerificationPayload {
    async fn validate_delivery(
        &self,
        state: &NotificationValidationState,
        _: &NotificationCompositeKey,
    ) -> bool {
        let privileged_action_result = state
            .privileged_action_repository
            .fetch_by_id::<Value>(&self.privileged_action_instance_id)
            .await;

        matches!(
            privileged_action_result,
            Ok(instance) if matches!(
                instance.authorization_strategy,
                AuthorizationStrategyRecord::OutOfBand(OutOfBandRecord {
                    status: RecordStatus::Completed,
                    ..
                })
            )
        )
    }
}
