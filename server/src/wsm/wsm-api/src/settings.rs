use config::{Config, ConfigError, Environment, File};
use serde::Deserialize;
use std::env;
use std::net::IpAddr;

#[derive(Clone, Debug, Deserialize, PartialEq)]
#[serde(rename_all = "lowercase")]
pub enum RunMode {
    Test,
    Development,
    Staging,
    Production,
}

//These fields serialize into camelCase to match Cfn outputs, which cannot have underscores in them.
#[derive(Debug, Deserialize, Clone)]
#[serde(rename_all = "camelCase")]
pub struct Settings {
    pub dek_table_name: String,
    pub customer_keys_table_name: String,
    pub customer_key_shares_table_name: String,
    pub enclave_endpoint: String,
    pub address: IpAddr,
    pub kms_proxy_port: u32,
    pub port: u16,
    pub cmk_id: String,
    pub dynamodb_endpoint: Option<String>,
    pub run_mode: RunMode,
}

impl Settings {
    pub fn new() -> Result<Self, ConfigError> {
        let run_mode = env::var("ROCKET_PROFILE").unwrap_or_else(|_| "development".into());
        let port = env::var("ROCKET_PORT")
            .unwrap_or_else(|_| "8000".into())
            .parse::<i16>()
            .unwrap();
        let s = Config::builder()
            .add_source(File::with_name("config/default").required(false))
            .add_source(File::with_name(&format!("config/{run_mode}")).required(false))
            .add_source(Environment::with_prefix("wsm"))
            .set_default("runMode", run_mode)?
            .set_default("port", port)?
            .build()?;
        tracing::debug!("Loaded Settings: {:?}", s);
        s.try_deserialize()
    }
}
