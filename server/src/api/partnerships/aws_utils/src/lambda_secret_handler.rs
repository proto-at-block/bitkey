use std::result::Result::Ok;

use async_trait::async_trait;
use lambda_runtime::{Error as LambdaError, LambdaEvent};
use serde_json::Value;
use tracing::instrument;

use crate::{
    errors::SecretsManagerError,
    secrets_manager::{CreatePendingSecret, MarkSecretAsCurrent},
};

use super::secrets_manager::ValidateSecretCanRotate;

#[async_trait]
pub trait SecretCreator {
    async fn create(&self, arn: &str) -> Result<String, LambdaError>;
}

mod step {
    pub const CREATE: &str = "createSecret";
    pub const SET: &str = "setSecret";
    pub const TEST: &str = "testSecret";
    pub const FINISH: &str = "finishSecret";
}

#[instrument(err, skip(secrets_manager, secret_creator))]
pub async fn rotate(
    event: LambdaEvent<Value>,
    secrets_manager: &mut (impl ValidateSecretCanRotate + CreatePendingSecret + MarkSecretAsCurrent),
    secret_creator: &impl SecretCreator,
) -> Result<(), LambdaError> {
    let (event, _context) = event.into_parts();
    let arn = event["SecretId"]
        .as_str()
        .ok_or(LambdaError::from("No SecretId received"))?;
    let token = event["ClientRequestToken"]
        .as_str()
        .ok_or(LambdaError::from("No ClientRequestToken received"))?;
    let step = event["Step"]
        .as_str()
        .ok_or(LambdaError::from("No Step received"))?;

    secrets_manager.validate_can_rotate(arn, token).await?;

    match step {
        step::CREATE => create_secret(secrets_manager, secret_creator, arn, token).await?,
        step::SET => set_secret(),
        step::TEST => test_secret(),
        step::FINISH => finish_secret(secrets_manager, arn, token).await?,
        _ => Err(LambdaError::from(format!("Invalid step: {step}")))?,
    }

    Ok(())
}

#[instrument(err, skip(secrets_manager, secret_creator), level = "trace")]
async fn create_secret(
    secrets_manager: &mut impl CreatePendingSecret,
    secret_creator: &impl SecretCreator,
    arn: &str,
    token: &str,
) -> Result<(), LambdaError> {
    let new_secret_string = secret_creator.create(arn).await?;

    secrets_manager
        .create_pending_secret(arn, token, &new_secret_string)
        .await?;

    Ok(())
}

#[instrument(level = "trace")]
fn set_secret() {}

#[instrument(level = "trace")]
fn test_secret() {}

#[instrument(err, skip(secrets_manager), level = "trace")]
async fn finish_secret(
    secrets_manager: &mut impl MarkSecretAsCurrent,
    arn: &str,
    token: &str,
) -> Result<(), SecretsManagerError> {
    secrets_manager.mark_secret_as_current(arn, token).await
}

#[cfg(test)]
pub mod tests {
    use std::collections::HashMap;

    use async_trait::async_trait;
    use lambda_runtime::{Error as LambdaError, LambdaEvent};
    use serde_json::Value;

    use crate::{
        lambda_secret_handler::rotate,
        secrets_manager::{test::TestSecretsManager, FetchSecret},
    };

    use super::SecretCreator;

    pub struct TestSecretCreator {
        secrets: HashMap<String, String>,
    }

    impl TestSecretCreator {
        pub fn new(secrets: HashMap<String, String>) -> Self {
            TestSecretCreator { secrets }
        }
    }

    #[async_trait]
    impl SecretCreator for TestSecretCreator {
        async fn create(&self, arn: &str) -> Result<String, LambdaError> {
            self.secrets
                .get(arn)
                .ok_or(LambdaError::from("error"))
                .cloned()
        }
    }

    fn test_lambda_event(arn: &str, request_token: &str, step: &str) -> LambdaEvent<Value> {
        let lambda_input = format!(
            r#"{{
                "SecretId": "{arn}",
                "ClientRequestToken": "{request_token}",
                "Step": "{step}"
            }}"#
        );
        let lambda_input =
            serde_json::from_str(lambda_input.as_str()).expect("hardcoded json should parse");
        let context = lambda_runtime::Context::default();

        lambda_runtime::LambdaEvent::new(lambda_input, context)
    }

    #[tokio::test]
    async fn key_rotation_create_success() {
        // arrange
        let arn = "arn:aws:secretsmanager:us-east-2:58711127516:secret:a_secret-7RcRXv";
        let request_token = "pending_version_id";
        let step = "createSecret";
        let event = test_lambda_event(arn, request_token, step);

        let mut secrets_manager = TestSecretsManager::new(HashMap::new());
        secrets_manager
            .secret_labels
            .insert(arn.to_owned() + request_token, "AWSPENDING".to_owned());

        let new_secret_value = "new_secret";
        let secret_creator = TestSecretCreator::new(HashMap::from([(
            arn.to_owned(),
            new_secret_value.to_owned(),
        )]));

        // act
        let result = rotate(event, &mut secrets_manager, &secret_creator).await;

        // assert
        assert!(result.is_ok());
        let created_secret = secrets_manager.secret_value(arn).await.unwrap();
        assert_eq!(created_secret, new_secret_value);
    }

    #[tokio::test]
    async fn key_rotation_create_failure_cannot_rotate() {
        let arn = "arn:aws:secretsmanager:us-east-2:58711127516:secret:a_secret-7RcRXv";
        let request_token = "pending_version_id";
        let step = "createSecret";
        let event = test_lambda_event(arn, request_token, step);
        let mut secrets_manager = TestSecretsManager::new(HashMap::new());
        let secret_creator = TestSecretCreator::new(HashMap::new());

        let result = rotate(event, &mut secrets_manager, &secret_creator).await;

        assert!(result.is_err());
        let error_message = result.unwrap_err().to_string();
        let expected_error_message =
            "Secret arn:aws:secretsmanager:us-east-2:58711127516:secret:a_secret-7RcRXv \
            has no pending version id matching target pending_version_id";
        assert_eq!(error_message, expected_error_message);
    }

    #[tokio::test]
    async fn key_rotation_finish_secret_success() {
        // arrange
        let arn = "arn:aws:secretsmanager:us-east-2:58711127516:secret:a_secret-7RcRXv";
        let request_token = "pending_version_id";
        let step = "finishSecret";
        let event = test_lambda_event(arn, request_token, step);

        let mut secrets_manager = TestSecretsManager::new(HashMap::new());
        secrets_manager
            .secret_labels
            .insert(arn.to_owned() + request_token, "AWSPENDING".to_owned());

        let secret_creator = TestSecretCreator::new(HashMap::new());

        // act
        let result = rotate(event, &mut secrets_manager, &secret_creator).await;

        // assert
        assert!(result.is_ok());
        let secret_label_values: Vec<String> =
            secrets_manager.secret_labels.values().cloned().collect();
        assert!(secret_label_values.contains(&"AWSCURRENT".to_owned()));
    }
}
