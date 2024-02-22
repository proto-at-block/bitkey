use crate::secrets_manager::{
    CreatePendingSecret, FetchSecret, MarkSecretAsCurrent, SecretsManager, ValidateSecretCanRotate,
};
use crate::tests::init::{create_mock, mock_url};
use aws_sdk_secretsmanager::error::DisplayErrorContext;
use wiremock::MockServer;

async fn secrets_manager(mock_server: &MockServer) -> SecretsManager {
    std::env::set_var("AWS_REGION", "us-west-2");
    std::env::set_var("AWS_ACCESS_KEY_ID", "akid");
    std::env::set_var("AWS_SECRET_ACCESS_KEY", "secret");

    SecretsManager::with_endpoint_url(mock_url(mock_server).into()).await
}

mod mock_secrets {
    pub const CAN_ROTATE: &str = "can_rotate";
    pub const MISSING_CURRENT: &str = "missing_current";
    pub const NON_ROTATING: &str = "non_rotating";
    pub const ALREADY_ROTATED: &str = "already_rotated";
    pub const EXISTING_PENDING_AND_CURRENT: &str = "existing_pending_and_current";
    pub const NO_CURRENT_VERSION: &str = "no_current_version";
}

pub(crate) mod target_header {
    pub const DESCRIBE_SECRET: &str = "secretsmanager.DescribeSecret";
    pub const GET_SECRET: &str = "secretsmanager.GetSecretValue";
    pub const PUT_SECRET: &str = "secretsmanager.PutSecretValue";
    pub const UPDATE_SECRET_VERSION: &str = "secretsmanager.UpdateSecretVersionStage";
}

#[tokio::test]
async fn test_secret_value_exists() {
    let secret_name = mock_secrets::CAN_ROTATE;
    let mock_server = MockServer::start().await;
    create_mock(
        &mock_server,
        target_header::GET_SECRET,
        &format!("{{\"SecretId\" : \"{secret_name}\"}}"),
        200,
        "asm-get-secret-value-can-rotate-default.json",
    )
    .await;
    let secrets_manager = secrets_manager(&mock_server).await;

    let secret_value = secrets_manager.secret_value(secret_name).await;

    let expected_value = "super_secret_value";
    assert!(secret_value.is_ok());
    assert_eq!(secret_value.unwrap(), expected_value);
}

#[tokio::test]
async fn test_secret_value_does_not_exist() {
    let secret_name = mock_secrets::MISSING_CURRENT;
    let mock_server = MockServer::start().await;
    create_mock(
        &mock_server,
        target_header::GET_SECRET,
        &format!("{{\"SecretId\" : \"{secret_name}\"}}"),
        400,
        "asm-get-secret-value-missing-current.json",
    )
    .await;
    let secrets_manager = secrets_manager(&mock_server).await;

    let result = secrets_manager.secret_value(secret_name).await;

    let error_context = DisplayErrorContext(result.unwrap_err()).to_string();
    assert!(error_context.contains("ResourceNotFoundException"));
}

#[tokio::test]
async fn test_validate_can_rotate_ok() {
    let secret_name = mock_secrets::CAN_ROTATE;
    let target_version_id = "pending_version_id";
    let mock_server = MockServer::start().await;
    create_mock(
        &mock_server,
        target_header::DESCRIBE_SECRET,
        &format!("{{\"SecretId\" : \"{secret_name}\"}}"),
        200,
        "asm-describe-secret-can-rotate.json",
    )
    .await;
    let secrets_manager = secrets_manager(&mock_server).await;

    let can_rotate = secrets_manager
        .validate_can_rotate(secret_name, target_version_id)
        .await;

    assert!(can_rotate.is_ok());
}

#[tokio::test]
async fn test_validate_can_rotate_rotation_disabled() {
    let secret_name = mock_secrets::NON_ROTATING;
    let target_version_id = "pending_version_id";
    let mock_server = MockServer::start().await;
    create_mock(
        &mock_server,
        target_header::DESCRIBE_SECRET,
        &format!("{{\"SecretId\" : \"{secret_name}\"}}"),
        200,
        "asm-describe-secret-non-rotating.json",
    )
    .await;
    let secrets_manager = secrets_manager(&mock_server).await;

    let can_rotate = secrets_manager
        .validate_can_rotate(secret_name, target_version_id)
        .await;

    let expected_error_msg = format!("Secret {secret_name} not configured for rotation");
    let actual_error_msg = format!("{}", can_rotate.unwrap_err());
    assert_eq!(expected_error_msg, actual_error_msg);
}

#[tokio::test]
async fn test_validate_can_rotate_version_not_pending() {
    let secret_name = mock_secrets::CAN_ROTATE;
    let target_version_id = "current_version_id";
    let mock_server = MockServer::start().await;
    create_mock(
        &mock_server,
        target_header::DESCRIBE_SECRET,
        &format!("{{\"SecretId\" : \"{secret_name}\"}}"),
        200,
        "asm-describe-secret-can-rotate.json",
    )
    .await;
    let secrets_manager = secrets_manager(&mock_server).await;

    let can_rotate = secrets_manager
        .validate_can_rotate(secret_name, target_version_id)
        .await;

    let expected_error_msg = format!(
        "Secret {secret_name} has no pending version id matching target {target_version_id}"
    );
    let actual_error_msg = format!("{}", can_rotate.unwrap_err());
    assert_eq!(expected_error_msg, actual_error_msg);
}

#[tokio::test]
async fn test_validate_can_rotate_missing_version() {
    let secret_name = mock_secrets::CAN_ROTATE;
    let target_version_id = "missing_version";
    let mock_server = MockServer::start().await;
    create_mock(
        &mock_server,
        target_header::DESCRIBE_SECRET,
        &format!("{{\"SecretId\" : \"{secret_name}\"}}"),
        200,
        "asm-describe-secret-can-rotate.json",
    )
    .await;
    let secrets_manager = secrets_manager(&mock_server).await;

    let can_rotate = secrets_manager
        .validate_can_rotate(secret_name, target_version_id)
        .await;

    let expected_error_msg = format!(
        "Secret {secret_name} has no pending version id matching target {target_version_id}"
    );
    let actual_error_msg = format!("{}", can_rotate.unwrap_err());
    assert_eq!(expected_error_msg, actual_error_msg);
}

#[tokio::test]
async fn test_validate_can_rotate_version_already_current() {
    let secret_name = mock_secrets::ALREADY_ROTATED;
    let target_version_id = "version_id";
    let mock_server = MockServer::start().await;
    create_mock(
        &mock_server,
        target_header::DESCRIBE_SECRET,
        &format!("{{\"SecretId\" : \"{secret_name}\"}}"),
        200,
        "asm-describe-secret-already-rotated.json",
    )
    .await;
    let secrets_manager = secrets_manager(&mock_server).await;

    let can_rotate = secrets_manager
        .validate_can_rotate(secret_name, target_version_id)
        .await;

    let expected_error_msg =
        format!("Secret {secret_name}: Target version id {target_version_id} is already current");
    let actual_error_msg = format!("{}", can_rotate.unwrap_err());
    assert_eq!(expected_error_msg, actual_error_msg);
}

#[tokio::test]
async fn test_create_pending_secret_success() {
    // arrange
    let secret_name = mock_secrets::CAN_ROTATE;
    let secret_value = "new_secret_value";
    let target_version_id = "pending_version_id";
    let mock_server = MockServer::start().await;

    // current version exists
    create_mock(
        &mock_server,
        target_header::GET_SECRET,
        &format!("{{\"SecretId\" : \"{secret_name}\", \"VersionStage\": \"AWSCURRENT\"}}"),
        200,
        "asm-get-secret-value-can-rotate-current.json",
    )
    .await;

    // pending version does not exist
    create_mock(
        &mock_server,
        target_header::GET_SECRET,
        &format!("{{\"SecretId\" : \"{secret_name}\", \"VersionStage\": \"AWSPENDING\"}}"),
        400,
        "asm-get-secret-value-can-rotate-pending.json",
    )
    .await;
    create_mock(
        &mock_server,
        target_header::PUT_SECRET,
        &format!(
            r#"{{
            "SecretId" : "{secret_name}",
            "SecretString" : "{secret_value}",
            "ClientRequestToken" : "{target_version_id}",
            "VersionStages": [ "AWSPENDING" ]
        }}"#
        ),
        200,
        "asm-put-secret-value-can-rotate.json",
    )
    .await;
    let mut secrets_manager = secrets_manager(&mock_server).await;

    // act
    let result = secrets_manager
        .create_pending_secret(secret_name, target_version_id, secret_value)
        .await;

    //assert
    assert!(result.is_ok());
}

#[tokio::test]
async fn test_create_pending_secret_missing_current_secret() {
    let secret_name = mock_secrets::MISSING_CURRENT;
    let secret_value = "new_secret_value";
    let target_version_id = "pending_version_id";
    let mock_server = MockServer::start().await;
    create_mock(
        &mock_server,
        target_header::GET_SECRET,
        &format!(r#"{{"SecretId" : "{secret_name}","VersionStage": "AWSCURRENT"}}"#),
        400,
        "asm-get-secret-value-missing-current.json",
    )
    .await;
    let mut secrets_manager = secrets_manager(&mock_server).await;

    let result = secrets_manager
        .create_pending_secret(secret_name, target_version_id, secret_value)
        .await;

    let error_context = DisplayErrorContext(result.unwrap_err()).to_string();
    assert!(error_context.contains("ResourceNotFoundException"));
}

#[tokio::test]
async fn test_create_pending_secret_existing_pending_secret() {
    // arrange
    let secret_name = mock_secrets::EXISTING_PENDING_AND_CURRENT;
    let secret_value = "new_secret_value";
    let target_version_id = "pending_version_id";
    let mock_server = MockServer::start().await;
    create_mock(
        &mock_server,
        target_header::GET_SECRET,
        &format!("{{\"SecretId\" : \"{secret_name}\", \"VersionStage\": \"AWSCURRENT\"}}"),
        200,
        "asm-get-secret-value-existing-pending-and-current-current.json",
    )
    .await;
    create_mock(
        &mock_server,
        target_header::GET_SECRET,
        &format!("{{\"SecretId\" : \"{secret_name}\", \"VersionStage\": \"AWSPENDING\"}}"),
        200,
        "asm-get-secret-value-existing-pending-and-current-pending.json",
    )
    .await;
    let mut secrets_manager = secrets_manager(&mock_server).await;

    // act
    let result = secrets_manager
        .create_pending_secret(secret_name, target_version_id, secret_value)
        .await;

    // assert
    let expected_error_msg =
        format!("Secret {secret_name} already has a version marked as AWSPENDING");
    let actual_error_msg = format!("{}", result.unwrap_err());
    assert_eq!(expected_error_msg, actual_error_msg);
}

#[tokio::test]
async fn test_mark_secret_as_current_success() {
    // arrange
    let secret_name = mock_secrets::CAN_ROTATE;
    let target_version_id = "pending_version_id";
    let response_filename = "asm-update-secret-version-stage-can-rotate.json";
    let mock_server = MockServer::start().await;
    create_mock(
        &mock_server,
        target_header::DESCRIBE_SECRET,
        &format!("{{\"SecretId\" : \"{secret_name}\"}}"),
        200,
        "asm-describe-secret-can-rotate.json",
    )
    .await;
    create_mock(
        &mock_server,
        target_header::UPDATE_SECRET_VERSION,
        &format!(
            r#"{{
            "SecretId" : "{secret_name}",
            "MoveToVersionId" : "{target_version_id}",
            "RemoveFromVersionId" : "current_version_id",
            "VersionStage": "AWSCURRENT"
        }}"#
        ),
        200,
        response_filename,
    )
    .await;
    let mut secrets_manager = secrets_manager(&mock_server).await;

    // act
    let result = secrets_manager
        .mark_secret_as_current(secret_name, target_version_id)
        .await;

    // assert
    assert!(result.is_ok());
}

#[tokio::test]
async fn test_mark_secret_as_current_failure_missing_current_version() {
    let secret_name = mock_secrets::NO_CURRENT_VERSION;
    let target_version_id = "pending_version_id";
    let mock_server = MockServer::start().await;
    create_mock(
        &mock_server,
        target_header::DESCRIBE_SECRET,
        &format!("{{\"SecretId\" : \"{secret_name}\"}}"),
        200,
        "asm-describe-secret-no-current-version.json",
    )
    .await;
    let mut secrets_manager = secrets_manager(&mock_server).await;

    let result = secrets_manager
        .mark_secret_as_current(secret_name, target_version_id)
        .await;

    let expected_error_msg = format!("Secret {secret_name} has no current version id");
    let actual_error_msg = format!("{}", result.unwrap_err());
    assert_eq!(expected_error_msg, actual_error_msg);
}
