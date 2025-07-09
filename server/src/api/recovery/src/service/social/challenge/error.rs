use crate::service::social::relationship::error::ServiceError as RecoveryRelationshipServiceError;
use errors::ApiError;
use thiserror::Error;

#[derive(Debug, Error)]
pub enum ServiceError {
    #[error(transparent)]
    Database(#[from] database::ddb::DatabaseError),
    #[error("Account is not the customer")]
    AccountNotCustomer,
    #[error("Account is not a trusted contact")]
    AccountNotTrustedContact,
    #[error("Mismatch between recovery relationships and challenge requests")]
    MismatchingRecoveryRelationships,
    #[error("Recovery relationship status mismatch")]
    RecoveryRelationshipStatusMismatch,
    #[error(transparent)]
    Notification(#[from] notification::NotificationError),
    #[error(transparent)]
    RecoveryRelationship(#[from] RecoveryRelationshipServiceError),
    #[error(transparent)]
    NotificationPayloadBuilder(#[from] notification::NotificationPayloadBuilderError),
    #[error(transparent)]
    Account(#[from] account::error::AccountError),
    #[error(transparent)]
    TryFromIntError(#[from] std::num::TryFromIntError),
}

impl From<ServiceError> for ApiError {
    fn from(value: ServiceError) -> Self {
        let msg = value.to_string();
        match value {
            ServiceError::NotificationPayloadBuilder(_) | ServiceError::TryFromIntError(_) => {
                ApiError::GenericInternalApplicationError(msg)
            }
            ServiceError::MismatchingRecoveryRelationships => ApiError::GenericBadRequest(msg),
            ServiceError::Database(e) => e.into(),
            ServiceError::AccountNotCustomer | ServiceError::AccountNotTrustedContact => {
                ApiError::GenericForbidden(msg)
            }
            ServiceError::RecoveryRelationshipStatusMismatch => ApiError::GenericConflict(msg),
            ServiceError::Notification(e) => e.into(),
            ServiceError::RecoveryRelationship(e) => e.into(),
            ServiceError::Account(e) => e.into(),
        }
    }
}
