use crate::service::social::challenge::error::ServiceError as SocialChallengeServiceError;
use account::error::AccountError;
use bdk_utils::error::BdkUtilError;
use comms_verification::error::CommsVerificationError;
use database::ddb::DatabaseError;
use errors::{ApiError, ErrorCode};
use thiserror::Error;
use tracing::{event, Level};
use userpool::userpool::UserPoolError;

#[derive(Debug, Error)]
pub enum RecoveryError {
    #[error("Invalid Transition")]
    InvalidTransition,
    #[error("Recovery does not exist")]
    NoExistingRecovery,
    #[error(transparent)]
    AccountService(#[from] AccountError),
    #[error("Cannot proceed with recovery with pending delay period")]
    DelayPeriodNotFinished,
    #[error("Unable to parse PSBT")]
    ParsePSBT,
    #[error("Unable to sign PSBT")]
    SignPSBT,
    #[error("Unable to start new recovery for Account")]
    StartRecoveryForAccount,
    #[error("No pending recovery action")]
    NoPendingRecoveryDestination,
    #[error("Invalid source auth keys for recovery")]
    InvalidRecoverySource,
    #[error("Invalid destination auth keys for recovery")]
    InvalidRecoveryDestination,
    #[error("Invalid auth key id during rotation")]
    InvalidAuthKeysId,
    #[error("Invalid auth key for rotation")]
    InvalidAuthKey,
    #[error("Invalid factor for completing recovery")]
    InvalidFactorForCompletion,
    #[error("Invalid input for completing recovery")]
    InvalidInputForCompletion,
    #[error("Unable to rotate authentication keyset {0}")]
    RotateAuthKeys(#[from] UserPoolError),
    #[error("No Spend Keyset Found")]
    NoSpendKeysetFound,
    #[error("No Active Auth Keyset")]
    NoActiveAuthKeysError,
    #[error("No Active Spend Keyset")]
    NoActiveSpendKeysetError,
    #[error("Invalid network for spend keyset")]
    InvalidSpendKeysetNetwork,
    #[error("No signature was given")]
    NoSignaturePresent,
    #[error("Recovery requirements not found")]
    RequirementsNotFound,
    #[error("Failed to generate notification payload")]
    GenerateNotificationPayloadError,
    #[error("Failed to persist scheduled notifications")]
    ScheduleNotificationPersistanceError,
    #[error("Failed to send notification")]
    SendNotificationError,
    #[error(transparent)]
    DDBError(#[from] DatabaseError),
    #[error(transparent)]
    BdkUtil(#[from] BdkUtilError),
    #[error(transparent)]
    CommsVerificationError(#[from] CommsVerificationError),
    #[error("Key proof required")]
    KeyProofRequired,
    #[error("Key proof unexpectedly provided for factor")]
    UnexpectedKeyProof,
    #[error("Touchpoint type mismatch")]
    TouchpointTypeMismatch,
    #[error("Touchpoint status mismatch")]
    TouchpointStatusMismatch,
    #[error("Malformed recovery action")]
    MalformedRecoveryAction,
    #[error("Malformed recovery requirements")]
    MalformedRecoveryRequirements,
    #[error("Cannot update parameters for non-test account")]
    InvalidUpdateForNonTestAccount,
    #[error(transparent)]
    ApiError(#[from] ApiError),
    #[error("Destination hardware auth pubkey in use by an account")]
    HwAuthPubkeyReuseAccount,
    #[error("Destination hardware auth pubkey in use by a recovery")]
    HwAuthPubkeyReuseRecovery,
    #[error("Destination app auth pubkey in use by an account")]
    AppAuthPubkeyReuseAccount,
    #[error("Destination app auth pubkey in use by a recovery")]
    AppAuthPubkeyReuseRecovery,
    #[error("Destination recovery auth pubkey in use by an account")]
    RecoveryAuthPubkeyReuseAccount,
    #[error("Destination recovery auth pubkey in use by a recovery")]
    RecoveryAuthPubkeyReuseRecovery,
    #[error("Invalid recovery relationship type")]
    InvalidRecoveryRelationshipType,
    #[error("Destination recovery auth pubkey must be provided")]
    NoDestinationRecoveryAuthPubkey,
    #[error(transparent)]
    SocialChallengeService(#[from] SocialChallengeServiceError),
    #[error("Challenge request was not found")]
    ChallengeRequestNotFound,
}

impl From<RecoveryError> for ApiError {
    fn from(val: RecoveryError) -> Self {
        let err_msg = val.to_string();
        match val {
            RecoveryError::InvalidTransition
            | RecoveryError::InvalidSpendKeysetNetwork
            | RecoveryError::GenerateNotificationPayloadError
            | RecoveryError::ScheduleNotificationPersistanceError
            | RecoveryError::SendNotificationError
            | RecoveryError::NoPendingRecoveryDestination
            | RecoveryError::RotateAuthKeys(_)
            | RecoveryError::NoSpendKeysetFound
            | RecoveryError::RequirementsNotFound
            | RecoveryError::NoActiveAuthKeysError
            | RecoveryError::MalformedRecoveryAction
            | RecoveryError::MalformedRecoveryRequirements
            | RecoveryError::NoActiveSpendKeysetError
            | RecoveryError::InvalidRecoveryRelationshipType => {
                ApiError::GenericInternalApplicationError(err_msg)
            }
            RecoveryError::DelayPeriodNotFinished
            | RecoveryError::ParsePSBT
            | RecoveryError::SignPSBT
            | RecoveryError::NoSignaturePresent
            | RecoveryError::InvalidAuthKey
            | RecoveryError::InvalidFactorForCompletion
            | RecoveryError::InvalidInputForCompletion
            | RecoveryError::KeyProofRequired
            | RecoveryError::UnexpectedKeyProof
            | RecoveryError::TouchpointStatusMismatch
            | RecoveryError::TouchpointTypeMismatch
            | RecoveryError::InvalidRecoverySource
            | RecoveryError::InvalidRecoveryDestination
            | RecoveryError::InvalidUpdateForNonTestAccount
            | RecoveryError::NoDestinationRecoveryAuthPubkey => {
                ApiError::GenericBadRequest(err_msg)
            }
            RecoveryError::AccountService(err) => match err {
                AccountError::DDBError(err) => err.into(),
                _ => ApiError::GenericInternalApplicationError(err_msg),
            },
            RecoveryError::DDBError(err) => match err {
                DatabaseError::ObjectNotFound(_) => ApiError::GenericNotFound(err_msg),
                _ => ApiError::GenericInternalApplicationError(err_msg),
            },
            RecoveryError::BdkUtil(err) => match err {
                BdkUtilError::WalletSync(e) => {
                    event!(
                        Level::WARN,
                        "Error when trying to sync wallet to blockchain: {e}",
                    );
                    ApiError::GenericServiceUnavailable(err_msg)
                }
                _ => err.into(),
            },
            RecoveryError::InvalidAuthKeysId | RecoveryError::ChallengeRequestNotFound => {
                ApiError::GenericNotFound(err_msg)
            }
            RecoveryError::CommsVerificationError(e) => match e {
                CommsVerificationError::StatusMismatch => ApiError::Specific {
                    code: ErrorCode::CommsVerificationRequired,
                    detail: Some("Comms verification required".to_string()),
                    field: None,
                },
                _ => e.into(),
            },
            RecoveryError::StartRecoveryForAccount => ApiError::Specific {
                code: ErrorCode::RecoveryAlreadyExists,
                detail: Some(err_msg),
                field: None,
            },
            RecoveryError::NoExistingRecovery => ApiError::Specific {
                code: ErrorCode::NoRecoveryExists,
                detail: Some(err_msg),
                field: None,
            },
            RecoveryError::ApiError(e) => e,
            RecoveryError::HwAuthPubkeyReuseAccount | RecoveryError::HwAuthPubkeyReuseRecovery => {
                ApiError::Specific {
                    code: ErrorCode::HwAuthPubkeyInUse,
                    detail: Some(err_msg),
                    field: None,
                }
            }
            RecoveryError::AppAuthPubkeyReuseAccount
            | RecoveryError::AppAuthPubkeyReuseRecovery => ApiError::Specific {
                code: ErrorCode::AppAuthPubkeyInUse,
                detail: Some(err_msg),
                field: None,
            },
            RecoveryError::RecoveryAuthPubkeyReuseAccount
            | RecoveryError::RecoveryAuthPubkeyReuseRecovery => ApiError::Specific {
                code: ErrorCode::RecoveryAuthPubkeyInUse,
                detail: Some(err_msg),
                field: None,
            },
            RecoveryError::SocialChallengeService(e) => e.into(),
        }
    }
}
