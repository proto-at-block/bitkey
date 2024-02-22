use database::ddb::{DatabaseError, DatabaseObject};
use errors::{ApiError, ErrorCode};
use thiserror::Error;

#[derive(Debug, Error)]
pub enum AccountError {
    #[error("Invalid state for auth keysets")]
    InvalidKeysetState,
    #[error("Invalid keyset id during rotation for account")]
    InvalidKeysetIdentifierForRotation,
    #[error("Invalid spending keyset id during rotation for account")]
    InvalidSpendingKeysetIdentifierForRotation,
    #[error("Failed to generate keyset identifier")]
    InvalidIdentifier(#[from] external_identifier::Error),
    #[error(transparent)]
    DDBError(#[from] DatabaseError),
    #[error("Couldn't create channel for Push Notification")]
    CreatePushNotificationChannel,
    #[error("Platform ARN not found")]
    PlatformArnNotFound,
    #[error("Touchpoint not found")]
    TouchpointNotFound,
    #[error("Invalid account type")]
    InvalidAccountType,
    #[error("Invalid update to account properties")]
    InvalidUpdateAccountProperties,
    #[error("Unexpected account error")]
    Unexpected,
    #[error("Account not eligible for deletion")]
    NotEligibleForDeletion,
}

impl From<AccountError> for ApiError {
    fn from(e: AccountError) -> Self {
        let err_msg = e.to_string();
        match e {
            AccountError::InvalidKeysetState
            | AccountError::InvalidIdentifier(_)
            | AccountError::PlatformArnNotFound
            | AccountError::InvalidKeysetIdentifierForRotation
            | AccountError::CreatePushNotificationChannel
            | AccountError::InvalidAccountType
            | AccountError::InvalidUpdateAccountProperties
            | AccountError::Unexpected => ApiError::GenericInternalApplicationError(err_msg),
            AccountError::TouchpointNotFound => ApiError::GenericNotFound(err_msg),
            AccountError::InvalidSpendingKeysetIdentifierForRotation => {
                ApiError::GenericBadRequest(err_msg)
            }
            AccountError::DDBError(err) => match err {
                DatabaseError::ObjectNotFound(o) => match o {
                    DatabaseObject::Account => ApiError::Specific {
                        code: ErrorCode::AccountNotFound,
                        detail: Some(err_msg),
                        field: None,
                    },
                    _ => ApiError::GenericInternalApplicationError(err_msg),
                },
                _ => ApiError::GenericInternalApplicationError(err_msg),
            },
            AccountError::NotEligibleForDeletion => Self::GenericConflict(err_msg),
        }
    }
}
