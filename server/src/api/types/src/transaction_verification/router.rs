use bdk_utils::bdk::bitcoin::psbt::Psbt;
use serde::{Deserialize, Serialize};
use time::serde::rfc3339;
use time::OffsetDateTime;
use utoipa::ToSchema;

use crate::transaction_verification::entities::TransactionVerification;

use super::TransactionVerificationId;

#[derive(Serialize, Deserialize, ToSchema)]
pub struct TransactionVerificationViewSuccess {
    pub psbt: Psbt,
    pub hw_grant: String,
    pub signature: String,
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
                    hw_grant: success.common_fields.hw_grant,
                    signature: success.signed_hw_grant,
                })
            }
        }
    }
}

#[derive(Serialize, Deserialize, ToSchema)]
pub struct InitiateTransactionVerificationViewSigned {
    pub psbt: Psbt,
    pub hw_grant: String,
    pub signature: String,
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
