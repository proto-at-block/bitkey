use std::collections::HashMap;

use serde::{Deserialize, Serialize};
use types::recovery::{
    social::relationship::{RecoveryRelationshipId, RecoveryRelationshipRole},
    trusted_contacts::TrustedContactRole,
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

const TRUSTED_CONTACT_ALIAS_FIELD: &str = "trustedContactAlias";
const BENEFACTOR_ALIAS_FIELD: &str = "benefactorAlias";
const BENEFICIARY_ALIAS_FIELD: &str = "beneficiaryAlias";

#[derive(Deserialize, Serialize, Clone, Debug)]
pub struct RecoveryRelationshipInvitationAcceptedPayload {
    pub trusted_contact_alias: String,
    pub protected_customer_alias: String,
    pub recovery_relationship_id: RecoveryRelationshipId,
    pub trusted_contact_roles: Vec<TrustedContactRole>,
    #[serde(default)]
    pub recipient_account_role: RecoveryRelationshipRole,
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
            payloads_for_inheritance(
                payload.recipient_account_role,
                payload.recovery_relationship_id,
                payload.protected_customer_alias,
                payload.trusted_contact_alias,
            )
        } else {
            payloads_for_social_recovery(
                payload.recipient_account_role,
                payload.recovery_relationship_id,
                payload.trusted_contact_alias,
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
    recipient_account_role: RecoveryRelationshipRole,
    recovery_relationship_id: RecoveryRelationshipId,
    trusted_contact_alias: String,
) -> (
    Option<EmailPayload>,
    Option<SNSPushPayload>,
    Option<SmsPayload>,
) {
    if recipient_account_role == RecoveryRelationshipRole::TrustedContact {
        return (None, None, None);
    }

    let message = format!(
        "{} is now a Recovery Contact for your Bitkey. Open the Bitkey app to confirm they've been added to your list.",
        trusted_contact_alias
    );
    let extras = SNSPushPayloadExtras {
        navigate_to_screen_id: Some(
            (NavigationScreenId::SocialRecoveryProtectedCustomerInviteAccepted as i32).to_string(),
        ),
        recovery_relationship_id: Some(recovery_relationship_id.to_string()),
        ..Default::default()
    };

    let email_payload = Some(EmailPayload::Iterable {
        campaign_type:
            IterableCampaignType::RecoveryRelationshipInvitationAcceptedReceivedByProtectedCustomer,
        data_fields: HashMap::from([(
            TRUSTED_CONTACT_ALIAS_FIELD.to_string(),
            trusted_contact_alias,
        )]),
    });

    let push_payload = Some(SNSPushPayload {
        message: message.clone(),
        android_channel_id: AndroidChannelId::RecoveryAccountSecurity,
        extras,
        ..Default::default()
    });

    let sms_payload = Some(SmsPayload {
        message,
        unsupported_country_codes: None,
    });

    (email_payload, push_payload, sms_payload)
}

fn payloads_for_inheritance(
    recipient_account_role: RecoveryRelationshipRole,
    recovery_relationship_id: RecoveryRelationshipId,
    benefactor_alias: String,
    beneficiary_alias: String,
) -> (
    Option<EmailPayload>,
    Option<SNSPushPayload>,
    Option<SmsPayload>,
) {
    let (message, campaign_type, extras) = match recipient_account_role {
        RecoveryRelationshipRole::ProtectedCustomer => (
            format!("{} has accepted your invite to be your beneficiary. Open the app to activate your inheritance plan.", beneficiary_alias),
            IterableCampaignType::RecoveryRelationshipInvitationAcceptedReceivedByBenefactor,
            SNSPushPayloadExtras {
                navigate_to_screen_id: Some(
                    (NavigationScreenId::InheritanceBenefactorInviteAccepted as i32).to_string(),
                ),
                recovery_relationship_id: Some(recovery_relationship_id.to_string()),
                ..Default::default()
            },
        ),
        RecoveryRelationshipRole::TrustedContact => (
            String::from("You have been added as a beneficiary of a Bitkey wallet."),
            IterableCampaignType::RecoveryRelationshipInvitationAcceptedReceivedByBeneficiary,
            SNSPushPayloadExtras::default(),
        ),
    };

    let email_payload = Some(EmailPayload::Iterable {
        campaign_type,
        data_fields: HashMap::from([
            (BENEFICIARY_ALIAS_FIELD.to_string(), beneficiary_alias),
            (BENEFACTOR_ALIAS_FIELD.to_string(), benefactor_alias),
        ]),
    });

    let push_payload = Some(SNSPushPayload {
        message: message.clone(),
        android_channel_id: AndroidChannelId::RecoveryAccountSecurity,
        extras,
        ..Default::default()
    });

    let sms_payload = Some(SmsPayload {
        message,
        unsupported_country_codes: None,
    });

    (email_payload, push_payload, sms_payload)
}
