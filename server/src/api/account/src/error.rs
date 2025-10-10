use database::ddb::{DatabaseError, DatabaseObject};
use errors::{ApiError, ErrorCode};
use thiserror::Error;
use types::account::errors::AccountError as AccountErrorType;

#[derive(Debug, Error)]
pub enum AccountError {
    #[error("Invalid state for auth keysets")]
    InvalidKeysetState,
    #[error("Invalid keyset id during rotation for account")]
    InvalidKeysetIdentifierForRotation,
    #[error("Invalid spending keyset id during rotation for account")]
    InvalidSpendingKeysetIdentifierForRotation,
    #[error("Invalid spending key definition id during rotation for account")]
    InvalidSpendingKeyDefinitionIdentifierForRotation,
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
    #[error(transparent)]
    UserPoolError(#[from] userpool::userpool::UserPoolError),
    #[error("Unauthorized device token registration")]
    UnauthorizedDeviceTokenRegistration,
    #[error("Touchpoint already active")]
    TouchpointAlreadyActive,
    #[error("Conflicting spending key definition state during rotation for account")]
    ConflictingSpendingKeyDefinitionStateForRotation,
    #[error("Missing keyset ID(s)")]
    MissingKeysetIds,
    #[error("Unrecognized keyset ID(s)")]
    UnrecognizedKeysetIds,
    #[error("Missing descriptor backup for keyset")]
    MissingDescriptorBackup,
}

impl From<AccountErrorType> for AccountError {
    fn from(e: AccountErrorType) -> Self {
        match e {
            AccountErrorType::InvalidUpdateAccountProperties => {
                AccountError::InvalidUpdateAccountProperties
            }
        }
    }
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
            | AccountError::Unexpected
            | AccountError::UserPoolError(_) => ApiError::GenericInternalApplicationError(err_msg),
            AccountError::TouchpointNotFound | AccountError::UnrecognizedKeysetIds => {
                ApiError::GenericNotFound(err_msg)
            }
            AccountError::InvalidSpendingKeysetIdentifierForRotation
            | AccountError::InvalidSpendingKeyDefinitionIdentifierForRotation
            | AccountError::MissingKeysetIds => ApiError::GenericBadRequest(err_msg),
            AccountError::DDBError(err) => match err {
                DatabaseError::ObjectNotFound(DatabaseObject::Account) => ApiError::Specific {
                    code: ErrorCode::AccountNotFound,
                    detail: Some(err_msg),
                    field: None,
                },
                _ => ApiError::GenericInternalApplicationError(err_msg),
            },
            AccountError::NotEligibleForDeletion
            | AccountError::ConflictingSpendingKeyDefinitionStateForRotation
            | AccountError::MissingDescriptorBackup => Self::GenericConflict(err_msg),
            AccountError::UnauthorizedDeviceTokenRegistration => {
                ApiError::GenericUnauthorized(err_msg)
            }
            AccountError::TouchpointAlreadyActive => ApiError::Specific {
                code: ErrorCode::TouchpointAlreadyActive,
                detail: Some(err_msg),
                field: None,
            },
        }
    }
}
