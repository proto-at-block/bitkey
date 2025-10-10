use std::collections::HashMap;

use serde::{Deserialize, Serialize};
use strum_macros::Display as StrumDisplay;
use types::account::identifiers::AccountId;

use crate::{
    clients::iterable::IterableCampaignType, email::EmailPayload,
    entities::NotificationCompositeKey, sms::SmsPayload, NotificationError, NotificationMessage,
};

const VERIFICATION_CODE_FIELD: &str = "verificationCode";
const TEMPLATE_TYPE_FIELD: &str = "templateType";

#[derive(Deserialize, Serialize, Clone, Debug, StrumDisplay)]
#[serde(rename_all = "snake_case")]
#[strum(serialize_all = "snake_case")]
pub enum TemplateType {
    Onboarding,
    Recovery,
}

#[derive(Deserialize, Serialize, Clone, Debug)]
pub struct CommsVerificationPayload {
    pub account_id: AccountId,
    pub code: String,
    pub template_type: TemplateType,
}

impl TryFrom<(NotificationCompositeKey, CommsVerificationPayload)> for NotificationMessage {
    type Error = NotificationError;

    fn try_from(
        v: (NotificationCompositeKey, CommsVerificationPayload),
    ) -> Result<Self, Self::Error> {
        let (composite_key, payload) = v;
        let (account_id, _) = composite_key.clone();
        Ok(NotificationMessage {
            composite_key,
            account_id,
            email_payload: Some(EmailPayload::Iterable {
                campaign_type: IterableCampaignType::CommsVerification,
                data_fields: HashMap::from([
                    (VERIFICATION_CODE_FIELD.to_string(), payload.code.to_owned()),
                    (
                        TEMPLATE_TYPE_FIELD.to_string(),
                        payload.template_type.to_string(),
                    ),
                ]),
            }),
            push_payload: None,
            sms_payload: Some(SmsPayload::new(
                format!("Your Bitkey verification code is: {}", payload.code),
                None,
            )),
        })
    }
}
