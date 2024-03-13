use serde::Deserialize;
use std::str::FromStr;
use strum_macros::EnumString;

pub mod s3_util;
mod sanctions_screener;
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

/// Path representing either a file in S3 or a local filesystem.
pub(crate) enum ObjectPath {
    S3(String),
    Local(String),
}

impl FromStr for ObjectPath {
    type Err = &'static str;

    fn from_str(s: &str) -> Result<Self, Self::Err> {
        if s.starts_with("s3://") {
            Ok(ObjectPath::S3(s.to_string()))
        } else if s.starts_with("file://") {
            Ok(ObjectPath::Local(s.to_string()))
        } else {
            Err("Invalid object path")
        }
    }
}
