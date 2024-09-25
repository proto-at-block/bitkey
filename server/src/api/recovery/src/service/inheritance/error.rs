use errors::ApiError;
use thiserror::Error;

use types::recovery::trusted_contacts::TrustedContactError;

use crate::service::social::relationship::error::ServiceError as RecoveryRelationshipServiceError;

#[derive(Debug, Error)]
pub enum ServiceError {
    #[error("Failed to generate recovery relationship id")]
    GenerateId(#[from] external_identifier::Error),
    #[error(transparent)]
    Database(#[from] database::ddb::DatabaseError),
    #[error("Trusted contact's alias cannot be blank")]
    BlankTrustedContactAlias,
    #[error("Trusted contact has no roles assigned")]
    MissingTrustedContactRoles,
    #[error(transparent)]
    RecoveryRelationship(#[from] RecoveryRelationshipServiceError),
    #[error("Mismatch between recovery relationships and inheritance claim")]
    MismatchingRecoveryRelationship,
    #[error("Pending claim exists between benefactor and beneficiary")]
    PendingClaimExists,
    #[error("Completed claim exists between benefactor and beneficiary")]
    CompletedClaimExists,
    #[error("Unable to parse beneficiary package from database")]
    InvalidPackage,
    #[error("Invalid relationship")]
    InvalidRelationship,
    #[error("Invalid state for claim cancellation")]
    InvalidClaimStateForCancelation,
}

impl From<TrustedContactError> for ServiceError {
    fn from(value: TrustedContactError) -> Self {
        match value {
            TrustedContactError::BlankAlias => ServiceError::BlankTrustedContactAlias,
            TrustedContactError::NoRoles => ServiceError::MissingTrustedContactRoles,
        }
    }
}

impl From<ServiceError> for ApiError {
    fn from(value: ServiceError) -> Self {
        let err_msg = value.to_string();
        match value {
            ServiceError::GenerateId(_)
            | ServiceError::Database(_)
            | ServiceError::InvalidPackage
            | ServiceError::RecoveryRelationship(_) => {
                ApiError::GenericInternalApplicationError(err_msg)
            }
            ServiceError::BlankTrustedContactAlias
            | ServiceError::MissingTrustedContactRoles
            | ServiceError::MismatchingRecoveryRelationship
            | ServiceError::PendingClaimExists
            | ServiceError::InvalidRelationship
            | ServiceError::CompletedClaimExists
            | ServiceError::InvalidClaimStateForCancelation => ApiError::GenericBadRequest(err_msg),
        }
    }
}
