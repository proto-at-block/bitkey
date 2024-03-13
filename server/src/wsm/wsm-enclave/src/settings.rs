use config::{Config, ConfigError, Environment, File};
use serde::Deserialize;
use std::env;
use std::net::IpAddr;

#[derive(Clone, Debug, Deserialize, PartialEq, Copy)]
#[serde(rename_all = "lowercase")]
pub enum RunMode {
    Test,
    Development,
    Staging,
    Production,
}

//These fields serialize into camelCase to match Cfn outputs, which cannot have underscores in them.
#[derive(Clone, Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Settings {
    pub run_mode: RunMode,
    pub address: IpAddr,
    pub port: u16,
}

impl Settings {
    pub fn new() -> Result<Self, ConfigError> {
        let run_mode = env::var("ROCKET_PROFILE").unwrap_or_else(|_| "development".into());
        let port = env::var("ROCKET_PORT")
            .unwrap_or_else(|_| "8446".into())
            .parse::<u16>()
            .unwrap();
        let address = env::var("ROCKET_ADDRESS").unwrap_or_else(|_| "0.0.0.0".into());
        let s = Config::builder()
            .add_source(File::with_name("config/default").required(false))
            .add_source(File::with_name(&format!("config/{run_mode}")).required(false))
            .add_source(Environment::with_prefix("wsm"))
            .set_default("runMode", run_mode)?
            .set_default("port", port)?
            .set_default("address", address)?
            .build()?;
        s.try_deserialize()
    }
}
