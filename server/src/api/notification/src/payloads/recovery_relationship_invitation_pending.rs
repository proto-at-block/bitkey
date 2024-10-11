use serde::{Deserialize, Serialize};
use types::recovery::social::relationship::RecoveryRelationshipId;

use crate::{
    entities::NotificationCompositeKey,
    push::{AndroidChannelId, SNSPushPayload},
    sms::SmsPayload,
    NotificationError, NotificationMessage,
};

#[derive(Deserialize, Serialize, Clone, Debug)]
pub struct RecoveryRelationshipInvitationPendingPayload {
    pub recovery_relationship_id: RecoveryRelationshipId,
    pub trusted_contact_alias: String,
}

impl
    TryFrom<(
        NotificationCompositeKey,
        RecoveryRelationshipInvitationPendingPayload,
    )> for NotificationMessage
{
    type Error = NotificationError;

    fn try_from(
        v: (
            NotificationCompositeKey,
            RecoveryRelationshipInvitationPendingPayload,
        ),
    ) -> Result<Self, Self::Error> {
        let (composite_key, payload) = v;
        let (account_id, _) = composite_key.clone();

        let message = format!(
            "{} hasn't accepted your invite. Resend to nudge them.",
            payload.trusted_contact_alias
        );

        Ok(NotificationMessage {
            composite_key,
            account_id,
            email_payload: None, // TODO W-9787
            push_payload: Some(SNSPushPayload {
                message: message.clone(),
                android_channel_id: AndroidChannelId::UrgentSecurity,
                ..Default::default()
            }),
            sms_payload: Some(SmsPayload {
                message,
                unsupported_country_codes: None,
            }),
        })
    }
}
