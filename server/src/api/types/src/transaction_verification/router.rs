use crate::transaction_verification::entities::{PolicyUpdate, TransactionVerification};
use bdk_utils::bdk::bitcoin::psbt::Psbt;
use bdk_utils::bdk::bitcoin::secp256k1::ecdsa::Signature;
use bdk_utils::bdk::bitcoin::secp256k1::PublicKey;
use serde::{Deserialize, Serialize};
use serde_with::{base64::Base64, serde_as, DisplayFromStr};
use time::serde::rfc3339;
use time::OffsetDateTime;
use utoipa::ToSchema;

use super::TransactionVerificationId;

#[derive(Serialize, Deserialize, ToSchema)]
pub struct TransactionVerificationViewSuccess {
    pub psbt: Psbt,
    pub hw_grant: TransactionVerificationGrantView,
}

#[derive(Serialize, Deserialize, ToSchema)]
#[serde(tag = "status", rename_all = "SCREAMING_SNAKE_CASE")]
pub enum TransactionVerificationView {
    Pending,
    Expired,
    Failed,
    Success(TransactionVerificationViewSuccess),
}

impl From<TransactionVerification> for TransactionVerificationView {
    fn from(verification: TransactionVerification) -> Self {
        match verification {
            TransactionVerification::Pending(_) => TransactionVerificationView::Pending,
            TransactionVerification::Expired(_) => TransactionVerificationView::Expired,
            TransactionVerification::Failed(_) => TransactionVerificationView::Failed,
            TransactionVerification::Success(success) => {
                TransactionVerificationView::Success(TransactionVerificationViewSuccess {
                    psbt: success.common_fields.psbt,
                    hw_grant: success.signed_hw_grant,
                })
            }
        }
    }
}

#[serde_as]
#[derive(Serialize, Deserialize, ToSchema)]
pub struct InitiateTransactionVerificationViewSigned {
    pub psbt: Psbt,
    pub hw_grant: TransactionVerificationGrantView,
}

#[serde_as]
#[derive(Debug, Serialize, Deserialize, Clone, ToSchema, PartialEq)]
pub struct TransactionVerificationGrantView {
    pub version: u8,
    pub hw_auth_public_key: PublicKey,
    #[serde_as(as = "Base64")]
    pub commitment: Vec<u8>,
    #[serde_as(as = "DisplayFromStr")]
    pub signature: Signature,
    #[serde_as(as = "Vec<Base64>")]
    pub reverse_hash_chain: Vec<Vec<u8>>,
}

#[derive(Serialize, Deserialize, ToSchema)]
pub struct InitiateTransactionVerificationViewRequested {
    pub verification_id: TransactionVerificationId,
    #[serde(with = "rfc3339")]
    pub expiration: OffsetDateTime,
}

#[derive(Serialize, Deserialize, ToSchema)]
#[serde(tag = "status", rename_all = "SCREAMING_SNAKE_CASE")]
pub enum InitiateTransactionVerificationView {
    Signed(InitiateTransactionVerificationViewSigned),
    VerificationRequired,
    VerificationRequested(InitiateTransactionVerificationViewRequested),
}

#[derive(Debug, Serialize, Deserialize, ToSchema, PartialEq, Clone)]
#[serde(rename_all = "snake_case")]
pub struct PutTransactionVerificationPolicyRequest {
    #[serde(flatten)]
    pub policy: PolicyUpdate,
    /// Whether to use BIP 177 Bitcoin sign (₿) instead of "sats" text.
    #[serde(default)]
    pub use_bip_177: bool,
}
