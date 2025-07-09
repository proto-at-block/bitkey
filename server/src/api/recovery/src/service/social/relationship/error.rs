use errors::{ApiError, ErrorCode};
use promotion_code::error::PromotionCodeError;
use thiserror::Error;
use types::recovery::social::relationship::{
    RecoveryRelationshipConnectionFieldsBuilderError, RecoveryRelationshipEndorsedBuilderError,
    RecoveryRelationshipUnendorsedBuilderError,
};
use types::recovery::trusted_contacts::{TrustedContactError, TrustedContactRole};

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
    #[error("Account has reached the maximum number of trusted contacts for role: {0:?}")]
    MaxTrustedContactsReached(TrustedContactRole),
    #[error("Account has reached the maximum number of protected customers")]
    MaxProtectedCustomersReached,
    #[error("Trusted contact's alias cannot be blank")]
    BlankTrustedContactAlias,
    #[error("Trusted contact has no roles assigned")]
    MissingTrustedContactRoles,
    #[error(transparent)]
    PromotionCode(#[from] PromotionCodeError),
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
        let msg = value.to_string();
        match value {
            ServiceError::GenerateId(_)
            | ServiceError::ConnectionFieldsBuilder(_)
            | ServiceError::UnendorsedBuilder(_)
            | ServiceError::EndorsedBuilder(_)
            | ServiceError::NotificationPayloadBuilder(_)
            | ServiceError::PromotionCode(_) => ApiError::GenericInternalApplicationError(msg),
            ServiceError::InvitationNonEndorsable
            | ServiceError::BlankTrustedContactAlias
            | ServiceError::MissingTrustedContactRoles => ApiError::GenericBadRequest(msg),
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
            ServiceError::MaxTrustedContactsReached(_) => ApiError::Specific {
                code: ErrorCode::MaxTrustedContactsReached,
                detail: Some(msg),
                field: None,
            },
            ServiceError::MaxProtectedCustomersReached => ApiError::Specific {
                code: ErrorCode::MaxProtectedCustomersReached,
                detail: Some(msg),
                field: None,
            },
        }
    }
}
