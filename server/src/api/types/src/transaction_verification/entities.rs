use base64::{engine::general_purpose::URL_SAFE_NO_PAD as b64, Engine as _};
use bdk_utils::bdk::bitcoin::psbt::Psbt;
use bdk_utils::bdk::bitcoin::Txid;
use rand::{rngs::OsRng, RngCore};
use serde::{Deserialize, Serialize};
use serde_with::serde_as;
use strum_macros::{Display, EnumDiscriminants, EnumString};
use time::{serde::rfc3339, OffsetDateTime};

use crate::account::entities::TransactionVerificationPolicy;
use crate::account::money::Money;
use crate::currencies::CurrencyCode;
use crate::transaction_verification::router::TransactionVerificationGrantView;
use crate::{account::identifiers::AccountId, transaction_verification::TransactionVerificationId};

/// The unit in which the Bitcoin amount is displayed in the secure verification site.
#[derive(Serialize, Deserialize, Debug, Clone, Eq, PartialEq)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum BitcoinDisplayUnit {
    Bitcoin,
    Satoshi,
}

#[derive(Clone, Debug, Serialize, Deserialize, PartialEq)]
#[serde(rename_all = "snake_case")]
pub struct PolicyUpdateMoney {
    pub amount_sats: u64,
    pub amount_fiat: u64,
    pub currency_code: CurrencyCode,
}

#[derive(Clone, Debug, Serialize, Deserialize, PartialEq)]
#[serde(
    tag = "state",
    content = "threshold",
    rename_all = "SCREAMING_SNAKE_CASE"
)]
pub enum PolicyUpdate {
    Never,
    Threshold(PolicyUpdateMoney),
    Always,
}

impl From<PolicyUpdate> for TransactionVerificationPolicy {
    fn from(policy: PolicyUpdate) -> Self {
        match policy {
            PolicyUpdate::Never => TransactionVerificationPolicy::Never,
            PolicyUpdate::Threshold(money) => TransactionVerificationPolicy::Threshold(Money {
                amount: money.amount_fiat,
                currency_code: money.currency_code,
            }),
            PolicyUpdate::Always => TransactionVerificationPolicy::Always,
        }
    }
}

#[derive(Serialize, Deserialize, Debug, Clone, EnumDiscriminants)]
#[strum_discriminants(derive(Display, EnumString))]
#[serde(tag = "verification_status", rename_all = "SCREAMING_SNAKE_CASE")]
pub enum TransactionVerification {
    Pending(TransactionVerificationPending),
    Expired(TransactionVerificationCommonFields),
    Failed(TransactionVerificationCommonFields),
    Success(TransactionVerificationSuccess),
}

impl TransactionVerification {
    pub fn common_fields(&self) -> &TransactionVerificationCommonFields {
        match self {
            Self::Pending(pending) => &pending.common_fields,
            Self::Expired(common_fields) => common_fields,
            Self::Failed(common_fields) => common_fields,
            Self::Success(success) => &success.common_fields,
        }
    }

    pub fn new_pending(
        account_id: &AccountId,
        psbt: Psbt,
        fiat_currency: CurrencyCode,
        bitcoin_display_unit: BitcoinDisplayUnit,
    ) -> Self {
        let id = TransactionVerificationId::gen().unwrap();
        Self::Pending(TransactionVerificationPending::new(
            id,
            account_id.to_owned(),
            psbt,
            fiat_currency,
            bitcoin_display_unit,
        ))
    }
}

#[derive(Serialize, Deserialize, Debug, Clone, PartialEq)]
pub struct TransactionVerificationPending {
    /// The web authentication token, allowing the user to retrieve the verification request
    /// without logging into an account.
    pub web_auth_token: String,
    /// The token used to confirm the transaction verification.
    pub confirmation_token: String,
    /// The token used to cancel the transaction verification.
    pub cancellation_token: String,
    /// The fiat currency we should show the user in the secure site.
    pub fiat_currency: CurrencyCode,
    /// The unit in which the Bitcoin amount is displayed in the secure verification site.
    pub bitcoin_display_unit: BitcoinDisplayUnit,
    #[serde(flatten)]
    pub common_fields: TransactionVerificationCommonFields,
}

impl TransactionVerificationPending {
    pub fn new(
        id: TransactionVerificationId,
        account_id: AccountId,
        psbt: Psbt,
        fiat_currency: CurrencyCode,
        bitcoin_display_unit: BitcoinDisplayUnit,
    ) -> Self {
        // Generate 128 bytes (1024 bits) of random data for each token
        let mut web_auth_bytes = [0u8; 128];
        let mut confirmation_bytes = [0u8; 128];
        let mut cancellation_bytes = [0u8; 128];

        // Fill arrays with secure random bytes
        OsRng.fill_bytes(&mut web_auth_bytes);
        OsRng.fill_bytes(&mut confirmation_bytes);
        OsRng.fill_bytes(&mut cancellation_bytes);

        // Encode as URL-safe base64 strings
        let web_auth_token = b64.encode(web_auth_bytes);
        let confirmation_token = b64.encode(confirmation_bytes);
        let cancellation_token = b64.encode(cancellation_bytes);

        Self {
            web_auth_token,
            confirmation_token,
            cancellation_token,
            fiat_currency,
            bitcoin_display_unit,
            common_fields: TransactionVerificationCommonFields::new(id, account_id, psbt),
        }
    }

    pub fn mark_as_failed(&self) -> TransactionVerification {
        TransactionVerification::Failed(self.common_fields.to_owned())
    }

    pub fn mark_as_success(
        &self,
        signed_hw_grant: TransactionVerificationGrantView,
    ) -> TransactionVerification {
        TransactionVerification::Success(TransactionVerificationSuccess {
            signed_hw_grant,
            common_fields: self.common_fields.to_owned(),
        })
    }

    pub fn is_confirmation_token(&self, token: &str) -> bool {
        // For higher security, consider using the `subtle` crate.
        self.confirmation_token == token
    }

    pub fn is_cancellation_token(&self, token: &str) -> bool {
        // For higher security, consider using the `subtle` crate.
        self.cancellation_token == token
    }
}

#[derive(Serialize, Deserialize, Debug, Clone, PartialEq)]
pub struct TransactionVerificationSuccess {
    pub signed_hw_grant: TransactionVerificationGrantView,
    #[serde(flatten)]
    pub common_fields: TransactionVerificationCommonFields,
}

#[derive(Serialize, Deserialize, Debug, Clone, PartialEq)]
#[serde_as]
pub struct TransactionVerificationCommonFields {
    #[serde(rename = "partition_key")]
    pub id: TransactionVerificationId,
    #[serde_as(as = "DisplayFromStr")]
    pub txid: Txid,
    #[serde_as(as = "DisplayFromStr")]
    pub psbt: Psbt,
    pub account_id: AccountId,
    #[serde(with = "rfc3339")]
    pub expires_at: OffsetDateTime,
    #[serde(with = "rfc3339")]
    pub created_at: OffsetDateTime,
    #[serde(with = "rfc3339")]
    pub updated_at: OffsetDateTime,
}

impl TransactionVerificationCommonFields {
    pub fn new(id: TransactionVerificationId, account_id: AccountId, psbt: Psbt) -> Self {
        Self {
            id,
            account_id,
            txid: psbt.unsigned_tx.txid(),
            psbt,
            // figure out expiry
            expires_at: OffsetDateTime::now_utc() + time::Duration::hours(24),
            created_at: OffsetDateTime::now_utc(),
            updated_at: OffsetDateTime::now_utc(),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::transaction_verification::entities::BitcoinDisplayUnit::Satoshi;
    use bdk_utils::bdk::bitcoin::secp256k1::ecdsa::Signature;
    use bdk_utils::bdk::bitcoin::secp256k1::PublicKey;
    use std::str::FromStr;

    // Helper to create a test Psbt
    fn create_test_psbt() -> Psbt {
        Psbt::from_str("cHNidP8BAHUCAAAAASaBcTce3/KF6Tet7qSze3gADAVmy7OtZGQXE8pCFxv2AAAAAAD+////AtPf9QUAAAAAGXapFNDFmQPFusKGh2DpD9UhpGZap2UgiKwA4fUFAAAAABepFDVF5uM7gyxHBQ8k0+65PJwDlIvHh7MuEwAAAQD9pQEBAAAAAAECiaPHHqtNIOA3G7ukzGmPopXJRjr6Ljl/hTPMti+VZ+UBAAAAFxYAFL4Y0VKpsBIDna89p95PUzSe7LmF/////4b4qkOnHf8USIk6UwpyN+9rRgi7st0tAXHmOuxqSJC0AQAAABcWABT+Pp7xp0XpdNkCxDVZQ6vLNL1TU/////8CAMLrCwAAAAAZdqkUhc/xCX/Z4Ai7NK9wnGIZeziXikiIrHL++E4sAAAAF6kUM5cluiHv1irHU6m80GfWx6ajnQWHAkcwRAIgJxK+IuAnDzlPVoMR3HyppolwuAJf3TskAinwf4pfOiQCIAGLONfc0xTnNMkna9b7QPZzMlvEuqFEyADS8vAtsnZcASED0uFWdJQbrUqZY3LLh+GFbTZSYG2YVi/jnF6efkE/IQUCSDBFAiEA0SuFLYXc2WHS9fSrZgZU327tzHlMDDPOXMMJ/7X85Y0CIGczio4OFyXBl/saiK9Z9R5E5CVbIBZ8hoQDHAXR8lkqASECI7cr7vCWXRC+B3jv7NYfysb3mk6haTkzgHNEZPhPKrMAAAAAAAAA").unwrap()
    }

    #[test]
    fn test_new_pending() {
        let account_id = AccountId::gen().unwrap();
        let psbt = create_test_psbt();

        let tx_verification = TransactionVerification::new_pending(
            &account_id,
            psbt.clone(),
            CurrencyCode::USD,
            Satoshi,
        );

        match &tx_verification {
            TransactionVerification::Pending(pending) => {
                // Check common fields
                assert_eq!(pending.common_fields.account_id, account_id);

                // Check display fields
                assert_eq!(pending.fiat_currency, CurrencyCode::USD);
                assert_eq!(pending.bitcoin_display_unit, Satoshi);

                // Check tokens were generated
                assert!(!pending.confirmation_token.is_empty());
                assert!(!pending.cancellation_token.is_empty());
                assert!(!pending.web_auth_token.is_empty());
                assert_ne!(pending.confirmation_token, pending.cancellation_token);
                assert_ne!(pending.confirmation_token, pending.web_auth_token);

                // Check timestamps
                let now = OffsetDateTime::now_utc();
                assert!(pending.common_fields.created_at <= now);
                assert!(pending.common_fields.expires_at > now);
            }
            _ => panic!("Expected Pending variant"),
        }
    }

    #[test]
    fn test_token_validation() {
        let account_id = AccountId::gen().unwrap();
        let psbt = create_test_psbt();

        if let TransactionVerification::Pending(pending) =
            TransactionVerification::new_pending(&account_id, psbt, CurrencyCode::USD, Satoshi)
        {
            // Test confirmation token
            assert!(pending.is_confirmation_token(&pending.confirmation_token));
            assert!(!pending.is_confirmation_token("wrong-token"));

            // Test cancellation token
            assert!(pending.is_cancellation_token(&pending.cancellation_token));
            assert!(!pending.is_cancellation_token("wrong-token"));
        } else {
            panic!("Expected Pending variant");
        }
    }

    #[test]
    fn test_state_transitions() {
        let account_id = AccountId::gen().unwrap();
        let psbt = create_test_psbt();
        let signed_hw_grant = TransactionVerificationGrantView {
            version: 0,
            hw_auth_public_key: PublicKey::from_str(
                "0326cb04015410966e715a14da549bacbf12acb823fe1247540b6123b2daea0164",
            )
            .unwrap(),
            commitment: vec![],
            reverse_hash_chain: vec![],
            signature: Signature::from_compact(&[0u8; 64]).unwrap(),
        };

        if let TransactionVerification::Pending(pending) =
            TransactionVerification::new_pending(&account_id, psbt, CurrencyCode::USD, Satoshi)
        {
            // Test transition to Failed
            let failed = pending.mark_as_failed();
            match failed {
                TransactionVerification::Failed(common_fields) => {
                    assert_eq!(common_fields.id, pending.common_fields.id);
                    assert_eq!(common_fields.account_id, pending.common_fields.account_id);
                }
                _ => panic!("Expected Failed variant"),
            }

            // Test transition to Success
            let success = pending.mark_as_success(signed_hw_grant.clone());
            match success {
                TransactionVerification::Success(success_data) => {
                    assert_eq!(success_data.common_fields.id, pending.common_fields.id);
                    assert_eq!(success_data.signed_hw_grant, signed_hw_grant);
                }
                _ => panic!("Expected Success variant"),
            }
        } else {
            panic!("Expected Pending variant");
        }
    }

    #[test]
    fn test_common_fields() {
        let account_id = AccountId::gen().unwrap();
        let psbt = create_test_psbt();

        if let TransactionVerification::Pending(pending) =
            TransactionVerification::new_pending(&account_id, psbt, CurrencyCode::USD, Satoshi)
        {
            let signed_hw_grant = TransactionVerificationGrantView {
                version: 0,
                hw_auth_public_key: PublicKey::from_str(
                    "0326cb04015410966e715a14da549bacbf12acb823fe1247540b6123b2daea0164",
                )
                .unwrap(),
                commitment: vec![],
                reverse_hash_chain: vec![],
                signature: Signature::from_compact(&[0u8; 64]).unwrap(),
            };
            // Test for all variants
            let pending_tx = TransactionVerification::Pending(pending.clone());
            let failed_tx = pending.mark_as_failed();
            let success_tx = pending.mark_as_success(signed_hw_grant);
            let expired_tx = TransactionVerification::Expired(pending.common_fields.clone());

            // All should have the same ID
            let id = pending.common_fields.id;
            assert_eq!(pending_tx.common_fields().id, id);
            assert_eq!(failed_tx.common_fields().id, id);
            assert_eq!(success_tx.common_fields().id, id);
            assert_eq!(expired_tx.common_fields().id, id);
        } else {
            panic!("Expected Pending variant");
        }
    }

    #[test]
    fn test_token_uniqueness() {
        // Generate two verifications and check tokens are unique
        let account_id = AccountId::gen().unwrap();
        let psbt = create_test_psbt();

        let verification1 = TransactionVerification::new_pending(
            &account_id,
            psbt.clone(),
            CurrencyCode::USD,
            Satoshi,
        );
        let verification2 =
            TransactionVerification::new_pending(&account_id, psbt, CurrencyCode::USD, Satoshi);

        if let (TransactionVerification::Pending(v1), TransactionVerification::Pending(v2)) =
            (&verification1, &verification2)
        {
            assert_ne!(v1.confirmation_token, v2.confirmation_token);
            assert_ne!(v1.cancellation_token, v2.cancellation_token);
        } else {
            panic!("Expected Pending variants");
        }
    }
}
