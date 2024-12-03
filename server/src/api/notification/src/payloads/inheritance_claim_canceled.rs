use std::collections::HashMap;

use serde::{Deserialize, Serialize};

use types::recovery::{
    inheritance::claim::InheritanceClaimId, social::relationship::RecoveryRelationshipRole,
};

use crate::{
    clients::iterable::IterableCampaignType,
    email::EmailPayload,
    entities::NotificationCompositeKey,
    push::{AndroidChannelId, SNSPushPayload},
    sms::SmsPayload,
    NotificationError, NotificationMessage,
};

const BENEFACTOR_ALIAS_FIELD: &str = "benefactorAlias";
const BENEFICIARY_ALIAS_FIELD: &str = "beneficiaryAlias";

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

        let (message, campaign_type) = match (payload.acting_account_role, payload.recipient_account_role) {
            (
                RecoveryRelationshipRole::ProtectedCustomer,
                RecoveryRelationshipRole::ProtectedCustomer,
            ) => (
                "The inheritance claim for your Bitkey wallet is now closed. You will retain full control of your wallet and funds.".to_string(),
                IterableCampaignType::InheritanceClaimCanceledByBenefactorReceivedByBenefactor,
            ),
            (
                RecoveryRelationshipRole::ProtectedCustomer,
                RecoveryRelationshipRole::TrustedContact,
            ) => (
                "Your inheritance claim has been declined.".to_string(),
                IterableCampaignType::InheritanceClaimCanceledByBenefactorReceivedByBeneficiary,
            ),
            (
                RecoveryRelationshipRole::TrustedContact,
                RecoveryRelationshipRole::ProtectedCustomer,
            ) => (
                "An inheritance claim for your Bitkey wallet has been canceled. You will retain full control of your wallet and funds.".to_string(),
                IterableCampaignType::InheritanceClaimCanceledByBeneficiaryReceivedByBenefactor,
            ),
            (
                RecoveryRelationshipRole::TrustedContact,
                RecoveryRelationshipRole::TrustedContact,
            ) => (
                "Your inheritance claim has been canceled.".to_string(),
                IterableCampaignType::InheritanceClaimCanceledByBeneficiaryReceivedByBeneficiary,
            ),
        };

        Ok(NotificationMessage {
            composite_key,
            account_id,
            email_payload: Some(EmailPayload::Iterable {
                campaign_type,
                data_fields: HashMap::from([
                    (BENEFACTOR_ALIAS_FIELD.to_string(), payload.customer_alias),
                    (
                        BENEFICIARY_ALIAS_FIELD.to_string(),
                        payload.trusted_contact_alias,
                    ),
                ]),
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
