use account::error::AccountError;
use errors::{ApiError, ErrorCode};
use notification::NotificationError;
use thiserror::Error;

#[derive(Debug, Error)]
pub enum CommsVerificationError {
    #[error("Comms verification code mismatch")]
    CodeMismatch,
    #[error("Comms verification code expired")]
    CodeExpired,
    #[error("Invalid comms verification status transition")]
    StatusTransition,
    #[error("Comms verification status mismatch")]
    StatusMismatch,
    #[error(transparent)]
    ApiError(#[from] ApiError),
    #[error(transparent)]
    TimeFormatError(#[from] time::error::Format),
    #[error(transparent)]
    NotificationError(#[from] NotificationError),
    #[error(transparent)]
    SerdeJsonError(#[from] serde_json::Error),
    #[error(transparent)]
    AccountError(#[from] AccountError),
    #[error(transparent)]
    NotificationPayloadBuilderError(#[from] notification::NotificationPayloadBuilderError),
    #[error(transparent)]
    ArgonPasswordHashError(#[from] argon2::password_hash::Error),
}

impl From<CommsVerificationError> for ApiError {
    fn from(value: CommsVerificationError) -> Self {
        match value {
            CommsVerificationError::ApiError(e) => e,
            CommsVerificationError::NotificationError(e) => e.into(),
            CommsVerificationError::AccountError(e) => e.into(),
            CommsVerificationError::CodeMismatch => ApiError::Specific {
                code: ErrorCode::CodeMismatch,
                detail: Some(value.to_string()),
                field: None,
            },
            CommsVerificationError::CodeExpired => ApiError::Specific {
                code: ErrorCode::CodeExpired,
                detail: Some(value.to_string()),
                field: None,
            },
            CommsVerificationError::StatusTransition | CommsVerificationError::StatusMismatch => {
                ApiError::GenericBadRequest(value.to_string())
            }
            CommsVerificationError::SerdeJsonError(_)
            | CommsVerificationError::TimeFormatError(_)
            | CommsVerificationError::NotificationPayloadBuilderError(_)
            | CommsVerificationError::ArgonPasswordHashError(_) => {
                ApiError::GenericInternalApplicationError(value.to_string())
            }
        }
    }
}
