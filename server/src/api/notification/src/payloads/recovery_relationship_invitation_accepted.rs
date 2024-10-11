use std::collections::HashMap;

use serde::{Deserialize, Serialize};
use types::recovery::trusted_contacts::TrustedContactRole;

use crate::{
    clients::iterable::IterableCampaignType,
    email::EmailPayload,
    entities::NotificationCompositeKey,
    push::{AndroidChannelId, SNSPushPayload},
    sms::SmsPayload,
    NotificationError, NotificationMessage,
};

const TRUSTED_CONTACT_ALIAS_FIELD: &str = "trustedContactAlias";

#[derive(Deserialize, Serialize, Clone, Debug, Default)]
pub struct RecoveryRelationshipInvitationAcceptedPayload {
    pub trusted_contact_alias: String,
    pub trusted_contact_roles: Vec<TrustedContactRole>,
}

impl
    TryFrom<(
        NotificationCompositeKey,
        RecoveryRelationshipInvitationAcceptedPayload,
    )> for NotificationMessage
{
    type Error = NotificationError;

    fn try_from(
        v: (
            NotificationCompositeKey,
            RecoveryRelationshipInvitationAcceptedPayload,
        ),
    ) -> Result<Self, Self::Error> {
        let (composite_key, payload) = v;
        let (account_id, _) = composite_key.clone();

        let (email_payload, push_payload, sms_payload) = if payload
            .trusted_contact_roles
            .contains(&TrustedContactRole::Beneficiary)
        {
            payloads_for_inheritance(payload.trusted_contact_alias)
        } else {
            payloads_for_social_recovery(payload.trusted_contact_alias)
        };

        Ok(NotificationMessage {
            composite_key,
            account_id,
            email_payload,
            push_payload,
            sms_payload,
        })
    }
}

fn payloads_for_social_recovery(
    trusted_contact_alias: String,
) -> (
    Option<EmailPayload>,
    Option<SNSPushPayload>,
    Option<SmsPayload>,
) {
    let message = format!(
        "{} is now set up as a trusted contact for your Bitkey. They can assist if you ever need help recovering a part of your Bitkey wallet.",
        trusted_contact_alias
    );

    let email_payload = Some(EmailPayload::Iterable {
        campaign_type: IterableCampaignType::RecoveryRelationshipInvitationAccepted,
        data_fields: HashMap::from([(
            TRUSTED_CONTACT_ALIAS_FIELD.to_string(),
            trusted_contact_alias,
        )]),
    });

    let push_payload = Some(SNSPushPayload {
        message: message.clone(),
        android_channel_id: AndroidChannelId::RecoveryAccountSecurity,
        ..Default::default()
    });

    let sms_payload = Some(SmsPayload {
        message,
        unsupported_country_codes: None,
    });

    (email_payload, push_payload, sms_payload)
}

fn payloads_for_inheritance(
    trusted_contact_alias: String,
) -> (
    Option<EmailPayload>,
    Option<SNSPushPayload>,
    Option<SmsPayload>,
) {
    let message = format!(
        "{} has accepted your invite to be a beneficiary. No further action is needed.",
        trusted_contact_alias
    );

    let email_payload = None; // TODO: W-9732

    let push_payload = Some(SNSPushPayload {
        message: message.clone(),
        android_channel_id: AndroidChannelId::RecoveryAccountSecurity,
        ..Default::default()
    });

    let sms_payload = Some(SmsPayload {
        message,
        unsupported_country_codes: None,
    });

    (email_payload, push_payload, sms_payload)
}
