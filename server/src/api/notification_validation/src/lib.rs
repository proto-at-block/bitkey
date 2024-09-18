use async_trait::async_trait;
use error::NotificationValidationError;
use notification::{
    entities::NotificationCompositeKey,
    payloads::{
        comms_verification::CommsVerificationPayload,
        payment::{ConfirmedPaymentPayload, PendingPaymentPayload},
        privileged_action_canceled_delay_period::PrivilegedActionCanceledDelayPeriodPayload,
        privileged_action_completed_delay_period::PrivilegedActionCompletedDelayPeriodPayload,
        privileged_action_pending_delay_period::PrivilegedActionPendingDelayPeriodPayload,
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
use repository::privileged_action::Repository as PrivilegedActionRepository;
use serde_json::Value;
use time::OffsetDateTime;
use types::privileged_action::repository::{AuthorizationStrategyRecord, DelayAndNotifyStatus};

mod error;

#[derive(Clone)]
pub struct NotificationValidationState {
    recovery_service: RecoveryRepository,
    privileged_action_repository: PrivilegedActionRepository,
}

impl NotificationValidationState {
    pub fn new(
        recovery_service: RecoveryRepository,
        privileged_action_repository: PrivilegedActionRepository,
    ) -> Self {
        Self {
            recovery_service,
            privileged_action_repository,
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
            match privileged_action_instance.authorization_strategy {
                AuthorizationStrategyRecord::DelayAndNotify(delay_and_notify_definition) => {
                    return privileged_action_instance.account_id == *account_id
                        && OffsetDateTime::now_utc() >= delay_and_notify_definition.delay_end_time
                        && delay_and_notify_definition.status == DelayAndNotifyStatus::Pending;
                }
                _ => {}
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
            match privileged_action_instance.authorization_strategy {
                AuthorizationStrategyRecord::DelayAndNotify(delay_and_notify_definition) => {
                    return privileged_action_instance.account_id == *account_id
                        && OffsetDateTime::now_utc() < delay_and_notify_definition.delay_end_time
                        && delay_and_notify_definition.status == DelayAndNotifyStatus::Pending;
                }
                _ => {}
            }
        }
        false
    }
}
