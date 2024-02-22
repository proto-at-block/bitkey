use account::{entities::Account, error::AccountError};
use database::ddb::DatabaseError;
use errors::{ApiError, ErrorCode};
use thiserror::Error;

#[derive(Debug, Error)]
pub enum AccountValidationError {
    #[error("Hardware auth pubkey in use by an account")]
    HwAuthPubkeyReuseAccount,
    #[error("Hardware auth pubkey in use by a recovery")]
    HwAuthPubkeyReuseRecovery,
    #[error("App auth pubkey in use by an account")]
    AppAuthPubkeyReuseAccount,
    #[error("App auth pubkey in use by a recovery")]
    AppAuthPubkeyReuseRecovery,
    #[error("Recovery auth pubkey in use by an account")]
    RecoveryAuthPubkeyReuseAccount,
    #[error("Recovery auth pubkey in use by a recovery")]
    RecoveryAuthPubkeyReuseRecovery,
    #[error("Duplicate account already exists for provided keys")]
    DuplicateAccountForKeys(Account),
    #[error("Invalid network for Test Account")]
    InvalidNetworkForTestAccount,
    #[error(transparent)]
    DatabaseError(#[from] DatabaseError),
    #[error(transparent)]
    AccountError(#[from] AccountError),
}

impl From<AccountValidationError> for ApiError {
    fn from(err: AccountValidationError) -> Self {
        let err_msg = err.to_string();
        match err {
            AccountValidationError::AccountError(e) => e.into(),
            AccountValidationError::DatabaseError(e) => e.into(),
            AccountValidationError::HwAuthPubkeyReuseAccount
            | AccountValidationError::HwAuthPubkeyReuseRecovery => ApiError::Specific {
                code: ErrorCode::HwAuthPubkeyInUse,
                detail: Some(err_msg),
                field: None,
            },
            AccountValidationError::AppAuthPubkeyReuseAccount
            | AccountValidationError::AppAuthPubkeyReuseRecovery => ApiError::Specific {
                code: ErrorCode::AppAuthPubkeyInUse,
                detail: Some(err_msg),
                field: None,
            },
            AccountValidationError::RecoveryAuthPubkeyReuseAccount
            | AccountValidationError::RecoveryAuthPubkeyReuseRecovery => ApiError::Specific {
                code: ErrorCode::RecoveryAuthPubkeyInUse,
                detail: Some(err_msg),
                field: None,
            },
            AccountValidationError::InvalidNetworkForTestAccount
            | AccountValidationError::DuplicateAccountForKeys(_) => {
                ApiError::GenericBadRequest(err_msg)
            }
        }
    }
}
