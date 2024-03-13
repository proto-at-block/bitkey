use async_trait::async_trait;
use error::NotificationValidationError;
use notification::{
    entities::NotificationCompositeKey,
    payloads::{
        comms_verification::CommsVerificationPayload, payment::PaymentPayload,
        push_blast::PushBlastPayload,
        recovery_canceled_delay_period::RecoveryCanceledDelayPeriodPayload,
        recovery_completed_delay_period::RecoveryCompletedDelayPeriodPayload,
        recovery_pending_delay_period::RecoveryPendingDelayPeriodPayload,
        recovery_relationship_deleted::RecoveryRelationshipDeletedPayload,
        recovery_relationship_invitation_accepted::RecoveryRelationshipInvitationAcceptedPayload,
        social_challenge_response_received::SocialChallengeResponseReceivedPayload,
        test_notification::TestNotificationPayload,
    },
    NotificationPayload, NotificationPayloadType,
};
use recovery::{entities::RecoveryStatus, repository::Repository as RecoveryRepository};
use time::OffsetDateTime;

mod error;

#[derive(Clone)]
pub struct NotificationValidationState {
    recovery_service: RecoveryRepository,
}

impl NotificationValidationState {
    pub fn new(recovery_service: RecoveryRepository) -> Self {
        Self { recovery_service }
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
            NotificationPayloadType::PaymentNotification => payload
                .payment_payload
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
            .recovery_service
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
            .recovery_service
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
            .recovery_service
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
impl ValidateNotificationDelivery for PaymentPayload {
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
