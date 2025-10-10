use serde::{Deserialize, Serialize};
use time::{serde::rfc3339, OffsetDateTime};
use types::{
    account::AccountType,
    privileged_action::{
        definition::{AuthorizationStrategyDefinition, PrivilegedActionDefinition},
        shared::{PrivilegedActionInstanceId, PrivilegedActionType},
    },
};

use crate::{
    email::EmailPayload,
    entities::NotificationCompositeKey,
    payloads::privileged_action_notification_builder::{
        DelayAndNotifyNotificationConfig, DelayNotifyEmailDataFields, DelayNotifyStatus,
    },
    push::{AndroidChannelId, SNSPushPayload},
    sms::SmsPayload,
    NotificationError, NotificationMessage, NotificationPayloadType,
};

use super::format_duration;

#[derive(Deserialize, Serialize, Clone, Debug)]
pub struct PrivilegedActionPendingDelayPeriodPayload {
    pub privileged_action_instance_id: PrivilegedActionInstanceId,
    pub account_type: AccountType,
    pub privileged_action_type: PrivilegedActionType,
    #[serde(with = "rfc3339")]
    pub delay_end_time: OffsetDateTime,
}

impl
    TryFrom<(
        NotificationCompositeKey,
        PrivilegedActionPendingDelayPeriodPayload,
    )> for NotificationMessage
{
    type Error = NotificationError;

    fn try_from(
        v: (
            NotificationCompositeKey,
            PrivilegedActionPendingDelayPeriodPayload,
        ),
    ) -> Result<Self, Self::Error> {
        let (composite_key, payload) = v;
        let (account_id, _) = composite_key.clone();
        let duration = payload.delay_end_time - OffsetDateTime::now_utc();

        let formatted_duration = format_duration(duration);

        let privileged_action_type = payload.privileged_action_type;
        let definition: PrivilegedActionDefinition = privileged_action_type.clone().into();
        let notification_summary = definition
            .authorization_strategies
            .get(&payload.account_type)
            .and_then(|s| match s {
                AuthorizationStrategyDefinition::DelayAndNotify(d) => {
                    Some(d.notification_summary.clone())
                }
                _ => None,
            })
            .ok_or_else(|| {
                NotificationError::InvalidPayload(
                    NotificationPayloadType::PrivilegedActionCompletedDelayPeriod,
                )
            })?;

        let message = format!(
            "Your request to {} will be ready to be completed in {}. If you didn't request this, please cancel immediately in your Bitkey app.",
            notification_summary,
            formatted_duration,
        );
        let email_data_fields = DelayNotifyEmailDataFields::new(
            DelayNotifyStatus::Pending(formatted_duration),
            notification_summary,
        );

        let config = DelayAndNotifyNotificationConfig::from(privileged_action_type);

        Ok(NotificationMessage {
            composite_key,
            account_id,
            email_payload: Some(EmailPayload::Iterable {
                campaign_type: config.campaign_types.pending,
                data_fields: email_data_fields.into(),
            }),
            push_payload: Some(SNSPushPayload {
                message: message.clone(),
                android_channel_id: AndroidChannelId::General, // TODO: should we make a new channel?
                ..Default::default()
            }),
            sms_payload: Some(SmsPayload::new(message, None)),
        })
    }
}

#[cfg(test)]
mod tests {
    use time::{Duration, OffsetDateTime};
    use types::account::identifiers::AccountId;
    use types::account::AccountType;
    use types::privileged_action::shared::{PrivilegedActionInstanceId, PrivilegedActionType};

    use crate::identifiers::NotificationId;
    use crate::payloads::privileged_action_pending_delay_period::PrivilegedActionPendingDelayPeriodPayload;
    use crate::NotificationMessage;

    #[test]
    fn test_notification_message_push_payload() {
        // Push notification at the start of the delay window
        let start_time = OffsetDateTime::now_utc();
        let end_time = start_time + Duration::days(1) + Duration::minutes(1);
        let privileged_action_instance_id =
            PrivilegedActionInstanceId::gen().expect("Valid PrivilegedActionInstanceId");
        let composite_key = (
            AccountId::gen().expect("Valid AccountId"),
            NotificationId::gen_scheduled(),
        );
        let payload = PrivilegedActionPendingDelayPeriodPayload {
            privileged_action_instance_id: privileged_action_instance_id.clone(),
            account_type: AccountType::Software,
            privileged_action_type: PrivilegedActionType::ActivateTouchpoint,
            delay_end_time: end_time,
        };

        let notification_message: NotificationMessage =
            (composite_key.clone(), payload).try_into().unwrap();
        let push_payload = notification_message.push_payload.unwrap();
        assert!(push_payload.message.contains("1 day"));

        // Push notification at the middle of the delay window
        let start_time = OffsetDateTime::now_utc();
        let end_time = start_time + Duration::hours(12) + Duration::minutes(1);
        let payload = PrivilegedActionPendingDelayPeriodPayload {
            privileged_action_instance_id,
            account_type: AccountType::Software,
            privileged_action_type: PrivilegedActionType::ActivateTouchpoint,
            delay_end_time: end_time,
        };

        let notification_message: NotificationMessage =
            (composite_key, payload).try_into().unwrap();
        let push_payload = notification_message.push_payload.unwrap();
        assert!(push_payload.message.contains("12 hours"));
    }
}
