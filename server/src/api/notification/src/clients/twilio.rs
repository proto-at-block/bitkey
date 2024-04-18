use std::collections::HashMap;
use std::env;
use std::sync::OnceLock;

use ::metrics::KeyValue;
use ::metrics::ResultCounter;
use base64::{engine::general_purpose::STANDARD as b64, Engine as _};
use bimap::BiMap;
use hmac::{Hmac, Mac};
use isocountry::CountryCode;
use itertools::Itertools;
use reqwest::Client;
use reqwest::StatusCode;
use serde::Deserialize;
use sha1::Sha1;
use strum_macros::EnumString;
use tracing::event;
use tracing::instrument;
use tracing::Level;

use crate::clients::error::NotificationClientsError;
use crate::metrics;

const TWILIO_API_URL: &str = "https://api.twilio.com/2010-04-01/";

static TWILIO_SUPPORTED_SMS_COUNTRIES: OnceLock<BiMap<CountryCode, &'static str>> = OnceLock::new();

fn init_twilio_supported_sms_countries() -> BiMap<CountryCode, &'static str> {
    let mut map = BiMap::new();
    map.insert(CountryCode::ARE, "971");
    map.insert(CountryCode::ARG, "54");
    map.insert(CountryCode::AUS, "61");
    map.insert(CountryCode::AUT, "43");
    map.insert(CountryCode::BRA, "55");
    map.insert(CountryCode::CHE, "41");
    map.insert(CountryCode::CHL, "56");
    map.insert(CountryCode::CMR, "237");
    map.insert(CountryCode::COL, "57");
    map.insert(CountryCode::CRI, "506");
    map.insert(CountryCode::CZE, "420");
    map.insert(CountryCode::DEU, "49");
    map.insert(CountryCode::DNK, "45");
    map.insert(CountryCode::ECU, "593");
    map.insert(CountryCode::ESP, "34");
    map.insert(CountryCode::FRA, "33");
    map.insert(CountryCode::GBR, "44");
    map.insert(CountryCode::GHA, "233");
    map.insert(CountryCode::HUN, "36");
    map.insert(CountryCode::IDN, "62");
    map.insert(CountryCode::IND, "91");
    map.insert(CountryCode::IRL, "353");
    map.insert(CountryCode::ISR, "972");
    map.insert(CountryCode::ITA, "39");
    map.insert(CountryCode::JPN, "81");
    map.insert(CountryCode::KAZ, "7");
    map.insert(CountryCode::KEN, "254");
    map.insert(CountryCode::KOR, "82");
    map.insert(CountryCode::LTU, "370");
    map.insert(CountryCode::MEX, "52");
    map.insert(CountryCode::MYS, "60");
    map.insert(CountryCode::NGA, "234");
    map.insert(CountryCode::NLD, "31");
    map.insert(CountryCode::NOR, "47");
    map.insert(CountryCode::NZL, "64");
    map.insert(CountryCode::PHL, "63");
    map.insert(CountryCode::POL, "48");
    map.insert(CountryCode::PRT, "351");
    map.insert(CountryCode::ROU, "40");
    map.insert(CountryCode::SGP, "65");
    map.insert(CountryCode::SLV, "503");
    map.insert(CountryCode::SWE, "46");
    map.insert(CountryCode::THA, "66");
    map.insert(CountryCode::TUR, "90");
    map.insert(CountryCode::TWN, "886");
    map.insert(CountryCode::UGA, "256");
    map.insert(CountryCode::URY, "598");
    map.insert(CountryCode::VNM, "84");
    map.insert(CountryCode::ZAF, "27");
    map
}

fn get_twilio_supported_sms_countries() -> &'static BiMap<CountryCode, &'static str> {
    TWILIO_SUPPORTED_SMS_COUNTRIES.get_or_init(init_twilio_supported_sms_countries)
}

#[derive(Deserialize, EnumString, Clone)]
#[serde(rename_all = "lowercase", tag = "mode")]
pub enum TwilioMode {
    Test,
    Environment {
        default_messaging_service_sid: String,
        messaging_service_sid_overrides: Option<HashMap<CountryCode, String>>,
        #[serde(default)]
        status_callback_override: Option<String>,
    },
}

impl TwilioMode {
    pub fn to_client(&self) -> TwilioClient {
        TwilioClient::new(self.to_owned())
    }
}

#[derive(Clone)]
pub enum TwilioClient {
    Real {
        endpoint: reqwest::Url,
        client: Client,
        account_sid: String,
        auth_token: String,
        key_sid: String,
        key_secret: String,
        default_messaging_service_sid: String,
        messaging_service_sid_overrides: HashMap<CountryCode, String>,
        status_callback: String,
    },
    Test,
}

#[derive(Deserialize)]
pub struct LookupResponse {
    pub country_code: Option<String>,
    pub phone_number: String,
    pub valid: bool,
}

impl TwilioClient {
    pub fn new(mode: TwilioMode) -> Self {
        match mode {
            TwilioMode::Environment {
                default_messaging_service_sid,
                messaging_service_sid_overrides,
                status_callback_override,
            } => Self::Real {
                endpoint: reqwest::Url::parse(TWILIO_API_URL).unwrap(),
                client: Client::new(),
                account_sid: env::var("TWILIO_ACCOUNT_SID")
                    .expect("TWILIO_ACCOUNT_SID environment variable not set"),
                auth_token: env::var("TWILIO_AUTH_TOKEN")
                    .expect("TWILIO_AUTH_TOKEN environment variable not set"),
                key_sid: env::var("TWILIO_KEY_SID")
                    .expect("TWILIO_KEY_SID environment variable not set"),
                key_secret: env::var("TWILIO_KEY_SECRET")
                    .expect("TWILIO_KEY_SECRET environment variable not set"),
                default_messaging_service_sid,
                messaging_service_sid_overrides: messaging_service_sid_overrides
                    .unwrap_or_default(),
                status_callback: status_callback_override.unwrap_or(format!(
                    "{}/api/twilio/status-callback",
                    env::var("SERVER_FROMAGERIE_ENDPOINT")
                        .expect("SERVER_FROMAGERIE_ENDPOINT environment variable not set")
                )),
            },
            TwilioMode::Test => Self::Test,
        }
    }

    #[instrument(skip(self))]
    pub async fn create_message(
        &self,
        country_code: CountryCode,
        to: String,
        body: String,
    ) -> Result<(), NotificationClientsError> {
        if !self.is_supported_sms_country_code(country_code) {
            event!(
                Level::ERROR,
                "Country code not supported for SMS: {}",
                country_code,
            );
            return Err(NotificationClientsError::TwilioUnsupportedSmsCountryCodeError);
        }

        match self {
            Self::Real {
                endpoint,
                client,
                account_sid,
                key_sid,
                key_secret,
                default_messaging_service_sid,
                messaging_service_sid_overrides,
                status_callback,
                ..
            } => {
                let mut params = HashMap::new();
                params.insert("To", to);
                params.insert("Body", body);

                let mut messaging_service_sid = default_messaging_service_sid;
                if let Some(messaging_service_sid_override) =
                    messaging_service_sid_overrides.get(&country_code)
                {
                    messaging_service_sid = messaging_service_sid_override;
                }

                params.insert("MessagingServiceSid", messaging_service_sid.to_owned());
                params.insert("StatusCallback", status_callback.clone());

                let response = client
                    .post(
                        endpoint
                            .join(&format!("Accounts/{account_sid}/Messages.json"))
                            .unwrap(),
                    )
                    .basic_auth(key_sid.clone(), Some(key_secret.clone()))
                    .form(&params)
                    .send()
                    .await?;

                if response.status() != StatusCode::CREATED {
                    Err(NotificationClientsError::TwilioCreateMessageError)
                } else {
                    Ok(())
                }
            }
            .count_result(
                &metrics::TWILIO_CREATE_MESSAGE_ATTEMPT,
                &metrics::TWILIO_CREATE_MESSAGE_FAILURE,
                &[KeyValue::new(
                    metrics::COUNTRY_CODE_KEY,
                    country_code.alpha2(),
                )],
            ),
            Self::Test => Ok(()),
        }
    }

    #[instrument(skip(self))]
    pub async fn lookup(
        &self,
        phone_number: String,
    ) -> Result<LookupResponse, NotificationClientsError> {
        match self {
            Self::Real {
                client,
                key_sid,
                key_secret,
                ..
            } => {
                let response = client
                    .get(format!(
                        "https://lookups.twilio.com/v2/PhoneNumbers/{phone_number}"
                    ))
                    .basic_auth(key_sid.clone(), Some(key_secret.clone()))
                    .send()
                    .await?;

                if response.status() != StatusCode::OK {
                    return Err(NotificationClientsError::TwilioLookupError);
                }

                Ok(response.json::<LookupResponse>().await?)
            }
            Self::Test => {
                let country_code = if phone_number.starts_with("+1") {
                    Some(CountryCode::as_array().first().unwrap().alpha2().to_owned())
                } else {
                    Some(CountryCode::as_array().last().unwrap().alpha2().to_owned())
                };

                let valid = phone_number.starts_with('+');

                Ok(LookupResponse {
                    country_code,
                    phone_number,
                    valid,
                })
            }
        }
    }

    pub fn is_supported_sms_country_code(&self, country_code: CountryCode) -> bool {
        match self {
            Self::Real { .. } => get_twilio_supported_sms_countries()
                .get_by_left(&country_code)
                .is_some(),
            Self::Test => country_code == *CountryCode::as_array().first().unwrap(),
        }
    }

    #[instrument(skip(self))]
    pub fn validate_callback_signature(
        &self,
        request: &HashMap<String, String>,
        signature: String,
    ) -> Result<(), NotificationClientsError> {
        match self {
            Self::Real {
                auth_token,
                status_callback,
                ..
            } => {
                // https://github.com/twilio/twilio-python/blob/main/twilio/request_validator.py

                let to_sign = request
                    .iter()
                    .sorted_by(|a, b| Ord::cmp(a.0, b.0))
                    .fold(status_callback.clone(), |acc, (key, value)| {
                        format!("{}{}{}", acc, key, value)
                    });

                let mut mac = Hmac::<Sha1>::new_from_slice(auth_token.as_bytes())?;
                mac.update(to_sign.as_bytes());
                mac.verify_slice(&b64.decode(signature)?)?;

                Ok(())
            }
            Self::Test => match signature.as_str() {
                "VALID" => Ok(()),
                _ => Err(NotificationClientsError::MacError(hmac::digest::MacError)),
            },
        }
    }
}

pub fn find_supported_sms_country_code(phone_number: String) -> Option<CountryCode> {
    let (start, mut end) = (1, 5);

    // Find the country whose calling code is the longest matching prefix for the phone number
    //   Do this by iteratively searching the map by the first N digits of the phone number,
    //   decreasing by 1 each time a match isn't found. Start at a prefix length of 4; the longest
    //   possible calling code.
    // Ex: +12645555555; map.get(1264) => Anguilla
    // Ex: +12065555555; map.get(1206) => None, map.get(120) => None, map.get(12) => None, map.get(1) => USA
    let mut result = None;
    while phone_number.len() >= end && end > start {
        let search = &phone_number[start..end];
        result = get_twilio_supported_sms_countries().get_by_right(search);
        if result.is_some() {
            break;
        }
        end -= 1;
    }

    result.copied()
}

#[cfg(test)]
mod tests {
    use isocountry::CountryCode;

    use crate::clients::twilio::{find_supported_sms_country_code, TwilioClient};
    use std::collections::HashMap;

    #[test]
    fn test_validate_callback_signature() {
        let client = TwilioClient::Real {
            endpoint: reqwest::Url::try_from("http://example.com").unwrap(),
            client: reqwest::Client::new(),
            account_sid: "".to_string(),
            auth_token: "12345".to_string(),
            key_sid: "".to_string(),
            key_secret: "".to_string(),
            default_messaging_service_sid: "".to_string(),
            messaging_service_sid_overrides: HashMap::new(),
            status_callback: "https://mycompany.com/myapp.php?foo=1&bar=2".to_string(),
        };

        // https://www.twilio.com/docs/usage/security#explore-the-algorithm-yourself
        let request = HashMap::from([
            ("Digits".to_string(), "1234".to_string()),
            ("To".to_string(), "+18005551212".to_string()),
            ("From".to_string(), "+14158675310".to_string()),
            ("Caller".to_string(), "+14158675310".to_string()),
            ("CallSid".to_string(), "CA1234567890ABCDE".to_string()),
        ]);

        let result = client
            .validate_callback_signature(&request, "GvWf1cFY/Q7PnoempGyD5oXAezc=".to_string());
        assert!(result.is_ok(), "{:?}", result);
    }

    #[test]
    fn test_find_supported_sms_country_code() {
        assert_eq!(
            find_supported_sms_country_code("+353855555555".to_string()),
            Some(CountryCode::IRL)
        );
        assert_eq!(
            find_supported_sms_country_code("+351655555555".to_string()),
            Some(CountryCode::PRT)
        );
        assert_eq!(
            find_supported_sms_country_code("+237255555555".to_string()),
            Some(CountryCode::CMR)
        );
        assert_eq!(
            find_supported_sms_country_code("+233255555555".to_string()),
            Some(CountryCode::GHA)
        );
        assert_eq!(
            find_supported_sms_country_code("+2348555555555".to_string()),
            Some(CountryCode::NGA)
        );
        assert_eq!(
            find_supported_sms_country_code("+50645555555".to_string()),
            Some(CountryCode::CRI)
        );
        assert_eq!(
            find_supported_sms_country_code("+50365555555".to_string()),
            Some(CountryCode::SLV)
        );
        assert_eq!(
            find_supported_sms_country_code("+254555555555".to_string()),
            Some(CountryCode::KEN)
        );
        assert_eq!(
            find_supported_sms_country_code("+256455555555".to_string()),
            Some(CountryCode::UGA)
        );
        assert_eq!(
            find_supported_sms_country_code("+59355555555".to_string()),
            Some(CountryCode::ECU)
        );
        assert_eq!(
            find_supported_sms_country_code("+5984555555555".to_string()),
            Some(CountryCode::URY)
        );
    }
}
