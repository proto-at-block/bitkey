use std::collections::HashMap;

use serde::{Deserialize, Serialize};
use time::{macros::format_description, serde::rfc3339, OffsetDateTime};
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

const DELAY_END_TIME_FIELD: &str = "delayEndTime";
const BENEFICIARY_ALIAS_FIELD: &str = "beneficiaryAlias";

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

        let (importance, message, campaign_type, extras) = match payload.recipient_account_role {
            RecoveryRelationshipRole::ProtectedCustomer => {
                (AndroidChannelId::UrgentSecurity,
                    "[Action required] An inheritance claim has been started for your Bitkey wallet. Decline the claim to retain control of your funds.".to_string(),
                IterableCampaignType::InheritanceClaimInitiatedAsBenefactor,
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
                (AndroidChannelId::RecoveryAccountSecurity,
                    format!(
                        "Your inheritance claim has been initiated. Your funds will be available for transfer on {}.",
                        payload.delay_end_time.format(format_description!("[day] [month repr:short] [year]"))?,
                    ),
                IterableCampaignType::InheritanceClaimInitiatedAsBeneficiary,
                SNSPushPayloadExtras {
                    inheritance_claim_id: Some(payload.inheritance_claim_id.to_string()),
                    ..Default::default()
                },
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
                    (DELAY_END_TIME_FIELD.to_string(), formatted_end_date),
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
