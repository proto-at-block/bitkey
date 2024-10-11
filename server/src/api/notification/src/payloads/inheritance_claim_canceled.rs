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
pub struct InheritanceClaimCanceledPayload {
    pub inheritance_claim_id: InheritanceClaimId,
    pub trusted_contact_alias: String,
    pub customer_alias: String,
    pub acting_account_role: RecoveryRelationshipRole,
    pub recipient_account_role: RecoveryRelationshipRole,
}

impl TryFrom<(NotificationCompositeKey, InheritanceClaimCanceledPayload)> for NotificationMessage {
    type Error = NotificationError;

    fn try_from(
        v: (NotificationCompositeKey, InheritanceClaimCanceledPayload),
    ) -> Result<Self, Self::Error> {
        let (composite_key, payload) = v;
        let (account_id, _) = composite_key.clone();

        let message = match (payload.acting_account_role, payload.recipient_account_role) {
            (RecoveryRelationshipRole::ProtectedCustomer, RecoveryRelationshipRole::ProtectedCustomer) => {
                "The claim has been closed. No further action is needed on your part to keep your funds safe.".to_string()
            }
            (RecoveryRelationshipRole::ProtectedCustomer, RecoveryRelationshipRole::TrustedContact) => {
                format!(
                    "{} confirmed they are active. Your inheritance case has been closed. No further action is needed.",
                    payload.customer_alias,
                )
            }
            (RecoveryRelationshipRole::TrustedContact, RecoveryRelationshipRole::ProtectedCustomer) => {
                format!("{} canceled an inheritance claim. No further action is needed on your part to keep your funds safe.", payload.trusted_contact_alias)
            }
            (RecoveryRelationshipRole::TrustedContact, RecoveryRelationshipRole::TrustedContact) => {
                format!(
                    "Your inheritance claim has been canceled. {} has been notified.", payload.customer_alias
                )
            }
        };

        Ok(NotificationMessage {
            composite_key,
            account_id,
            email_payload: None, // TODO: W-9770,
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
