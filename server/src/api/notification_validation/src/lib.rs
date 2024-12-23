use async_trait::async_trait;
use error::NotificationValidationError;
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
    privileged_action::PrivilegedActionRepository,
    recovery::{inheritance::InheritanceRepository, social::SocialRecoveryRepository},
};
use serde_json::Value;
use time::{Duration, OffsetDateTime};
use types::{
    privileged_action::repository::{AuthorizationStrategyRecord, DelayAndNotifyStatus},
    recovery::{inheritance::claim::InheritanceClaim, social::relationship::RecoveryRelationship},
};

mod error;

#[derive(Clone)]
pub struct NotificationValidationState {
    recovery_repository: RecoveryRepository,
    privileged_action_repository: PrivilegedActionRepository,
    inheritance_repository: InheritanceRepository,
    social_recovery_repository: SocialRecoveryRepository,
}

impl NotificationValidationState {
    pub fn new(
        recovery_repository: RecoveryRepository,
        privileged_action_repository: PrivilegedActionRepository,
        inheritance_repository: InheritanceRepository,
        social_recovery_repository: SocialRecoveryRepository,
    ) -> Self {
        Self {
            recovery_repository,
            privileged_action_repository,
            inheritance_repository,
            social_recovery_repository,
        }
    }
}

#[async_trait]
pub trait ValidateNotificationDelivery: Send + Sync {
    async fn validate_delivery(
        &self,
        state: &NotificationValidationState,
        composite_key: &NotificationCompositeKey,
    ) -> bool;
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
            NotificationPayloadType::PrivilegedActionCompletedDelayPeriod => payload
                .privileged_action_completed_delay_period_payload
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
impl ValidateNotificationDelivery for TestNotificationPayload {
    async fn validate_delivery(
        &self,
        _state: &NotificationValidationState,
        _: &NotificationCompositeKey,
    ) -> bool {
        true
    }
}

#[async_trait]
impl ValidateNotificationDelivery for CommsVerificationPayload {
    async fn validate_delivery(
        &self,
        _state: &NotificationValidationState,
        _: &NotificationCompositeKey,
    ) -> bool {
        true
    }
}

#[async_trait]
impl ValidateNotificationDelivery for ConfirmedPaymentPayload {
    async fn validate_delivery(
        &self,
        _state: &NotificationValidationState,
        _composite_key: &NotificationCompositeKey,
    ) -> bool {
        true
    }
}

#[async_trait]
impl ValidateNotificationDelivery for PendingPaymentPayload {
    async fn validate_delivery(
        &self,
        _state: &NotificationValidationState,
        _composite_key: &NotificationCompositeKey,
    ) -> bool {
        true
    }
}

#[async_trait]
impl ValidateNotificationDelivery for RecoveryRelationshipInvitationAcceptedPayload {
    async fn validate_delivery(
        &self,
        _state: &NotificationValidationState,
        _composite_key: &NotificationCompositeKey,
    ) -> bool {
        true
    }
}

#[async_trait]
impl ValidateNotificationDelivery for RecoveryRelationshipDeletedPayload {
    async fn validate_delivery(
        &self,
        _state: &NotificationValidationState,
        _composite_key: &NotificationCompositeKey,
    ) -> bool {
        true
    }
}

#[async_trait]
impl ValidateNotificationDelivery for SocialChallengeResponseReceivedPayload {
    async fn validate_delivery(
        &self,
        _state: &NotificationValidationState,
        _composite_key: &NotificationCompositeKey,
    ) -> bool {
        true
    }
}

#[async_trait]
impl ValidateNotificationDelivery for PushBlastPayload {
    async fn validate_delivery(
        &self,
        _state: &NotificationValidationState,
        _: &NotificationCompositeKey,
    ) -> bool {
        true
    }
}

#[async_trait]
impl ValidateNotificationDelivery for PrivilegedActionCanceledDelayPeriodPayload {
    async fn validate_delivery(
        &self,
        _state: &NotificationValidationState,
        _: &NotificationCompositeKey,
    ) -> bool {
        true
    }
}

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
                    && delay_and_notify_definition.status == DelayAndNotifyStatus::Pending;
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
                    && delay_and_notify_definition.status == DelayAndNotifyStatus::Pending;
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

        if let Ok(claim) = claim_result {
            if let InheritanceClaim::Pending(pending_claim) = claim {
                return OffsetDateTime::now_utc() < pending_claim.delay_end_time;
            }
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

        if let Ok(claim) = claim_result {
            if let InheritanceClaim::Pending(pending_claim) = claim {
                return OffsetDateTime::now_utc()
                    >= (pending_claim.delay_end_time - Duration::days(3));
            }
        }
        false
    }
}

#[async_trait]
impl ValidateNotificationDelivery for InheritanceClaimCanceledPayload {
    async fn validate_delivery(
        &self,
        _state: &NotificationValidationState,
        _: &NotificationCompositeKey,
    ) -> bool {
        true
    }
}

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

        if let Ok(claim) = claim_result {
            if let InheritanceClaim::Pending(pending_claim) = claim {
                return OffsetDateTime::now_utc() >= pending_claim.delay_end_time;
            }
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
