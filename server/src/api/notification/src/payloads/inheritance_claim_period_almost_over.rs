use std::collections::HashMap;

use serde::{Deserialize, Serialize};
use time::{serde::rfc3339, OffsetDateTime};
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

const END_DATE_FIELD: &str = "endDate";
const BENEFICIARY_ALIAS_FIELD: &str = "beneficiaryAlias";

#[derive(Deserialize, Serialize, Clone, Debug)]
pub struct InheritanceClaimPeriodAlmostOverPayload {
    pub inheritance_claim_id: InheritanceClaimId,
    pub trusted_contact_alias: String,
    pub recipient_account_role: RecoveryRelationshipRole,
    #[serde(with = "rfc3339")]
    pub delay_end_time: OffsetDateTime,
}

impl
    TryFrom<(
        NotificationCompositeKey,
        InheritanceClaimPeriodAlmostOverPayload,
    )> for NotificationMessage
{
    type Error = NotificationError;

    fn try_from(
        v: (
            NotificationCompositeKey,
            InheritanceClaimPeriodAlmostOverPayload,
        ),
    ) -> Result<Self, Self::Error> {
        let (composite_key, payload) = v;
        let (account_id, _) = composite_key.clone();

        let (importance, message, campaign_type) = match payload.recipient_account_role {
            RecoveryRelationshipRole::ProtectedCustomer => {
                (AndroidChannelId::UrgentSecurity,
                    "[Action required] You have 3 days left to decline the inheritance claim for your Bitkey wallet and retain control of your funds.".to_string(),
                IterableCampaignType::InheritanceClaimAlmostOverAsBenefactor
                )
            }
            RecoveryRelationshipRole::TrustedContact => {
                (AndroidChannelId::RecoveryAccountSecurity,
                    "Your inheritance funds will be available for transfer in 3 days.".to_string(),
                IterableCampaignType::InheritanceClaimAlmostOverAsBeneficiary
                )
            }
        };

        let calendar_end_date = payload.delay_end_time.to_calendar_date();
        let formatted_end_date = format!(
            "{} {} {}",
            calendar_end_date.2, calendar_end_date.1, calendar_end_date.0
        );

        Ok(NotificationMessage {
            composite_key,
            account_id,
            email_payload: Some(EmailPayload::Iterable {
                campaign_type,
                data_fields: HashMap::from([
                    (END_DATE_FIELD.to_string(), formatted_end_date),
                    (
                        BENEFICIARY_ALIAS_FIELD.to_string(),
                        payload.trusted_contact_alias,
                    ),
                ]),
            }),
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
