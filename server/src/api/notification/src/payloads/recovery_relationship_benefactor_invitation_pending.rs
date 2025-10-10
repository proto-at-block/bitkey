use std::collections::HashMap;

use serde::{Deserialize, Serialize};
use types::recovery::social::relationship::RecoveryRelationshipId;

use crate::{
    clients::iterable::IterableCampaignType,
    definitions::NavigationScreenId,
    email::EmailPayload,
    entities::NotificationCompositeKey,
    push::{AndroidChannelId, SNSPushPayload, SNSPushPayloadExtras},
    sms::SmsPayload,
    NotificationError, NotificationMessage,
};

const BENEFICIARY_ALIAS_FIELD: &str = "beneficiaryAlias";

#[derive(Deserialize, Serialize, Clone, Debug)]
pub struct RecoveryRelationshipBenefactorInvitationPendingPayload {
    pub recovery_relationship_id: RecoveryRelationshipId,
    pub trusted_contact_alias: String,
}

impl
    TryFrom<(
        NotificationCompositeKey,
        RecoveryRelationshipBenefactorInvitationPendingPayload,
    )> for NotificationMessage
{
    type Error = NotificationError;

    fn try_from(
        v: (
            NotificationCompositeKey,
            RecoveryRelationshipBenefactorInvitationPendingPayload,
        ),
    ) -> Result<Self, Self::Error> {
        let (composite_key, payload) = v;
        let (account_id, _) = composite_key.clone();

        let message =
            "Your beneficiary’s invite is still pending. Resend to give them a nudge.".to_string();

        Ok(NotificationMessage {
            composite_key,
            account_id,
            email_payload: Some(EmailPayload::Iterable {
                campaign_type:
                    IterableCampaignType::RecoveryRelationshipBenefactorInvitationPending,
                data_fields: HashMap::from([(
                    BENEFICIARY_ALIAS_FIELD.to_string(),
                    payload.trusted_contact_alias,
                )]),
            }),
            push_payload: Some(SNSPushPayload {
                message: message.clone(),
                android_channel_id: AndroidChannelId::UrgentSecurity,
                extras: SNSPushPayloadExtras {
                    navigate_to_screen_id: Some(
                        (NavigationScreenId::ManageInheritance as i32).to_string(),
                    ),
                    ..Default::default()
                },
                ..Default::default()
            }),
            sms_payload: Some(SmsPayload::new(message, None)),
        })
    }
}
