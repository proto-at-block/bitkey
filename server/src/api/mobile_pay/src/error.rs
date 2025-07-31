use crate::spend_rules::errors::{SpendRuleCheckError, SpendRuleCheckErrors};
use crate::util::MobilepayDatetimeError;
use bdk_utils::bdk::bitcoin::psbt::PsbtParseError;
use bdk_utils::error::BdkUtilError;
use database::ddb::DatabaseError;
use errors::ApiError;
use exchange_rate::error::ExchangeRateError;
use std::error::Error;
use thiserror::Error;
use transaction_verification::error::TransactionVerificationError;
use types::account::identifiers::KeysetId;

#[derive(Debug, Error)]
pub enum SigningError {
    #[error("Server side signing is disabled")]
    ServerSigningDisabled,
    #[error("Attempted to sign with server when user has Mobile Pay turned off")]
    MobilePayDisabled,
    #[error("Bdk utils error: {0}")]
    BdkUtils(#[from] BdkUtilError),
    #[error("Could not decode psbt due to error: {0}")]
    InvalidPsbt(String),
    #[error(transparent)]
    PsbtParsingFailed(#[from] PsbtParseError),
    #[error(transparent)]
    SpendRuleCheckFailed(#[from] SpendRuleCheckErrors),
    #[error("Account doesn't have requested keyset: {0}")]
    NoSpendKeyset(KeysetId),
    #[error(transparent)]
    PsbtSigning(#[from] wsm_rust_client::Error),
    #[error("Account without active spending keyset")]
    NoActiveSpendKeyset,
    #[error("Psbt was not broadcasted because it wasn't fully signed")]
    CannotBroadcastNonFullySignedPsbt,
    #[error("Mobilepay settings not found")]
    MissingMobilePaySettings,
    #[error("Could not get spending records: {0}")]
    CouldNotGetSpendingRecords(String),
    #[error(transparent)]
    ExchangeRate(#[from] ExchangeRateError),
    #[error(transparent)]
    DatabaseError(#[from] DatabaseError),
    #[error(transparent)]
    MobilePayDatetimeError(#[from] MobilepayDatetimeError),
    #[error("Transaction verification required")]
    TransactionVerificationRequired,
    #[error(transparent)]
    TransactionVerification(#[from] TransactionVerificationError),
}

impl From<SigningError> for ApiError {
    fn from(error: SigningError) -> Self {
        let err_msg = error.to_string();

        let source = error.source();
        if let Some(source) = source {
            if let Some(bdk_error) = source.downcast_ref::<BdkUtilError>() {
                return bdk_error.into();
            }
        }
        match error {
            SigningError::ServerSigningDisabled | SigningError::MobilePayDisabled => {
                ApiError::GenericForbidden(err_msg)
            }
            SigningError::InvalidPsbt(_)
            | SigningError::PsbtParsingFailed(_)
            | SigningError::CannotBroadcastNonFullySignedPsbt => {
                ApiError::GenericBadRequest(err_msg)
            }
            SigningError::SpendRuleCheckFailed(errors) => {
                if errors.has_error(&SpendRuleCheckError::SpendLimitInactive) {
                    ApiError::GenericForbidden(err_msg)
                } else if errors
                    .has_error(&SpendRuleCheckError::OutputsBelongToSanctionedIndividuals)
                {
                    ApiError::GenericUnavailableForLegalReasons(err_msg)
                } else {
                    ApiError::GenericBadRequest(err_msg)
                }
            }
            SigningError::TransactionVerification(_) => ApiError::GenericBadRequest(err_msg),
            _ => ApiError::GenericInternalApplicationError(err_msg),
        }
    }
}
