use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use time::{serde::rfc3339, OffsetDateTime};
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

use super::format_duration;

const NUM_DAYS_LEFT_FIELD: &str = "numDaysLeft";
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
        let num_days_left = format_duration(payload.delay_end_time - OffsetDateTime::now_utc());

        let (importance, message, campaign_type, extras) = match payload.recipient_account_role {
            RecoveryRelationshipRole::ProtectedCustomer => {
                (AndroidChannelId::UrgentSecurity,
                    format!(
                        "[Action required] You have {num_days_left} left to decline the inheritance claim for your Bitkey wallet and retain control of your funds.",
                    ),
                    IterableCampaignType::InheritanceClaimAlmostOverAsBenefactor,
                    SNSPushPayloadExtras {
                        inheritance_claim_id: Some(payload.inheritance_claim_id.to_string()),
                        navigate_to_screen_id: Some(
                            (NavigationScreenId::InheritanceDeclineClaim as i32).to_string(),
                        ),
                        ..Default::default()
                    },
                )
            }
            RecoveryRelationshipRole::TrustedContact => {
                (
                    AndroidChannelId::RecoveryAccountSecurity,
                    format!(
                        "Your inheritance funds will be available for transfer in {num_days_left}.",
                    ),
                    IterableCampaignType::InheritanceClaimAlmostOverAsBeneficiary,
                    SNSPushPayloadExtras::default(),
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
                    (NUM_DAYS_LEFT_FIELD.to_string(), num_days_left),
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
                extras,
                ..Default::default()
            }),
            sms_payload: Some(SmsPayload::new(message, None)),
        })
    }
}
