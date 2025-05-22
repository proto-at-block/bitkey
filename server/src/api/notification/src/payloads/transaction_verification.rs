use std::collections::HashMap;

use serde::{Deserialize, Serialize};

use crate::{
    clients::iterable::IterableCampaignType, email::EmailPayload,
    entities::NotificationCompositeKey, NotificationError, NotificationMessage,
};

const TRANSACTION_VERIFICATION_URL_FIELD: &str = "transactionVerificationURL";

#[derive(Deserialize, Serialize, Clone, Debug, Default)]
pub struct TransactionVerificationPayload {
    pub base_verification_url: String,
    pub auth_token: String,
}

impl TryFrom<(NotificationCompositeKey, TransactionVerificationPayload)> for NotificationMessage {
    type Error = NotificationError;

    fn try_from(
        v: (NotificationCompositeKey, TransactionVerificationPayload),
    ) -> Result<Self, Self::Error> {
        let (composite_key, payload) = v;
        let (account_id, _) = composite_key.clone();

        let transaction_verification_url = format!(
            "{}/transaction-verification?auth_token={}",
            payload.base_verification_url, payload.auth_token
        );

        let email_payload = Some(EmailPayload::Iterable {
            campaign_type: IterableCampaignType::TransactionVerification,
            data_fields: HashMap::from([(
                TRANSACTION_VERIFICATION_URL_FIELD.to_string(),
                transaction_verification_url,
            )]),
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
    fn test_transaction_verification_payload_try_from() {
        // Setup
        let account_id =
            AccountId::from_str("urn:wallet-account:00000000000000000000000000").unwrap();
        let notification_id =
            NotificationId::from_str("customer-01H4661RMJDM407YDJ63BHAJ2R").unwrap();
        let composite_key = NotificationCompositeKey::from((account_id.clone(), notification_id));

        let base_url = "https://example.com".to_string();
        let auth_token = "abc123xyz789".to_string();
        let payload = TransactionVerificationPayload {
            base_verification_url: base_url.clone(),
            auth_token: auth_token.clone(),
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
            assert_eq!(campaign_type, IterableCampaignType::TransactionVerification);
            assert!(data_fields.contains_key(TRANSACTION_VERIFICATION_URL_FIELD));

            let expected_url =
                "https://example.com/transaction-verification?auth_token=abc123xyz789";
            assert_eq!(
                data_fields.get(TRANSACTION_VERIFICATION_URL_FIELD).unwrap(),
                &expected_url
            );
        } else {
            panic!("Email payload was not of expected type");
        }

        // Check other payloads are None
        assert!(message.push_payload.is_none());
        assert!(message.sms_payload.is_none());
    }

    #[test]
    fn test_default_transaction_verification_payload() {
        let default_payload = TransactionVerificationPayload::default();
        assert_eq!(default_payload.base_verification_url, "");
        assert_eq!(default_payload.auth_token, "");
    }
}
