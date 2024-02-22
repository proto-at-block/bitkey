use thiserror::Error;

use aws_sdk_secretsmanager::error::SdkError;
use aws_sdk_secretsmanager::operation::describe_secret::DescribeSecretError;
use aws_sdk_secretsmanager::operation::get_secret_value::GetSecretValueError;
use aws_sdk_secretsmanager::operation::put_secret_value::PutSecretValueError;
use aws_sdk_secretsmanager::operation::update_secret_version_stage::UpdateSecretVersionStageError;

#[derive(Error, Debug)]
pub enum SecretsManagerError {
    #[error("Secret {0} not found in secrets manager")]
    NotFound(String),
    #[error("Secret {0} has no pending version id matching target {1}")]
    PendingVersionIdNotFound(String, String),
    #[error("Secret {0} has no current version id")]
    CurrentVersionIdNotFound(String),
    #[error("Secret {0} not configured for rotation")]
    RotationDisabled(String),
    #[error("Secret {0} already has a version marked as AWSPENDING")]
    PendingSecretAlreadyExists(String),
    #[error("Secret {0}: Target version id {1} is already current")]
    VersionIdAlreadyCurrent(String, String),
    #[error(transparent)]
    GetSecretValueError(#[from] SdkError<GetSecretValueError>),
    #[error(transparent)]
    DescribeSecretError(#[from] SdkError<DescribeSecretError>),
    #[error(transparent)]
    PutSecretValueError(#[from] SdkError<PutSecretValueError>),
    #[error(transparent)]
    UpdateSecretVersionStageError(#[from] SdkError<UpdateSecretVersionStageError>),
}
