use isocountry::CountryCode;
use serde::{Deserialize, Serialize};

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct SmsPayload {
    message: String,
    #[serde(default)]
    pub unsupported_country_codes: Option<Vec<CountryCode>>,
}

impl SmsPayload {
    pub fn new(message: String, unsupported_country_codes: Option<Vec<CountryCode>>) -> Self {
        Self {
            message,
            unsupported_country_codes,
        }
    }

    pub fn message(&self) -> String {
        if self.message.starts_with("Bitkey:") || self.message.starts_with("[Bitkey]") {
            self.message.clone()
        } else {
            format!("Bitkey: {}", &self.message)
        }
    }
}
