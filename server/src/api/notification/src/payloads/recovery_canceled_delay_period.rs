use std::collections::HashMap;

use account::entities::Factor;
use serde::{Deserialize, Serialize};
use time::{serde::rfc3339, OffsetDateTime};

use crate::{
    clients::iterable::IterableCampaignType, email::EmailPayload,
    entities::NotificationCompositeKey, push::AndroidChannelId, push::SNSPushPayload,
    sms::SmsPayload, NotificationError, NotificationMessage,
};

const LOST_FACTOR_FIELD: &str = "lostFactor";

#[derive(Deserialize, Serialize, Clone, Debug)]
pub struct RecoveryCanceledDelayPeriodPayload {
    #[serde(with = "rfc3339")]
    pub initiation_time: OffsetDateTime,
    pub lost_factor: Factor,
}

impl TryFrom<(NotificationCompositeKey, RecoveryCanceledDelayPeriodPayload)>
    for NotificationMessage
{
    type Error = NotificationError;

    fn try_from(
        v: (NotificationCompositeKey, RecoveryCanceledDelayPeriodPayload),
    ) -> Result<Self, Self::Error> {
        let (composite_key, payload) = v;
        let (account_id, _) = composite_key.clone();

        let message = match payload.lost_factor {
            Factor::App => "Your mobile recovery request has been canceled. If you didn't cancel this request, please return to your Bitkey app to take further action.".to_owned(),
            Factor::Hw => "Your Bitkey hardware device recovery has been canceled. If you didn't cancel this, please open to the Bitkey app to take further action.".to_owned(),
        };

        Ok(NotificationMessage {
            composite_key,
            account_id,
            email_payload: Some(EmailPayload::Iterable {
                campaign_type: match payload.lost_factor {
                    Factor::App => IterableCampaignType::RecoveryCanceledDelayPeriodLostApp,
                    Factor::Hw => IterableCampaignType::RecoveryCanceledDelayPeriodLostHw,
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
            sms_payload: Some(SmsPayload {
                message,
                unsupported_country_codes: None,
            }),
        })
    }
}
