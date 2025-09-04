use serde::{Deserialize, Serialize};
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

#[derive(Deserialize, Serialize, Clone, Debug)]
pub struct PrivilegedActionCompletedDelayPeriodPayload {
    pub privileged_action_instance_id: PrivilegedActionInstanceId,
    pub account_type: AccountType,
    pub privileged_action_type: PrivilegedActionType,
}

impl
    TryFrom<(
        NotificationCompositeKey,
        PrivilegedActionCompletedDelayPeriodPayload,
    )> for NotificationMessage
{
    type Error = NotificationError;

    fn try_from(
        v: (
            NotificationCompositeKey,
            PrivilegedActionCompletedDelayPeriodPayload,
        ),
    ) -> Result<Self, Self::Error> {
        let (composite_key, payload) = v;
        let (account_id, _) = composite_key.clone();

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

        let config = DelayAndNotifyNotificationConfig::from(privileged_action_type);
        let message = format!(
            "Your request to {} is ready to be completed. Please open your Bitkey app.",
            notification_summary,
        );
        let email_data_fields =
            DelayNotifyEmailDataFields::new(DelayNotifyStatus::Completed, notification_summary);

        Ok(NotificationMessage {
            composite_key,
            account_id,
            email_payload: Some(EmailPayload::Iterable {
                campaign_type: config.campaign_types.completed,
                data_fields: email_data_fields.into(),
            }),
            push_payload: Some(SNSPushPayload {
                message: message.clone(),
                android_channel_id: AndroidChannelId::General, // TODO: should we make a new channel?
                ..Default::default()
            }),
            sms_payload: Some(SmsPayload {
                message,
                unsupported_country_codes: None,
            }),
        })
    }
}
