use serde::{Deserialize, Serialize};
use time::{macros::format_description, serde::rfc3339, OffsetDateTime};
use types::recovery::{
    inheritance::claim::InheritanceClaimId, social::relationship::RecoveryRelationshipRole,
};

use crate::{
    entities::NotificationCompositeKey,
    push::{AndroidChannelId, SNSPushPayload},
    sms::SmsPayload,
    NotificationError, NotificationMessage,
};

#[derive(Deserialize, Serialize, Clone, Debug)]
pub struct InheritanceClaimPeriodInitiatedPayload {
    pub inheritance_claim_id: InheritanceClaimId,
    pub trusted_contact_alias: String,
    pub recipient_account_role: RecoveryRelationshipRole,
    #[serde(with = "rfc3339")]
    pub delay_end_time: OffsetDateTime,
}

impl
    TryFrom<(
        NotificationCompositeKey,
        InheritanceClaimPeriodInitiatedPayload,
    )> for NotificationMessage
{
    type Error = NotificationError;

    fn try_from(
        v: (
            NotificationCompositeKey,
            InheritanceClaimPeriodInitiatedPayload,
        ),
    ) -> Result<Self, Self::Error> {
        let (composite_key, payload) = v;
        let (account_id, _) = composite_key.clone();

        let (importance, message) = match payload.recipient_account_role {
            RecoveryRelationshipRole::ProtectedCustomer => {
                (AndroidChannelId::UrgentSecurity, format!(
                    "[Action required] {} has started an inheritance claim. Confirm you are active to decline the claim.",
                    payload.trusted_contact_alias
                ))
            }
            RecoveryRelationshipRole::TrustedContact => {
                (AndroidChannelId::RecoveryAccountSecurity, format!(
                    "Your inheritance claim has been initiated. Your claim period will end on {}. No further action needed.",
                    payload.delay_end_time.format(format_description!("[day] [month repr:short] [year]"))?,
                ))
            }
        };

        Ok(NotificationMessage {
            composite_key,
            account_id,
            email_payload: None, // TODO: W-9755,
            push_payload: Some(SNSPushPayload {
                message: message.clone(),
                android_channel_id: importance,
                ..Default::default()
            }),
            sms_payload: Some(SmsPayload {
                message,
                unsupported_country_codes: None,
            }),
        })
    }
}
