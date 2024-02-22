pub mod ddb;

use aws_credential_types::provider::SharedCredentialsProvider;
use aws_credential_types::Credentials;
use aws_types::SdkConfig;
use serde::Deserialize;

pub use aws_sdk_dynamodb;
pub use serde_dynamo;

#[derive(Deserialize, Clone)]
#[serde(rename_all = "lowercase")]
pub enum DBMode {
    Endpoint(String),
    Test,
    Environment,
}

fn build_fake_sdk_config() -> SdkConfig {
    SdkConfig::builder()
        .credentials_provider(SharedCredentialsProvider::new(Credentials::new(
            "awsaccesskeyid",
            "awssecretaccesskey",
            None,
            None,
            "TEST_CREDENTIALS_PROVIDER",
        )))
        .build()
}
