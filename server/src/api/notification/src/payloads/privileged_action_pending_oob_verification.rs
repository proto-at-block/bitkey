use serde::{Deserialize, Serialize};
use types::privileged_action::shared::{PrivilegedActionInstanceId, PrivilegedActionType};

use crate::{
    email::EmailPayload, entities::NotificationCompositeKey,
    payloads::privileged_action_notification_builder::OutOfBandNotificationConfig,
    NotificationError, NotificationMessage,
};

#[derive(Deserialize, Serialize, Clone, Debug)]
pub struct PrivilegedActionPendingOutOfBandVerificationPayload {
    pub privileged_action_instance_id: PrivilegedActionInstanceId,
    pub privileged_action_type: PrivilegedActionType,
    pub base_verification_url: String,
    pub web_auth_token: String,
}

impl
    TryFrom<(
        NotificationCompositeKey,
        PrivilegedActionPendingOutOfBandVerificationPayload,
    )> for NotificationMessage
{
    type Error = NotificationError;

    fn try_from(
        v: (
            NotificationCompositeKey,
            PrivilegedActionPendingOutOfBandVerificationPayload,
        ),
    ) -> Result<Self, Self::Error> {
        let (composite_key, payload) = v;
        let (account_id, _) = composite_key.clone();

        let config = OutOfBandNotificationConfig::from(payload.privileged_action_type);
        let url =
            config.build_verification_url(&payload.base_verification_url, &payload.web_auth_token);

        let data_fields = config.build_data_fields(url);
        let email_payload = Some(EmailPayload::Iterable {
            campaign_type: config.campaign_types.pending,
            data_fields,
        });

        Ok(NotificationMessage {
            composite_key,
            account_id,
            email_payload,
            push_payload: None,
            sms_payload: None,
        })
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::identifiers::NotificationId;
    use crate::{clients::iterable::IterableCampaignType, entities::NotificationCompositeKey};
    use std::str::FromStr;
    use types::account::identifiers::AccountId;

    #[test]
    fn test_privileged_action_pending_oob_verification_payload_try_from() {
        // Setup
        let account_id =
            AccountId::from_str("urn:wallet-account:00000000000000000000000000").unwrap();
        let notification_id =
            NotificationId::from_str("customer-01H4661RMJDM407YDJ63BHAJ2R").unwrap();
        let composite_key = NotificationCompositeKey::from((account_id.clone(), notification_id));

        let base_url = "https://example.com".to_string();
        let auth_token = "abc123xyz789".to_string();

        let payload = PrivilegedActionPendingOutOfBandVerificationPayload {
            base_verification_url: base_url.clone(),
            web_auth_token: auth_token.clone(),
            privileged_action_instance_id: PrivilegedActionInstanceId::gen().unwrap(),
            privileged_action_type: PrivilegedActionType::LoosenTransactionVerificationPolicy,
        };

        // Execute
        let result = NotificationMessage::try_from((composite_key.clone(), payload.clone()));

        // Verify
        assert!(result.is_ok());
        let message = result.unwrap();

        // Check email payload
        assert!(message.email_payload.is_some());
        if let Some(EmailPayload::Iterable {
            campaign_type,
            data_fields,
        }) = message.email_payload
        {
            assert_eq!(
                campaign_type,
                IterableCampaignType::PrivilegedActionPendingOutOfBandVerification
            );
            assert!(data_fields.contains_key("verificationURL"));

            let expected_url =
                "https://example.com/tx-validation-policy?web_auth_token=abc123xyz789";
            assert_eq!(data_fields.get("verificationURL").unwrap(), &expected_url);
        } else {
            panic!("Email payload was not of expected type");
        }

        // Check other payloads are None
        assert!(message.push_payload.is_none());
        assert!(message.sms_payload.is_none());
    }
}
