use errors::ApiError;
use thiserror::Error;
use types::{
    account::{errors::AccountError, AccountType},
    privileged_action::shared::PrivilegedActionType,
};

#[derive(Debug, Error)]
pub enum ServiceError {
    #[error(transparent)]
    Api(#[from] errors::ApiError),
    #[error(transparent)]
    Account(#[from] account::error::AccountError),
    #[error(transparent)]
    Database(#[from] database::ddb::DatabaseError),
    #[error(transparent)]
    TryFromInt(#[from] std::num::TryFromIntError),
    #[error(transparent)]
    ExternalIdentifier(#[from] external_identifier::Error),
    #[error(transparent)]
    NotificationPayloadBuilder(#[from] notification::NotificationPayloadBuilderError),
    #[error(transparent)]
    Notification(#[from] notification::NotificationError),

    #[error("Account does not own privileged action instance record")]
    RecordAccountIdForbidden,
    #[error("Privileged action instance record authorization strategy type conflict")]
    RecordAuthorizationStrategyTypeConflict,
    #[error("Privileged action instance record delay and notify status conflict")]
    RecordDelayAndNotifyStatusConflict,
    #[error("Privileged action instance record out of band status conflict")]
    RecordOutofBandStatusConflict,

    #[error("Cannot configure delay for privileged action type {0} and account type {1}")]
    CannotConfigureDelay(PrivilegedActionType, AccountType),
    #[error("Cannot update delay for non-test account")]
    CannotUpdateDelayForNonTestAccount,

    #[error(
        "No authorization strategy defined for privileged action type {0} and account type {1}"
    )]
    NoAuthorizationStrategyDefinedForbidden(PrivilegedActionType, AccountType),
    #[error("Cannot continue defined authorization strategy type")]
    CannotContinueDefinedAuthorizationStrategyType,
    #[error("Failed hardware proof of possession check")]
    FailedHardwareProofOfPossessionCheck,
    #[error("Authentication was required but not provided")]
    AuthenticationRequiredButNotProvided,
    #[error("Privileged action instance record privileged action type conflict")]
    RecordPrivilegedActionTypeConflict,
    #[error("Privileged action instance record authorization strategy type unexpected")]
    RecordAuthorizationStrategyTypeUnexpected,
    #[error("Delay and notify end time in future")]
    DelayAndNotifyEndTimeInFuture,
    #[error("Bad privileged action instance input authorization strategy type")]
    BadInputAuthorizationStrategyType,
    #[error("Bad privileged action instance input completion token")]
    BadInputCompletionToken,
    #[error("Bad privileged action instance input web auth token")]
    BadInputWebAuthToken,

    #[error("Cannot have multiple concurrent privileged action instances of type {0}")]
    MultipleConcurrentInstancesConflict(PrivilegedActionType),
}

impl From<AccountError> for ServiceError {
    fn from(value: AccountError) -> Self {
        ApiError::GenericInternalApplicationError(value.to_string()).into()
    }
}

impl From<ServiceError> for ApiError {
    fn from(value: ServiceError) -> Self {
        let msg = value.to_string();
        match value {
            ServiceError::Api(e) => e,
            ServiceError::Account(e) => e.into(),
            ServiceError::Database(e) => e.into(),
            ServiceError::Notification(e) => e.into(),
            ServiceError::RecordAccountIdForbidden
            | ServiceError::NoAuthorizationStrategyDefinedForbidden(_, _)
            | ServiceError::FailedHardwareProofOfPossessionCheck
            | ServiceError::AuthenticationRequiredButNotProvided => ApiError::GenericForbidden(msg),
            ServiceError::RecordAuthorizationStrategyTypeConflict
            | ServiceError::RecordDelayAndNotifyStatusConflict
            | ServiceError::DelayAndNotifyEndTimeInFuture
            | ServiceError::RecordPrivilegedActionTypeConflict
            | ServiceError::RecordOutofBandStatusConflict
            | ServiceError::MultipleConcurrentInstancesConflict(_) => {
                ApiError::GenericConflict(msg)
            }
            ServiceError::BadInputAuthorizationStrategyType
            | ServiceError::BadInputCompletionToken
            | ServiceError::BadInputWebAuthToken
            | ServiceError::CannotConfigureDelay(_, _)
            | ServiceError::CannotContinueDefinedAuthorizationStrategyType => {
                ApiError::GenericBadRequest(msg)
            }
            ServiceError::RecordAuthorizationStrategyTypeUnexpected
            | ServiceError::TryFromInt(_)
            | ServiceError::ExternalIdentifier(_)
            | ServiceError::NotificationPayloadBuilder(_) => {
                ApiError::GenericInternalApplicationError(msg)
            }
            ServiceError::CannotUpdateDelayForNonTestAccount => ApiError::GenericForbidden(msg),
        }
    }
}
