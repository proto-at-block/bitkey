use bdk_utils::bdk::bitcoin::psbt::PsbtParseError;
use bdk_utils::error::BdkUtilError;
use database::ddb::DatabaseError;
use errors::ApiError;
use std::error::Error;
use thiserror::Error;
use types::account::identifiers::KeysetId;
use types::transaction_verification::entities::TransactionVerificationDiscriminants;

#[derive(Debug, Error)]
pub enum TransactionVerificationError {
    #[error(transparent)]
    Account(#[from] account::error::AccountError),
    #[error("Bdk utils error: {0}")]
    BdkUtils(#[from] BdkUtilError),
    #[error("Database error due to error: {0}")]
    DatabaseError(#[from] database::ddb::DatabaseError),
    #[error("Exchange rate error")]
    ExchangeRateError(#[from] exchange_rate::error::ExchangeRateError),
    #[error("Verification request not found")]
    InvalidVerificationId,
    #[error("Expected verification state: {0:?}, but got: {1:?}")]
    InvalidVerificationState(
        TransactionVerificationDiscriminants,
        TransactionVerificationDiscriminants,
    ),
    #[error("Invalid token for completion of transaction verification")]
    InvalidTokenForCompletion,
    #[error("Account doesn't have requested keyset: {0}")]
    NoSpendKeyset(KeysetId),
    #[error(transparent)]
    Notification(#[from] notification::NotificationError),
    #[error(transparent)]
    NotificationPayloadBuilder(#[from] notification::NotificationPayloadBuilderError),
    #[error(transparent)]
    PsbtParsingFailed(#[from] PsbtParseError),
}

impl From<TransactionVerificationError> for ApiError {
    fn from(val: TransactionVerificationError) -> Self {
        let err_msg = val.to_string();

        let source = val.source();
        if let Some(source) = source {
            if let Some(bdk_error) = source.downcast_ref::<BdkUtilError>() {
                return bdk_error.into();
            }
        }

        match val {
            TransactionVerificationError::ExchangeRateError(_)
            | TransactionVerificationError::Notification(_)
            | TransactionVerificationError::NotificationPayloadBuilder(_) => {
                ApiError::GenericInternalApplicationError(err_msg)
            }
            TransactionVerificationError::InvalidVerificationId => {
                ApiError::GenericNotFound(err_msg)
            }
            TransactionVerificationError::DatabaseError(err) => match err {
                DatabaseError::ObjectNotFound(_) => ApiError::GenericBadRequest(err_msg),
                _ => ApiError::GenericInternalApplicationError(err_msg),
            },
            TransactionVerificationError::Account(_)
            | TransactionVerificationError::BdkUtils(_)
            | TransactionVerificationError::InvalidVerificationState(_, _)
            | TransactionVerificationError::InvalidTokenForCompletion
            | TransactionVerificationError::NoSpendKeyset(_)
            | TransactionVerificationError::PsbtParsingFailed(_) => {
                ApiError::GenericBadRequest(err_msg)
            }
        }
    }
}
