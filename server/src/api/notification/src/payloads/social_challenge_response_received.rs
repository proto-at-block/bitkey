use std::collections::HashMap;

use serde::{Deserialize, Serialize};

use crate::{
    clients::iterable::IterableCampaignType,
    email::EmailPayload,
    entities::NotificationCompositeKey,
    push::{AndroidChannelId, SNSPushPayload},
    sms::SmsPayload,
    NotificationError, NotificationMessage,
};

const TRUSTED_CONTACT_ALIAS_FIELD: &str = "trustedContactAlias";

#[derive(Deserialize, Serialize, Clone, Debug, Default)]
pub struct SocialChallengeResponseReceivedPayload {
    pub trusted_contact_alias: String,
}

impl
    TryFrom<(
        NotificationCompositeKey,
        SocialChallengeResponseReceivedPayload,
    )> for NotificationMessage
{
    type Error = NotificationError;

    fn try_from(
        v: (
            NotificationCompositeKey,
            SocialChallengeResponseReceivedPayload,
        ),
    ) -> Result<Self, Self::Error> {
        let (composite_key, payload) = v;
        let (account_id, _) = composite_key.clone();
        let message = format!(
            "{} has confirmed you've requested help recovering Bitkey to a new mobile phone. Please return to your Bitkey app to complete the recovery process.",
            payload.trusted_contact_alias
        );
        Ok(NotificationMessage {
            composite_key,
            account_id,
            email_payload: Some(EmailPayload::Iterable {
                campaign_type: IterableCampaignType::SocialChallengeResponseReceived,
                data_fields: HashMap::from([(
                    TRUSTED_CONTACT_ALIAS_FIELD.to_string(),
                    payload.trusted_contact_alias,
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
