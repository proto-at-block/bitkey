use serde::{Deserialize, Serialize};

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
pub struct InheritanceClaimPeriodCompletedPayload {
    pub inheritance_claim_id: InheritanceClaimId,
    pub trusted_contact_alias: String,
    pub recipient_account_role: RecoveryRelationshipRole,
}

impl
    TryFrom<(
        NotificationCompositeKey,
        InheritanceClaimPeriodCompletedPayload,
    )> for NotificationMessage
{
    type Error = NotificationError;

    fn try_from(
        v: (
            NotificationCompositeKey,
            InheritanceClaimPeriodCompletedPayload,
        ),
    ) -> Result<Self, Self::Error> {
        let (composite_key, payload) = v;
        let (account_id, _) = composite_key.clone();

        let message = match payload.recipient_account_role {
            RecoveryRelationshipRole::ProtectedCustomer => {
                format!(
                    "Your inheritance claim is complete. Your funds will be transferred to {}. No further action is needed.",
                    payload.trusted_contact_alias
                )
            }
            RecoveryRelationshipRole::TrustedContact => {
                "Your claim period has ended. Your funds are processing and should be available soon.".to_string()
            }
        };

        Ok(NotificationMessage {
            composite_key,
            account_id,
            email_payload: None, // TODO: W-9782,
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
