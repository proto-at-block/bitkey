use std::collections::HashMap;

use serde::{Deserialize, Serialize};
use time::{serde::rfc3339, OffsetDateTime};
use types::notification::NotificationsTriggerType;

use crate::{
    clients::iterable::IterableCampaignType,
    definitions::NavigationScreenId,
    email::EmailPayload,
    entities::NotificationCompositeKey,
    push::{AndroidChannelId, SNSPushPayload, SNSPushPayloadExtras},
    sms::SmsPayload,
    NotificationError, NotificationMessage,
};

const SUBJECT_FIELD: &str = "subject";
const HEADER_FIELD: &str = "header";
const BODY_FIELD: &str = "body";

#[derive(Deserialize, Serialize, Clone, Debug)]
pub struct SecurityHubPayload {
    #[serde(with = "rfc3339")]
    pub created_at: OffsetDateTime,
    pub trigger_type: NotificationsTriggerType,
}

impl TryFrom<(NotificationCompositeKey, SecurityHubPayload)> for NotificationMessage {
    type Error = NotificationError;

    fn try_from(v: (NotificationCompositeKey, SecurityHubPayload)) -> Result<Self, Self::Error> {
        let (composite_key, payload) = v;
        let (account_id, _) = composite_key.clone();

        let (subject, header, body) = email_message(&payload.trigger_type);

        Ok(NotificationMessage {
            composite_key,
            account_id,
            email_payload: Some(EmailPayload::Iterable {
                campaign_type: IterableCampaignType::SecurityHub,
                data_fields: HashMap::from([
                    (SUBJECT_FIELD.to_string(), subject),
                    (HEADER_FIELD.to_string(), header),
                    (BODY_FIELD.to_string(), body),
                ]),
            }),
            push_payload: Some(SNSPushPayload {
                message: push_message(&payload.trigger_type),
                android_channel_id: AndroidChannelId::RecoveryAccountSecurity,
                extras: SNSPushPayloadExtras {
                    navigate_to_screen_id: Some(
                        (NavigationScreenId::SecurityHub as i32).to_string(),
                    ),
                    ..Default::default()
                },
                ..Default::default()
            }),
            sms_payload: Some(SmsPayload::new(sms_message(&payload.trigger_type), None)),
        })
    }
}

fn push_message(trigger_type: &NotificationsTriggerType) -> String {
    match trigger_type {
        NotificationsTriggerType::SecurityHubWalletAtRisk => {
            "Your wallet is currently at risk.".to_string()
        }
    }
}

fn sms_message(trigger_type: &NotificationsTriggerType) -> String {
    match trigger_type {
        NotificationsTriggerType::SecurityHubWalletAtRisk => {
            "[Action required] Your Bitkey wallet needs attention to ensure your funds are secure. Open the Bitkey app for more information.".to_string()
        }
    }
}

fn email_message(trigger_type: &NotificationsTriggerType) -> (String, String, String) {
    match trigger_type {
        NotificationsTriggerType::SecurityHubWalletAtRisk => {
            (
                "[Action Required] Your Bitkey wallet needs attention".to_string(),
                "Your Bitkey wallet needs attention".to_string(),
                "Your Bitkey wallet needs attention to ensure your funds are secure. Open the Bitkey app for more information.".to_string(),
            )
        }
    }
}
