use std::env;

use errors::ApiError;
use serde::Deserialize;
use strum_macros::EnumString;

#[derive(Clone, Debug, Deserialize, EnumString)]
#[serde(rename_all = "lowercase", tag = "mode")]
pub enum WebhookMode {
    Test,
    Environment,
}

impl WebhookMode {
    pub fn to_validator(&self) -> WebhookValidator {
        let expected_api_key = match self {
            WebhookMode::Environment => {
                env::var("WEBHOOK_API_KEY").expect("WEBHOOK_API_KEY environment variable not set")
            }
            WebhookMode::Test => "FAKE_API_KEY".to_owned(),
        };
        WebhookValidator { expected_api_key }
    }
}

#[derive(Clone)]
pub struct WebhookValidator {
    expected_api_key: String,
}

impl WebhookValidator {
    pub fn validate(&self, api_key: &str) -> Result<(), ApiError> {
        if api_key != self.expected_api_key {
            return Err(ApiError::GenericUnauthorized("Invalid API key".to_owned()));
        }
        Ok(())
    }
}
