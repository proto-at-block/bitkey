use std::collections::HashMap;

use serde::{Deserialize, Serialize};
use types::recovery::{
    social::relationship::RecoveryRelationshipRole, trusted_contacts::TrustedContactRole,
};

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
pub struct RecoveryRelationshipDeletedPayload {
    pub trusted_contact_alias: String,
    #[serde(default)]
    pub customer_alias: String,
    #[serde(default)]
    pub trusted_contact_roles: Vec<TrustedContactRole>,
    #[serde(default)]
    pub acting_account_role: RecoveryRelationshipRole,
    #[serde(default)]
    pub recipient_account_role: RecoveryRelationshipRole,
}

impl TryFrom<(NotificationCompositeKey, RecoveryRelationshipDeletedPayload)>
    for NotificationMessage
{
    type Error = NotificationError;

    fn try_from(
        v: (NotificationCompositeKey, RecoveryRelationshipDeletedPayload),
    ) -> Result<Self, Self::Error> {
        let (composite_key, payload) = v;
        let (account_id, _) = composite_key.clone();

        let (email_payload, push_payload, sms_payload) = if payload
            .trusted_contact_roles
            .contains(&TrustedContactRole::Beneficiary)
        {
            payloads_for_inheritance(
                payload.trusted_contact_alias,
                payload.customer_alias,
                payload.acting_account_role,
                payload.recipient_account_role,
            )
        } else {
            payloads_for_social_recovery(
                payload.trusted_contact_alias,
                payload.recipient_account_role,
            )
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
    recipient_account_role: RecoveryRelationshipRole,
) -> (
    Option<EmailPayload>,
    Option<SNSPushPayload>,
    Option<SmsPayload>,
) {
    // We only send the notification to the protected customer
    if recipient_account_role == RecoveryRelationshipRole::TrustedContact {
        return (None, None, None);
    }

    let message = format!(
        "{} has been removed as one of your trusted contacts. You can manage your trusted contacts from your Bitkey app if you want to replace them.",
        trusted_contact_alias
    );

    let email_payload = Some(EmailPayload::Iterable {
        campaign_type: IterableCampaignType::RecoveryRelationshipDeleted,
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
    customer_alias: String,
    acting_account_role: RecoveryRelationshipRole,
    recipient_account_role: RecoveryRelationshipRole,
) -> (
    Option<EmailPayload>,
    Option<SNSPushPayload>,
    Option<SmsPayload>,
) {
    let message = match (acting_account_role, recipient_account_role) {
        (
            RecoveryRelationshipRole::ProtectedCustomer,
            RecoveryRelationshipRole::ProtectedCustomer,
        ) => {
            format!(
                "You have successfully removed {} as a beneficiary.",
                trusted_contact_alias
            )
        }
        (RecoveryRelationshipRole::ProtectedCustomer, RecoveryRelationshipRole::TrustedContact) => {
            format!("{} removed you as a beneficiary.", customer_alias)
        }
        (RecoveryRelationshipRole::TrustedContact, RecoveryRelationshipRole::TrustedContact) => {
            format!(
                "You have successfully removed {} as a benefactor.",
                customer_alias
            )
        }
        (RecoveryRelationshipRole::TrustedContact, RecoveryRelationshipRole::ProtectedCustomer) => {
            format!("{} removed you as a benefactor.", trusted_contact_alias)
        }
    };

    let email_payload = None; // TODO: W-9736

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
