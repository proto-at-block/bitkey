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
pub struct RecoveryRelationshipDeletedPayload {
    pub trusted_contact_alias: String,
}

impl TryFrom<(NotificationCompositeKey, RecoveryRelationshipDeletedPayload)>
    for NotificationMessage
{
    type Error = NotificationError;

    fn try_from(
        v: (NotificationCompositeKey, RecoveryRelationshipDeletedPayload),
    ) -> Result<Self, Self::Error> {
        let (composite_key, payload) = v;
        let (account_id, _) = composite_key.clone();
        let message = format!(
            "{} has been removed as one of your trusted contacts. You can manage your trusted contacts from your Bitkey app if you want to replace them.",
            payload.trusted_contact_alias
        );
        Ok(NotificationMessage {
            composite_key,
            account_id,
            email_payload: Some(EmailPayload::Iterable {
                campaign_type: IterableCampaignType::RecoveryRelationshipDeleted,
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
            sms_payload: Some(SmsPayload {
                message,
                unsupported_country_codes: None,
            }),
        })
    }
}
