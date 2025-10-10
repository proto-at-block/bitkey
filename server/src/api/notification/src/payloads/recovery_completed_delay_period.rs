use std::collections::HashMap;

use serde::{Deserialize, Serialize};
use time::{serde::rfc3339, OffsetDateTime};
use types::account::entities::Factor;

use crate::{
    clients::iterable::IterableCampaignType, email::EmailPayload,
    entities::NotificationCompositeKey, push::AndroidChannelId, push::SNSPushPayload,
    sms::SmsPayload, NotificationError, NotificationMessage,
};

const LOST_FACTOR_FIELD: &str = "lostFactor";

#[derive(Deserialize, Serialize, Clone, Debug)]
pub struct RecoveryCompletedDelayPeriodPayload {
    #[serde(with = "rfc3339")]
    pub initiation_time: OffsetDateTime,
    pub lost_factor: Factor,
}

impl
    TryFrom<(
        NotificationCompositeKey,
        RecoveryCompletedDelayPeriodPayload,
    )> for NotificationMessage
{
    type Error = NotificationError;

    fn try_from(
        v: (
            NotificationCompositeKey,
            RecoveryCompletedDelayPeriodPayload,
        ),
    ) -> Result<Self, Self::Error> {
        let (composite_key, payload) = v;
        let (account_id, _) = composite_key.clone();

        let message = match payload.lost_factor {
            Factor::App => "Your Bitkey wallet is ready to be recovered on your new phone. Please open your Bitkey app to complete your wallet recovery.".to_owned(),
            Factor::Hw => "Your replacement Bitkey hardware device is ready. To complete your wallet recovery, please open your Bitkey app.".to_owned(),
        };

        Ok(NotificationMessage {
            composite_key,
            account_id,
            email_payload: Some(EmailPayload::Iterable {
                campaign_type: match payload.lost_factor {
                    Factor::App => IterableCampaignType::RecoveryCompletedDelayPeriodLostApp,
                    Factor::Hw => IterableCampaignType::RecoveryCompletedDelayPeriodLostHw,
                },
                data_fields: HashMap::from([(
                    LOST_FACTOR_FIELD.to_string(),
                    payload.lost_factor.to_string(),
                )]),
            }),
            push_payload: Some(SNSPushPayload {
                message: message.clone(),
                android_channel_id: AndroidChannelId::RecoveryAccountSecurity,
                ..Default::default()
            }),
            sms_payload: Some(SmsPayload::new(message, None)),
        })
    }
}
