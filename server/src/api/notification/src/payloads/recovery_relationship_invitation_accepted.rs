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
pub struct RecoveryRelationshipInvitationAcceptedPayload {
    pub trusted_contact_alias: String,
}

impl
    TryFrom<(
        NotificationCompositeKey,
        RecoveryRelationshipInvitationAcceptedPayload,
    )> for NotificationMessage
{
    type Error = NotificationError;

    fn try_from(
        v: (
            NotificationCompositeKey,
            RecoveryRelationshipInvitationAcceptedPayload,
        ),
    ) -> Result<Self, Self::Error> {
        let (composite_key, payload) = v;
        let (account_id, _) = composite_key.clone();
        let message = format!(
            "{} is now set up as a trusted contact for your Bitkey. They can assist if you ever need help recovering a part of your Bitkey wallet.",
            payload.trusted_contact_alias
        );
        Ok(NotificationMessage {
            composite_key,
            account_id,
            email_payload: Some(EmailPayload::Iterable {
                campaign_type: IterableCampaignType::RecoveryRelationshipInvitationAccepted,
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
