pub mod config;
pub mod flag;
pub mod service;

#[derive(Debug, thiserror::Error)]
pub enum Error {
    #[error("Could not build LaunchDarkly client: {0}")]
    Build(#[from] launchdarkly_server_sdk::BuildError),
    #[error("Could not build LaunchDarkly config: {0}")]
    Config(#[from] launchdarkly_server_sdk::ConfigBuildError),
    #[error("Could not initialize LaunchDarkly client: {0}")]
    Initialize(String),
    #[error("Could not create feature flag context: {0}")]
    Context(String),
    #[error("Could not resolve feature flag: {0}")]
    Resolve(String),
}
