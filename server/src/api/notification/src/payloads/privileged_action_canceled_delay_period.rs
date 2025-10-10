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
pub struct PrivilegedActionCanceledDelayPeriodPayload {
    pub privileged_action_instance_id: PrivilegedActionInstanceId,
    pub account_type: AccountType,
    pub privileged_action_type: PrivilegedActionType,
}

impl
    TryFrom<(
        NotificationCompositeKey,
        PrivilegedActionCanceledDelayPeriodPayload,
    )> for NotificationMessage
{
    type Error = NotificationError;

    fn try_from(
        v: (
            NotificationCompositeKey,
            PrivilegedActionCanceledDelayPeriodPayload,
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
                    NotificationPayloadType::PrivilegedActionCanceledDelayPeriod,
                )
            })?;

        let config = DelayAndNotifyNotificationConfig::from(privileged_action_type);
        let message = format!(
            "Your request to {} has been canceled. If you didn't cancel this request, please return to your Bitkey app to take further action.",
            notification_summary,
        );
        let email_data_fields =
            DelayNotifyEmailDataFields::new(DelayNotifyStatus::Canceled, notification_summary);

        Ok(NotificationMessage {
            composite_key,
            account_id,
            email_payload: Some(EmailPayload::Iterable {
                campaign_type: config.campaign_types.canceled,
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
