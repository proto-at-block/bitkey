use aws_config::{BehaviorVersion, SdkConfig};
use aws_credential_types::provider::SharedCredentialsProvider;
use aws_credential_types::Credentials;
use aws_sdk_dynamodb::client::Client;
use aws_sdk_dynamodb::config::Builder;
use aws_types::region::Region;

pub fn new_client(sdk_config: &SdkConfig, endpoint: &Option<String>) -> Client {
    match endpoint {
        Some(uri) => {
            let config = Builder::from(&build_fake_sdk_config())
                .endpoint_url(uri)
                .build();
            Client::from_conf(config)
        }
        None => Client::new(sdk_config),
    }
}

fn build_fake_sdk_config() -> SdkConfig {
    SdkConfig::builder()
        .behavior_version(BehaviorVersion::latest())
        .region(Region::new("us-east-1")) // The default region for DynamoDB Local is us-east-1
        .credentials_provider(SharedCredentialsProvider::new(Credentials::new(
            "awsaccesskeyid",
            "awssecretaccesskey",
            None,
            None,
            "TEST_CREDENTIALS_PROVIDER",
        )))
        .build()
}
