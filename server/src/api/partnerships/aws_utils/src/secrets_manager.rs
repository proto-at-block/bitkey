use super::errors::SecretsManagerError as Error;
use async_trait::async_trait;
use aws_config::ConfigLoader;
use aws_config::{meta::region::RegionProviderChain, BehaviorVersion};
use aws_sdk_secretsmanager::{
    error::SdkError,
    operation::{describe_secret::DescribeSecretOutput, get_secret_value::GetSecretValueError},
    Client,
};
use std::collections::HashMap;
use tracing::instrument;

mod label {
    pub const PENDING: &str = "AWSPENDING";
    pub const CURRENT: &str = "AWSCURRENT";
}

mod aws {
    pub const DEFAULT_REGION: &str = "us-west-2";
}

pub struct SecretsManager {
    client: Client,
}

#[async_trait]
pub trait FetchSecret {
    async fn secret_value(&self, secret_name: &str) -> Result<String, Error>;
}

#[async_trait]
pub trait CreatePendingSecret {
    async fn create_pending_secret(
        &mut self,
        secret_name: &str,
        target_version_id: &str,
        secret_value: &str,
    ) -> Result<(), Error>;
}

#[async_trait]
pub trait MarkSecretAsCurrent {
    async fn mark_secret_as_current(
        &mut self,
        secret_name: &str,
        target_version_id: &str,
    ) -> Result<(), Error>;
}

#[async_trait]
pub trait ValidateSecretCanRotate {
    async fn validate_can_rotate(
        &self,
        secret_name: &str,
        target_version_id: &str,
    ) -> Result<(), Error>;
}

#[async_trait]
impl FetchSecret for SecretsManager {
    #[instrument(err, skip(self), level = "trace")]
    async fn secret_value(&self, secret_name: &str) -> Result<String, Error> {
        let response = self
            .client
            .get_secret_value()
            .secret_id(secret_name)
            .send()
            .await?;

        response
            .secret_string
            .ok_or(Error::NotFound(secret_name.to_owned()))
    }
}

#[async_trait]
impl CreatePendingSecret for SecretsManager {
    #[instrument(err, skip(self, secret_value), level = "trace")]
    async fn create_pending_secret(
        &mut self,
        secret_name: &str,
        target_version_id: &str,
        secret_value: &str,
    ) -> Result<(), Error> {
        self.validate_current_secret_exists(secret_name).await?;
        self.validate_pending_secret_does_not_exist(secret_name)
            .await?;

        self.client
            .put_secret_value()
            .secret_id(secret_name)
            .client_request_token(target_version_id)
            .secret_string(secret_value)
            .version_stages(label::PENDING)
            .send()
            .await?;
        Ok(())
    }
}

#[async_trait]
impl MarkSecretAsCurrent for SecretsManager {
    #[instrument(err, skip(self), level = "trace")]
    async fn mark_secret_as_current(
        &mut self,
        secret_name: &str,
        target_version_id: &str,
    ) -> Result<(), Error> {
        let current_version_id = self.current_version_id(secret_name).await?;

        if current_version_id == target_version_id {
            return Ok(());
        }

        self.client
            .update_secret_version_stage()
            .secret_id(secret_name)
            .version_stage(label::CURRENT)
            .move_to_version_id(target_version_id)
            .remove_from_version_id(current_version_id)
            .send()
            .await?;

        Ok(())
    }
}

#[async_trait]
impl ValidateSecretCanRotate for SecretsManager {
    #[instrument(err, skip(self), level = "trace")]
    async fn validate_can_rotate(
        &self,
        secret_name: &str,
        target_version_id: &str,
    ) -> Result<(), Error> {
        let secret_description = self
            .client
            .describe_secret()
            .secret_id(secret_name)
            .send()
            .await?;

        if !secret_description.rotation_enabled.unwrap_or(false) {
            return Err(Error::RotationDisabled(secret_name.to_owned()));
        }

        if !self.version_id_has_label(&secret_description, target_version_id, label::PENDING) {
            return Err(Error::PendingVersionIdNotFound(
                secret_name.to_owned(),
                target_version_id.to_owned(),
            ));
        }

        if self.version_id_has_label(&secret_description, target_version_id, label::CURRENT) {
            return Err(Error::VersionIdAlreadyCurrent(
                secret_name.to_owned(),
                target_version_id.to_owned(),
            ));
        }

        Ok(())
    }
}

impl SecretsManager {
    pub async fn new() -> Self {
        Self::with_endpoint_url(std::env::var("AWS_SDK_ENDPOINT_URL").ok()).await
    }

    pub async fn with_endpoint_url(endpoint_url: Option<String>) -> Self {
        let mut config = aws_config::defaults(BehaviorVersion::latest());
        if let Some(endpoint_url) = endpoint_url {
            config = config.endpoint_url(endpoint_url)
        }
        let region_provider = RegionProviderChain::default_provider().or_else(aws::DEFAULT_REGION);
        config = config.region(region_provider);
        Self::from_config(config).await
    }

    async fn from_config(config: ConfigLoader) -> Self {
        let config = config.load().await;
        let client = Client::new(&config);

        Self { client }
    }

    fn version_id_has_label(
        &self,
        secret_description: &DescribeSecretOutput,
        target_version_id: &str,
        label: &str,
    ) -> bool {
        let version_ids = secret_description
            .clone()
            .version_ids_to_stages
            .unwrap_or_default();
        version_ids
            .get(target_version_id)
            .unwrap_or(&Vec::new())
            .contains(&label.to_owned())
    }

    async fn validate_current_secret_exists(&self, secret_name: &str) -> Result<(), Error> {
        self.client
            .get_secret_value()
            .secret_id(secret_name)
            .version_stage(label::CURRENT)
            .send()
            .await?;

        Ok(())
    }

    async fn validate_pending_secret_does_not_exist(&self, secret_name: &str) -> Result<(), Error> {
        let pending_secret = self
            .client
            .get_secret_value()
            .secret_id(secret_name)
            .version_stage(label::PENDING)
            .send()
            .await;

        match pending_secret {
            Ok(_) => Err(Error::PendingSecretAlreadyExists(secret_name.to_owned())),
            Err(e) => {
                if let SdkError::ServiceError(service_error) = &e {
                    if let GetSecretValueError::ResourceNotFoundException(_) = service_error.err() {
                        return Ok(());
                    }
                }
                Err(Error::from(e))
            }
        }
    }

    async fn current_version_id(&self, secret_name: &str) -> Result<String, Error> {
        let version_ids_to_stages = self
            .client
            .describe_secret()
            .secret_id(secret_name)
            .send()
            .await?
            .version_ids_to_stages
            .unwrap_or(HashMap::new());

        version_ids_to_stages
            .iter()
            .find_map(|(version_id, stages)| {
                if stages.contains(&label::CURRENT.to_owned()) {
                    Some(version_id.to_owned())
                } else {
                    None
                }
            })
            .ok_or(Error::CurrentVersionIdNotFound(secret_name.to_owned()))
    }
}

pub mod test {
    use super::{
        label::{CURRENT, PENDING},
        CreatePendingSecret, FetchSecret, MarkSecretAsCurrent, ValidateSecretCanRotate,
    };
    use crate::errors::SecretsManagerError;
    use async_trait::async_trait;
    use std::collections::HashMap;

    pub struct TestSecretsManager {
        pub secret_values: HashMap<String, String>,
        pub secret_labels: HashMap<String, String>,
    }

    #[async_trait]
    impl FetchSecret for TestSecretsManager {
        async fn secret_value(&self, secret_name: &str) -> Result<String, SecretsManagerError> {
            self.secret_values
                .get(secret_name)
                .ok_or(SecretsManagerError::NotFound(secret_name.to_owned()))
                .cloned()
        }
    }

    #[async_trait]
    impl CreatePendingSecret for TestSecretsManager {
        async fn create_pending_secret(
            &mut self,
            secret_name: &str,
            _target_version_id: &str,
            secret_value: &str,
        ) -> Result<(), SecretsManagerError> {
            self.secret_values
                .insert(secret_name.to_owned(), secret_value.to_owned());
            Ok(())
        }
    }

    #[async_trait]
    impl ValidateSecretCanRotate for TestSecretsManager {
        async fn validate_can_rotate(
            &self,
            secret_name: &str,
            target_version_id: &str,
        ) -> Result<(), SecretsManagerError> {
            let label = self
                .secret_labels
                .get(&(secret_name.to_owned() + target_version_id));
            if label != Some(&PENDING.to_owned()) {
                return Err(SecretsManagerError::PendingVersionIdNotFound(
                    secret_name.to_owned(),
                    target_version_id.to_owned(),
                ));
            }
            Ok(())
        }
    }

    #[async_trait]
    impl MarkSecretAsCurrent for TestSecretsManager {
        async fn mark_secret_as_current(
            &mut self,
            secret_name: &str,
            target_version_id: &str,
        ) -> Result<(), SecretsManagerError> {
            self.secret_labels.insert(
                secret_name.to_owned() + target_version_id,
                CURRENT.to_owned(),
            );
            Ok(())
        }
    }

    impl TestSecretsManager {
        pub fn new(secret_values: HashMap<String, String>) -> Self {
            Self {
                secret_values,
                secret_labels: HashMap::new(),
            }
        }
    }
}
