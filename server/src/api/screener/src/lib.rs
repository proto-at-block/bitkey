use serde::Deserialize;
use strum_macros::EnumString;

mod sanctions;
pub mod screening;
pub mod service;

#[derive(Deserialize)]
pub struct Config {
    pub screener: ScreenerMode,
}

#[derive(Deserialize, EnumString, Clone)]
#[serde(rename_all = "lowercase", tag = "mode")]
pub enum ScreenerMode {
    Test,
    S3,
}
