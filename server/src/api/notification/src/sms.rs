use isocountry::CountryCode;
use serde::{Deserialize, Serialize};

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct SmsPayload {
    pub message: String,
    #[serde(default)]
    pub unsupported_country_codes: Option<Vec<CountryCode>>,
}
