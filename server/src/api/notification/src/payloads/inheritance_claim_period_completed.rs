use std::collections::HashMap;

use serde::{Deserialize, Serialize};

use types::recovery::{
    inheritance::claim::InheritanceClaimId, social::relationship::RecoveryRelationshipRole,
};

use crate::{
    clients::iterable::IterableCampaignType,
    definitions::NavigationScreenId,
    email::EmailPayload,
    entities::NotificationCompositeKey,
    push::{AndroidChannelId, SNSPushPayload, SNSPushPayloadExtras},
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

        let (message, campaign_type, extras) = match payload.recipient_account_role {
            RecoveryRelationshipRole::ProtectedCustomer => (
                "The inheritance claim period for your Bitkey wallet has ended.".to_string(),
                IterableCampaignType::InheritanceClaimPeriodCompletedAsBenefactor,
                SNSPushPayloadExtras::default(),
            ),
            RecoveryRelationshipRole::TrustedContact => (
                "Your inheritance funds are now available for transfer.".to_string(),
                IterableCampaignType::InheritanceClaimPeriodCompletedAsBeneficiary,
                SNSPushPayloadExtras {
                    inheritance_claim_id: Some(payload.inheritance_claim_id.to_string()),
                    navigate_to_screen_id: Some(
                        (NavigationScreenId::InheritanceCompleteClaim as i32).to_string(),
                    ),
                    ..Default::default()
                },
            ),
        };

        Ok(NotificationMessage {
            composite_key,
            account_id,
            email_payload: Some(EmailPayload::Iterable {
                campaign_type,
                data_fields: HashMap::new(),
            }),
            push_payload: Some(SNSPushPayload {
                message: message.clone(),
                android_channel_id: AndroidChannelId::RecoveryAccountSecurity,
                extras,
                ..Default::default()
            }),
            sms_payload: Some(SmsPayload {
                message,
                unsupported_country_codes: None,
            }),
        })
    }
}
