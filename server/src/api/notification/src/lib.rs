use std::{collections::HashSet, fmt};

use account::error::AccountError;
use database::ddb::DatabaseError;
use derive_builder::Builder;
use errors::ApiError;
use payloads::{
    comms_verification::CommsVerificationPayload,
    inheritance_claim_canceled::InheritanceClaimCanceledPayload,
    inheritance_claim_period_completed::InheritanceClaimPeriodCompletedPayload,
    inheritance_claim_period_initiated::InheritanceClaimPeriodInitiatedPayload,
    payment::PendingPaymentPayload,
    privileged_action_canceled_delay_period::PrivilegedActionCanceledDelayPeriodPayload,
    privileged_action_completed_delay_period::PrivilegedActionCompletedDelayPeriodPayload,
    privileged_action_pending_delay_period::PrivilegedActionPendingDelayPeriodPayload,
    push_blast::PushBlastPayload,
    recovery_canceled_delay_period::RecoveryCanceledDelayPeriodPayload,
    recovery_relationship_benefactor_invitation_pending::RecoveryRelationshipBenefactorInvitationPendingPayload,
    recovery_relationship_deleted::RecoveryRelationshipDeletedPayload,
    recovery_relationship_invitation_accepted::RecoveryRelationshipInvitationAcceptedPayload,
    social_challenge_response_received::SocialChallengeResponseReceivedPayload,
};
use queue::sqs::QueueError;
use serde::{Deserialize, Serialize};
use sms::SmsPayload;
use strum::ParseError;
use strum_macros::{Display as StrumDisplay, EnumString};
use thiserror::Error;
use types::notification::NotificationCategory;
use ulid::DecodeError;

use self::{
    email::EmailPayload,
    payloads::{
        payment::ConfirmedPaymentPayload,
        recovery_completed_delay_period::RecoveryCompletedDelayPeriodPayload,
        recovery_pending_delay_period::RecoveryPendingDelayPeriodPayload,
        test_notification::TestNotificationPayload,
    },
    push::SNSPushPayload,
};
use entities::NotificationCompositeKey;
use types::{account::identifiers::AccountId, notification::NotificationChannel};

#[allow(clippy::all)]
pub mod definitions {
    include!("definitions/build.wallet.navigation.v1.rs");
}

pub mod address_repo;
pub mod clients;
pub mod email;
pub mod entities;
pub mod identifiers;
pub mod metrics;
pub mod payloads;
pub mod push;
pub mod repository;
pub mod routes;
pub mod schedule;
pub mod service;
pub mod sms;

pub const PUSH_QUEUE_ENV_VAR: &str = "PUSH_QUEUE_URL";
pub const EMAIL_QUEUE_ENV_VAR: &str = "EMAIL_QUEUE_URL";
pub const SMS_QUEUE_ENV_VAR: &str = "SMS_QUEUE_URL";

#[derive(Error, Debug)]
pub enum NotificationError {
    #[error(transparent)]
    AccountError(#[from] AccountError),
    #[error("Invalid payload for notification with type {0}")]
    InvalidPayload(NotificationPayloadType),
    #[error("Failed to persist changes {0}")]
    PersistanceError(#[from] DatabaseError),
    #[error("Unable to format OffsetDateTime due to error {0}")]
    FormatOffsetDateTime(#[from] time::error::Format),
    #[error("Unable to parse format description due to error {0}")]
    ParseFormatDescription(#[from] time::error::InvalidFormatDescription),
    #[error("Failed to parse Notification Identifier")]
    ParseIdentifier,
    #[error("Failed to parse Ulid")]
    ParseUlid(#[from] DecodeError),
    #[error("Failed to parse Enum")]
    ParseEnum(#[from] ParseError),
    #[error(transparent)]
    SerdeJson(#[from] serde_json::Error),
    #[error(transparent)]
    Queue(#[from] QueueError),
}

impl From<NotificationError> for ApiError {
    fn from(value: NotificationError) -> Self {
        let message = value.to_string();
        match value {
            NotificationError::InvalidPayload(_)
            | NotificationError::PersistanceError(_)
            | NotificationError::FormatOffsetDateTime(_)
            | NotificationError::ParseFormatDescription(_)
            | NotificationError::ParseIdentifier
            | NotificationError::ParseEnum(_)
            | NotificationError::SerdeJson(_)
            | NotificationError::ParseUlid(_) => ApiError::GenericInternalApplicationError(message),
            NotificationError::AccountError(e) => e.into(),
            NotificationError::Queue(e) => e.into(),
        }
    }
}

#[derive(
    Deserialize, StrumDisplay, EnumString, Serialize, Clone, Debug, PartialEq, Eq, Hash, Copy,
)]
#[serde(rename_all = "snake_case")]
#[strum(serialize_all = "snake_case")]
pub enum NotificationPayloadType {
    TestPushNotification,
    RecoveryPendingDelayPeriod,
    RecoveryCompletedDelayPeriod,
    RecoveryCanceledDelayPeriod,
    ConfirmedPaymentNotification,
    CommsVerification,
    RecoveryRelationshipInvitationAccepted,
    RecoveryRelationshipDeleted,
    SocialChallengeResponseReceived,
    PushBlast,
    PendingPaymentNotification,
    PrivilegedActionCanceledDelayPeriod,
    PrivilegedActionCompletedDelayPeriod,
    PrivilegedActionPendingDelayPeriod,
    InheritanceClaimPeriodInitiated,
    InheritanceClaimCanceled,
    InheritanceClaimPeriodCompleted,
    RecoveryRelationshipBenefactorInvitationPending,
}

impl From<NotificationPayloadType> for NotificationCategory {
    fn from(value: NotificationPayloadType) -> Self {
        match value {
            NotificationPayloadType::CommsVerification
            | NotificationPayloadType::RecoveryCanceledDelayPeriod
            | NotificationPayloadType::RecoveryCompletedDelayPeriod
            | NotificationPayloadType::RecoveryPendingDelayPeriod
            | NotificationPayloadType::RecoveryRelationshipDeleted
            | NotificationPayloadType::RecoveryRelationshipInvitationAccepted
            | NotificationPayloadType::SocialChallengeResponseReceived
            | NotificationPayloadType::PushBlast
            | NotificationPayloadType::TestPushNotification
            | NotificationPayloadType::PrivilegedActionCanceledDelayPeriod
            | NotificationPayloadType::PrivilegedActionCompletedDelayPeriod
            | NotificationPayloadType::PrivilegedActionPendingDelayPeriod
            | NotificationPayloadType::InheritanceClaimPeriodInitiated
            | NotificationPayloadType::InheritanceClaimCanceled
            | NotificationPayloadType::InheritanceClaimPeriodCompleted
            | NotificationPayloadType::RecoveryRelationshipBenefactorInvitationPending => {
                NotificationCategory::AccountSecurity
            }
            NotificationPayloadType::ConfirmedPaymentNotification
            | NotificationPayloadType::PendingPaymentNotification => {
                NotificationCategory::MoneyMovement
            }
        }
    }
}

impl NotificationPayloadType {
    fn filter_payload(
        &self,
        payload: &NotificationPayload,
    ) -> Result<NotificationPayload, NotificationError> {
        let mut builder = NotificationPayloadBuilder::default();
        let valid_payload = match self {
            NotificationPayloadType::TestPushNotification => {
                builder.test_notification_payload(payload.test_notification_payload.clone());
                payload.test_notification_payload.is_some()
            }
            NotificationPayloadType::RecoveryPendingDelayPeriod => {
                builder.recovery_pending_delay_period_payload(
                    payload.recovery_pending_delay_period_payload.clone(),
                );
                payload.recovery_pending_delay_period_payload.is_some()
            }
            NotificationPayloadType::RecoveryCompletedDelayPeriod => {
                builder.recovery_completed_delay_period_payload(
                    payload.recovery_completed_delay_period_payload.clone(),
                );
                payload.recovery_completed_delay_period_payload.is_some()
            }
            NotificationPayloadType::RecoveryCanceledDelayPeriod => {
                builder.recovery_canceled_delay_period_payload(
                    payload.recovery_canceled_delay_period_payload.clone(),
                );
                payload.recovery_canceled_delay_period_payload.is_some()
            }
            NotificationPayloadType::ConfirmedPaymentNotification => {
                builder.confirmed_payment_payload(payload.confirmed_payment_payload.clone());
                payload.confirmed_payment_payload.is_some()
            }
            NotificationPayloadType::CommsVerification => {
                builder.comms_verification_payload(payload.comms_verification_payload.clone());
                payload.comms_verification_payload.is_some()
            }
            NotificationPayloadType::RecoveryRelationshipInvitationAccepted => {
                builder.recovery_relationship_invitation_accepted_payload(
                    payload
                        .recovery_relationship_invitation_accepted_payload
                        .clone(),
                );
                payload
                    .recovery_relationship_invitation_accepted_payload
                    .is_some()
            }
            NotificationPayloadType::RecoveryRelationshipDeleted => {
                builder.recovery_relationship_deleted_payload(
                    payload.recovery_relationship_deleted_payload.clone(),
                );
                payload.recovery_relationship_deleted_payload.is_some()
            }
            NotificationPayloadType::SocialChallengeResponseReceived => {
                builder.social_challenge_response_received_payload(
                    payload.social_challenge_response_received_payload.clone(),
                );
                payload.social_challenge_response_received_payload.is_some()
            }
            NotificationPayloadType::PushBlast => {
                builder.push_blast_payload(payload.push_blast_payload.clone());
                payload.push_blast_payload.is_some()
            }
            NotificationPayloadType::PendingPaymentNotification => {
                builder.pending_payment_payload(payload.pending_payment_payload.clone());
                payload.pending_payment_payload.is_some()
            }
            NotificationPayloadType::PrivilegedActionCanceledDelayPeriod => {
                builder.privileged_action_canceled_delay_period_payload(
                    payload
                        .privileged_action_canceled_delay_period_payload
                        .clone(),
                );
                payload
                    .privileged_action_canceled_delay_period_payload
                    .is_some()
            }
            NotificationPayloadType::PrivilegedActionCompletedDelayPeriod => {
                builder.privileged_action_completed_delay_period_payload(
                    payload
                        .privileged_action_completed_delay_period_payload
                        .clone(),
                );
                payload
                    .privileged_action_completed_delay_period_payload
                    .is_some()
            }
            NotificationPayloadType::PrivilegedActionPendingDelayPeriod => {
                builder.privileged_action_pending_delay_period_payload(
                    payload
                        .privileged_action_pending_delay_period_payload
                        .clone(),
                );
                payload
                    .privileged_action_pending_delay_period_payload
                    .is_some()
            }
            NotificationPayloadType::InheritanceClaimPeriodInitiated => {
                builder.inheritance_claim_period_initiated_payload(
                    payload.inheritance_claim_period_initiated_payload.clone(),
                );
                payload.inheritance_claim_period_initiated_payload.is_some()
            }
            NotificationPayloadType::InheritanceClaimCanceled => {
                builder.inheritance_claim_canceled_payload(
                    payload.inheritance_claim_canceled_payload.clone(),
                );
                payload.inheritance_claim_canceled_payload.is_some()
            }
            NotificationPayloadType::InheritanceClaimPeriodCompleted => {
                builder.inheritance_claim_period_completed_payload(
                    payload.inheritance_claim_period_completed_payload.clone(),
                );
                payload.inheritance_claim_period_completed_payload.is_some()
            }
            NotificationPayloadType::RecoveryRelationshipBenefactorInvitationPending => {
                builder.recovery_relationship_benefactor_invitation_pending_payload(
                    payload
                        .recovery_relationship_benefactor_invitation_pending_payload
                        .clone(),
                );
                payload
                    .recovery_relationship_benefactor_invitation_pending_payload
                    .is_some()
            }
        };
        if valid_payload {
            builder
                .build()
                .map_err(|_| NotificationError::InvalidPayload(self.to_owned()))
        } else {
            Err(NotificationError::InvalidPayload(self.to_owned()))
        }
    }
}

#[derive(Deserialize, StrumDisplay, EnumString, Serialize, Clone, Debug, Copy, PartialEq)]
#[serde(rename_all = "UPPERCASE")]
#[strum(serialize_all = "UPPERCASE")]
pub enum DeliveryStatus {
    New,
    Enqueued,
    Completed,
    Error,
}

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct NotificationMessage {
    pub composite_key: NotificationCompositeKey,
    pub account_id: AccountId,
    pub email_payload: Option<EmailPayload>,
    pub push_payload: Option<SNSPushPayload>,
    pub sms_payload: Option<SmsPayload>,
}

impl fmt::Display for NotificationMessage {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(
            f,
            "{:?} <{}, {:?}, {:?}>",
            self.composite_key, self.account_id, self.email_payload, self.push_payload
        )
    }
}

impl
    TryFrom<(
        NotificationCompositeKey,
        NotificationPayloadType,
        NotificationPayload,
    )> for NotificationMessage
{
    type Error = NotificationError;

    fn try_from(
        value: (
            NotificationCompositeKey,
            NotificationPayloadType,
            NotificationPayload,
        ),
    ) -> Result<Self, Self::Error> {
        let (composite_key, payload_type, payload) = value;

        match payload_type {
            NotificationPayloadType::CommsVerification => NotificationMessage::try_from((
                composite_key,
                payload
                    .comms_verification_payload
                    .ok_or(NotificationError::InvalidPayload(payload_type))?,
            )),
            NotificationPayloadType::ConfirmedPaymentNotification => {
                NotificationMessage::try_from((
                    composite_key,
                    payload
                        .confirmed_payment_payload
                        .ok_or(NotificationError::InvalidPayload(payload_type))?,
                ))
            }
            NotificationPayloadType::RecoveryCanceledDelayPeriod => {
                NotificationMessage::try_from((
                    composite_key,
                    payload
                        .recovery_canceled_delay_period_payload
                        .ok_or(NotificationError::InvalidPayload(payload_type))?,
                ))
            }
            NotificationPayloadType::RecoveryCompletedDelayPeriod => {
                NotificationMessage::try_from((
                    composite_key,
                    payload
                        .recovery_completed_delay_period_payload
                        .ok_or(NotificationError::InvalidPayload(payload_type))?,
                ))
            }
            NotificationPayloadType::RecoveryPendingDelayPeriod => NotificationMessage::try_from((
                composite_key,
                payload
                    .recovery_pending_delay_period_payload
                    .ok_or(NotificationError::InvalidPayload(payload_type))?,
            )),
            NotificationPayloadType::TestPushNotification => NotificationMessage::try_from((
                composite_key,
                payload
                    .test_notification_payload
                    .ok_or(NotificationError::InvalidPayload(payload_type))?,
            )),
            NotificationPayloadType::RecoveryRelationshipInvitationAccepted => {
                NotificationMessage::try_from((
                    composite_key,
                    payload
                        .recovery_relationship_invitation_accepted_payload
                        .ok_or(NotificationError::InvalidPayload(payload_type))?,
                ))
            }
            NotificationPayloadType::RecoveryRelationshipDeleted => {
                NotificationMessage::try_from((
                    composite_key,
                    payload
                        .recovery_relationship_deleted_payload
                        .ok_or(NotificationError::InvalidPayload(payload_type))?,
                ))
            }
            NotificationPayloadType::SocialChallengeResponseReceived => {
                NotificationMessage::try_from((
                    composite_key,
                    payload
                        .social_challenge_response_received_payload
                        .ok_or(NotificationError::InvalidPayload(payload_type))?,
                ))
            }
            NotificationPayloadType::PushBlast => NotificationMessage::try_from((
                composite_key,
                payload
                    .push_blast_payload
                    .ok_or(NotificationError::InvalidPayload(payload_type))?,
            )),
            NotificationPayloadType::PendingPaymentNotification => NotificationMessage::try_from((
                composite_key,
                payload
                    .pending_payment_payload
                    .ok_or(NotificationError::InvalidPayload(payload_type))?,
            )),
            NotificationPayloadType::PrivilegedActionCanceledDelayPeriod => {
                NotificationMessage::try_from((
                    composite_key,
                    payload
                        .privileged_action_canceled_delay_period_payload
                        .ok_or(NotificationError::InvalidPayload(payload_type))?,
                ))
            }
            NotificationPayloadType::PrivilegedActionCompletedDelayPeriod => {
                NotificationMessage::try_from((
                    composite_key,
                    payload
                        .privileged_action_completed_delay_period_payload
                        .ok_or(NotificationError::InvalidPayload(payload_type))?,
                ))
            }
            NotificationPayloadType::PrivilegedActionPendingDelayPeriod => {
                NotificationMessage::try_from((
                    composite_key,
                    payload
                        .privileged_action_pending_delay_period_payload
                        .ok_or(NotificationError::InvalidPayload(payload_type))?,
                ))
            }
            NotificationPayloadType::InheritanceClaimPeriodInitiated => {
                NotificationMessage::try_from((
                    composite_key,
                    payload
                        .inheritance_claim_period_initiated_payload
                        .ok_or(NotificationError::InvalidPayload(payload_type))?,
                ))
            }
            NotificationPayloadType::InheritanceClaimCanceled => NotificationMessage::try_from((
                composite_key,
                payload
                    .inheritance_claim_canceled_payload
                    .ok_or(NotificationError::InvalidPayload(payload_type))?,
            )),
            NotificationPayloadType::InheritanceClaimPeriodCompleted => {
                NotificationMessage::try_from((
                    composite_key,
                    payload
                        .inheritance_claim_period_completed_payload
                        .ok_or(NotificationError::InvalidPayload(payload_type))?,
                ))
            }
            NotificationPayloadType::RecoveryRelationshipBenefactorInvitationPending => {
                NotificationMessage::try_from((
                    composite_key,
                    payload
                        .recovery_relationship_benefactor_invitation_pending_payload
                        .ok_or(NotificationError::InvalidPayload(payload_type))?,
                ))
            }
        }
    }
}

impl NotificationMessage {
    pub fn channels(&self) -> HashSet<NotificationChannel> {
        let mut channels = HashSet::new();
        if self.email_payload.is_some() {
            channels.insert(NotificationChannel::Email);
        }
        if self.push_payload.is_some() {
            channels.insert(NotificationChannel::Push);
        }
        if self.sms_payload.is_some() {
            channels.insert(NotificationChannel::Sms);
        }
        channels
    }
}

#[derive(Default, Builder, Deserialize, Serialize, Clone, Debug)]
#[builder(default)]
pub struct NotificationPayload {
    #[serde(default)]
    pub test_notification_payload: Option<TestNotificationPayload>,
    #[serde(default)]
    pub recovery_pending_delay_period_payload: Option<RecoveryPendingDelayPeriodPayload>,
    #[serde(default)]
    pub recovery_completed_delay_period_payload: Option<RecoveryCompletedDelayPeriodPayload>,
    #[serde(default)]
    pub recovery_canceled_delay_period_payload: Option<RecoveryCanceledDelayPeriodPayload>,
    #[serde(default)]
    pub confirmed_payment_payload: Option<ConfirmedPaymentPayload>,
    #[serde(default)]
    pub pending_payment_payload: Option<PendingPaymentPayload>,
    #[serde(default)]
    pub comms_verification_payload: Option<CommsVerificationPayload>,
    #[serde(default)]
    pub recovery_relationship_invitation_accepted_payload:
        Option<RecoveryRelationshipInvitationAcceptedPayload>,
    #[serde(default)]
    pub recovery_relationship_deleted_payload: Option<RecoveryRelationshipDeletedPayload>,
    #[serde(default)]
    pub social_challenge_response_received_payload: Option<SocialChallengeResponseReceivedPayload>,
    #[serde(default)]
    pub push_blast_payload: Option<PushBlastPayload>,
    #[serde(default)]
    pub privileged_action_canceled_delay_period_payload:
        Option<PrivilegedActionCanceledDelayPeriodPayload>,
    #[serde(default)]
    pub privileged_action_completed_delay_period_payload:
        Option<PrivilegedActionCompletedDelayPeriodPayload>,
    #[serde(default)]
    pub privileged_action_pending_delay_period_payload:
        Option<PrivilegedActionPendingDelayPeriodPayload>,
    #[serde(default)]
    pub inheritance_claim_period_initiated_payload: Option<InheritanceClaimPeriodInitiatedPayload>,
    #[serde(default)]
    pub inheritance_claim_canceled_payload: Option<InheritanceClaimCanceledPayload>,
    #[serde(default)]
    pub inheritance_claim_period_completed_payload: Option<InheritanceClaimPeriodCompletedPayload>,
    #[serde(default)]
    pub recovery_relationship_benefactor_invitation_pending_payload:
        Option<RecoveryRelationshipBenefactorInvitationPendingPayload>,
}
