use std::time::Duration;

use aws_config::{timeout::TimeoutConfig, BehaviorVersion};
use aws_types::SdkConfig;

const CONNECT_TIMEOUT: Duration = Duration::from_secs(3);
const READ_TIMEOUT: Duration = Duration::from_secs(5);
const OPERATION_ATTEMPT_TIMEOUT: Duration = Duration::from_secs(10);
const OPERATION_TIMEOUT: Duration = Duration::from_secs(30);

pub fn default_timeout_config() -> TimeoutConfig {
    TimeoutConfig::builder()
        .connect_timeout(CONNECT_TIMEOUT)
        .read_timeout(READ_TIMEOUT)
        .operation_attempt_timeout(OPERATION_ATTEMPT_TIMEOUT)
        .operation_timeout(OPERATION_TIMEOUT)
        .build()
}

// Drop-in replacement for `aws_config::load_defaults(BehaviorVersion::latest()).await`
// that also applies our shared timeout config so requests cannot hang forever.
pub async fn load_default_sdk_config() -> SdkConfig {
    aws_config::defaults(BehaviorVersion::latest())
        .timeout_config(default_timeout_config())
        .load()
        .await
}
