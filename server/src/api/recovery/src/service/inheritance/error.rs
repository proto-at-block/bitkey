use bdk_utils::error::BdkUtilError;
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
    #[error(transparent)]
    BdkUtils(#[from] BdkUtilError),
    #[error(transparent)]
    InvalidAddress(#[from] bdk_utils::bdk::bitcoin::address::Error),
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
    #[error("Pending claim not found between benefactor and beneficiary")]
    PendingClaimNotFound,
    #[error("Completed claim exists between benefactor and beneficiary")]
    CompletedClaimExists,
    #[error("Unable to parse beneficiary package from database")]
    InvalidPackage,
    #[error("No inheritance package was found")]
    NoInheritancePackage,
    #[error("Invalid relationship")]
    InvalidRelationship,
    #[error("Invalid state for claim cancellation")]
    InvalidClaimStateForCancellation,
    #[error("Incompatible account type")]
    IncompatibleAccountType,
    #[error("Active descriptor key set not found")]
    NoActiveDescriptorKeySet,
    #[error("Attempting to update the claim to an unowned destination")]
    UnownedDestination,
    #[error(transparent)]
    Notification(#[from] notification::NotificationError),
    #[error(transparent)]
    NotificationPayloadBuilder(#[from] notification::NotificationPayloadBuilderError),
    #[error("Challenge is invalid")]
    InvalidChallengeSignature,
    #[error("Failed to lock claim")]
    ClaimLockFailed,
    #[error("Can't lock claim before delay end time")]
    ClaimDelayNotComplete,
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
            | ServiceError::BdkUtils(_)
            | ServiceError::InvalidPackage
            | ServiceError::NoInheritancePackage
            | ServiceError::NoActiveDescriptorKeySet
            | ServiceError::ClaimLockFailed
            | ServiceError::RecoveryRelationship(_)
            | ServiceError::NotificationPayloadBuilder(_) => {
                ApiError::GenericInternalApplicationError(err_msg)
            }
            ServiceError::BlankTrustedContactAlias
            | ServiceError::MissingTrustedContactRoles
            | ServiceError::MismatchingRecoveryRelationship
            | ServiceError::PendingClaimExists
            | ServiceError::InvalidRelationship
            | ServiceError::CompletedClaimExists
            | ServiceError::InvalidClaimStateForCancellation
            | ServiceError::PendingClaimNotFound
            | ServiceError::IncompatibleAccountType
            | ServiceError::InvalidAddress(_)
            | ServiceError::ClaimDelayNotComplete
            | ServiceError::UnownedDestination => ApiError::GenericBadRequest(err_msg),
            ServiceError::InvalidChallengeSignature => ApiError::GenericUnauthorized(err_msg),
            ServiceError::Notification(e) => e.into(),
        }
    }
}