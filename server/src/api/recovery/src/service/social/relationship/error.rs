use errors::{ApiError, ErrorCode};
use thiserror::Error;
use types::recovery::social::relationship::{
    RecoveryRelationshipConnectionFieldsBuilderError, RecoveryRelationshipEndorsedBuilderError,
    RecoveryRelationshipUnendorsedBuilderError,
};

#[derive(Debug, Error)]
pub enum ServiceError {
    #[error("Failed to generate recovery relationship id")]
    GenerateId(#[from] external_identifier::Error),
    #[error(transparent)]
    Database(#[from] database::ddb::DatabaseError),
    #[error(transparent)]
    ConnectionFieldsBuilder(#[from] RecoveryRelationshipConnectionFieldsBuilderError),
    #[error(transparent)]
    UnendorsedBuilder(#[from] RecoveryRelationshipUnendorsedBuilderError),
    #[error(transparent)]
    EndorsedBuilder(#[from] RecoveryRelationshipEndorsedBuilderError),
    #[error("Recovery relationship invitation cannot be endorsed")]
    InvitationNonEndorsable,
    #[error("Recovery relationship already established")]
    RelationshipAlreadyEstablished,
    #[error("Recovery relationship invitation expired")]
    InvitationExpired,
    #[error("Recovery relationship invitation code mismatch")]
    InvitationCodeMismatch,
    #[error("Customer cannot be trusted contact")]
    CustomerIsTrustedContact,
    #[error("Unauthorized recovery relationship deletion")]
    UnauthorizedRelationshipDeletion,
    #[error("Unauthorized recovery relationship update")]
    UnauthorizedRelationshipUpdate,
    #[error("Account is already a trusted contact for the customer")]
    AccountAlreadyTrustedContact,
    #[error("Invalid Keyproof, signature over access token required both app and hw auth key")]
    InvalidKeyProof,
    #[error("Invalid operation for access token")]
    InvalidOperationForAccessToken,
    #[error(transparent)]
    Notification(#[from] notification::NotificationError),
    #[error(transparent)]
    NotificationPayloadBuilder(#[from] notification::NotificationPayloadBuilderError),
}

impl From<ServiceError> for ApiError {
    fn from(value: ServiceError) -> Self {
        let msg = value.to_string();
        match value {
            ServiceError::GenerateId(_)
            | ServiceError::ConnectionFieldsBuilder(_)
            | ServiceError::UnendorsedBuilder(_)
            | ServiceError::EndorsedBuilder(_)
            | ServiceError::NotificationPayloadBuilder(_) => {
                ApiError::GenericInternalApplicationError(msg)
            }
            ServiceError::InvitationNonEndorsable => ApiError::GenericBadRequest(msg),
            ServiceError::Database(e) => e.into(),
            ServiceError::RelationshipAlreadyEstablished
            | ServiceError::AccountAlreadyTrustedContact => ApiError::GenericConflict(msg),
            ServiceError::UnauthorizedRelationshipDeletion
            | ServiceError::UnauthorizedRelationshipUpdate
            | ServiceError::CustomerIsTrustedContact
            | ServiceError::InvalidKeyProof
            | ServiceError::InvalidOperationForAccessToken => Self::GenericForbidden(msg),
            ServiceError::InvitationExpired => ApiError::Specific {
                code: ErrorCode::InvitationExpired,
                detail: Some(msg),
                field: None,
            },
            ServiceError::InvitationCodeMismatch => ApiError::Specific {
                code: ErrorCode::CodeMismatch,
                detail: Some(msg),
                field: None,
            },
            ServiceError::Notification(e) => e.into(),
        }
    }
}
