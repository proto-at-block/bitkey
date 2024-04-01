use std::{collections::HashMap, env};

use launchdarkly_server_sdk::{Client, ConfigBuilder};
use rust_embed::RustEmbed;
use serde::Deserialize;

use crate::{service::Service, Error};

#[derive(RustEmbed)]
#[folder = "$CARGO_MANIFEST_DIR/overrides"]
#[include = "*.toml"]
struct Asset;

#[derive(Deserialize)]
pub struct Config {
    launchdarkly: Mode,
    feature_flag_overrides: Option<OverrideMode>,
}

impl Config {
    pub fn new_with_overrides(overrides: HashMap<String, String>) -> Self {
        Self {
            launchdarkly: Mode::Test,
            feature_flag_overrides: Some(OverrideMode::Object(overrides)),
        }
    }
}

#[derive(Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum OverrideMode {
    File(String),
    Object(HashMap<String, String>),
}

#[derive(Clone, Deserialize, PartialEq)]
#[serde(rename_all = "lowercase")]
pub enum Mode {
    Test,
    Environment,
}

impl Config {
    pub async fn to_service(self) -> Result<Service, Error> {
        let mode = self.launchdarkly;
        let config = match mode {
            Mode::Test => ConfigBuilder::new("fake-launchdarkly-sdk-key")
                .offline(true)
                .build(),
            Mode::Environment => {
                let sdk_key = env::var("LAUNCHDARKLY_SDK_KEY")
                    .expect("LAUNCHDARKLY_SDK_KEY environment variable not set");
                ConfigBuilder::new(&sdk_key).build()
            }
        }?;

        let client = Client::build(config)?;
        client.start_with_default_executor();
        if !client.initialized_async().await {
            return Err(Error::Initialize("see logs for details".to_string()));
        }

        let overrides: HashMap<String, String> = match self.feature_flag_overrides {
            Some(OverrideMode::File(file_name)) => parse_toml_overrides(file_name)?,
            Some(OverrideMode::Object(map)) => map,
            _ => HashMap::new(),
        };

        Ok(Service::new(client, mode, overrides))
    }
}

fn parse_toml_overrides(file: String) -> Result<HashMap<String, String>, Error> {
    let file = &format!("{file}.toml");
    let config = Asset::get(file)
        .ok_or_else(|| Error::Initialize(format!("file {file} not found")))?
        .data;
    let config: HashMap<String, String> =
        toml::from_str(std::str::from_utf8(config.as_ref()).expect("utf-8 string"))
            .map_err(|e| Error::Initialize(format!("Could not read {file} file: {e:?}")))?;
    Ok(config)
}
