use bdk_utils::bdk::bitcoin::psbt::PsbtParseError;
use bdk_utils::error::BdkUtilError;
use errors::ApiError;
use thiserror::Error;
use types::account::identifiers::KeysetId;

#[derive(Debug, Error)]
pub enum SigningError {
    #[error("Server side signing is disabled")]
    ServerSigningDisabled,
    #[error("Attempted to sign with server when user has Mobile Pay turned off")]
    MobilePayDisabled,
    #[error(transparent)]
    BdkUtils(#[from] BdkUtilError),
    #[error("Could not decode psbt due to error: {0}")]
    InvalidPsbt(String),
    #[error(transparent)]
    PsbtParsingFailed(#[from] PsbtParseError),
    #[error("Transaction failed to pass sweep spend rules: {0:?}")]
    SpendRuleCheckFailed(Vec<String>),
    #[error("Account doesn't have requested keyset: {0}")]
    NoSpendKeyset(KeysetId),
    #[error(transparent)]
    PsbtSigning(#[from] wsm_rust_client::Error),
    #[error("Account without active spending keyset")]
    NoActiveSpendKeyset,
}

impl From<SigningError> for ApiError {
    fn from(error: SigningError) -> Self {
        let err_msg = error.to_string();
        match error {
            SigningError::ServerSigningDisabled | SigningError::MobilePayDisabled => {
                ApiError::GenericForbidden(err_msg)
            }
            SigningError::BdkUtils(_)
            | SigningError::PsbtSigning(_)
            | SigningError::NoSpendKeyset(_)
            | SigningError::NoActiveSpendKeyset => {
                ApiError::GenericInternalApplicationError(err_msg)
            }
            SigningError::SpendRuleCheckFailed(_)
            | SigningError::InvalidPsbt(_)
            | SigningError::PsbtParsingFailed(_) => ApiError::GenericBadRequest(err_msg),
        }
    }
}
