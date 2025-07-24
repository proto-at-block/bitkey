use errors::ApiError;
use repository::DatabaseError;
use thiserror::Error;
use types::ExternalIdentifierError;

use crate::clients::kms::KmsError;

#[derive(Debug, Error)]
pub enum CustomerFeedbackServiceError {
    #[error("Database error: {0}")]
    DatabaseError(#[from] DatabaseError),
    #[error("KMS error: {0}")]
    KmsError(#[from] KmsError),
    #[error("External identifier error: {0}")]
    ExternalIdentifierError(#[from] ExternalIdentifierError),
    #[error("Invalid account ID")]
    InvalidAccountId,
    #[error("Attachment already uploaded")]
    AttachmentAlreadyUploaded,
}

impl From<CustomerFeedbackServiceError> for ApiError {
    fn from(e: CustomerFeedbackServiceError) -> Self {
        let msg = e.to_string();
        match e {
            CustomerFeedbackServiceError::DatabaseError(d) => match d {
                DatabaseError::ObjectNotFound(_) => ApiError::GenericNotFound(msg),
                _ => ApiError::GenericInternalApplicationError(msg),
            },
            CustomerFeedbackServiceError::KmsError(_)
            | CustomerFeedbackServiceError::ExternalIdentifierError(_) => {
                ApiError::GenericInternalApplicationError(msg)
            }
            CustomerFeedbackServiceError::InvalidAccountId => ApiError::GenericForbidden(msg),
            CustomerFeedbackServiceError::AttachmentAlreadyUploaded => {
                ApiError::GenericBadRequest(msg)
            }
        }
    }
}
