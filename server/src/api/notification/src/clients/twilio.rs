use std::collections::HashMap;
use std::env;
use std::sync::OnceLock;

use base64::{engine::general_purpose::STANDARD as b64, Engine as _};
use hmac::{Hmac, Mac};
use instrumentation::metrics::KeyValue;
use instrumentation::metrics::ResultCounter;
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

// The values of the map are the country codes we support for SMS notifications
// and correspond with the countries enabled within Twilio console.
// The keys of the map allow us to determine the country code of the phone number
// for metrics reporting purposes only; in normal business logic, the country code
// of the phone number is determined by Twilio lookup API. They do not report the
// country code when they use our webhook to report success metrics, so we use this
// map to attribute the metric to a specific country.
static SUPPORTED_PREFIX_TO_COUNTRY_CODE: OnceLock<HashMap<&'static str, CountryCode>> =
    OnceLock::new();

fn init_twilio_supported_sms_countries() -> HashMap<&'static str, CountryCode> {
    let mut map = HashMap::new();
    map.insert("54", CountryCode::ARG);
    map.insert("61", CountryCode::AUS);
    map.insert("43", CountryCode::AUT);
    map.insert("55", CountryCode::BRA);

    // Make an entry for each Canadian area code so we can differntiate between USA and CAN.
    // Since USA and CAN share country code prefix, we need the Canadian area codes
    // to recognize a Canadian phone number.
    map.insert("1204", CountryCode::CAN);
    map.insert("1226", CountryCode::CAN);
    map.insert("1236", CountryCode::CAN);
    map.insert("1249", CountryCode::CAN);
    map.insert("1250", CountryCode::CAN);
    map.insert("1257", CountryCode::CAN);
    map.insert("1263", CountryCode::CAN);
    map.insert("1289", CountryCode::CAN);
    map.insert("1306", CountryCode::CAN);
    map.insert("1343", CountryCode::CAN);
    map.insert("1354", CountryCode::CAN);
    map.insert("1365", CountryCode::CAN);
    map.insert("1367", CountryCode::CAN);
    map.insert("1368", CountryCode::CAN);
    map.insert("1382", CountryCode::CAN);
    map.insert("1387", CountryCode::CAN);
    map.insert("1403", CountryCode::CAN);
    map.insert("1416", CountryCode::CAN);
    map.insert("1418", CountryCode::CAN);
    map.insert("1428", CountryCode::CAN);
    map.insert("1431", CountryCode::CAN);
    map.insert("1437", CountryCode::CAN);
    map.insert("1438", CountryCode::CAN);
    map.insert("1450", CountryCode::CAN);
    map.insert("1460", CountryCode::CAN);
    map.insert("1468", CountryCode::CAN);
    map.insert("1474", CountryCode::CAN);
    map.insert("1506", CountryCode::CAN);
    map.insert("1514", CountryCode::CAN);
    map.insert("1519", CountryCode::CAN);
    map.insert("1537", CountryCode::CAN);
    map.insert("1548", CountryCode::CAN);
    map.insert("1568", CountryCode::CAN);
    map.insert("1579", CountryCode::CAN);
    map.insert("1581", CountryCode::CAN);
    map.insert("1584", CountryCode::CAN);
    map.insert("1587", CountryCode::CAN);
    map.insert("1604", CountryCode::CAN);
    map.insert("1613", CountryCode::CAN);
    map.insert("1639", CountryCode::CAN);
    map.insert("1647", CountryCode::CAN);
    map.insert("1672", CountryCode::CAN);
    map.insert("1683", CountryCode::CAN);
    map.insert("1705", CountryCode::CAN);
    map.insert("1709", CountryCode::CAN);
    map.insert("1742", CountryCode::CAN);
    map.insert("1753", CountryCode::CAN);
    map.insert("1778", CountryCode::CAN);
    map.insert("1780", CountryCode::CAN);
    map.insert("1782", CountryCode::CAN);
    map.insert("1807", CountryCode::CAN);
    map.insert("1819", CountryCode::CAN);
    map.insert("1825", CountryCode::CAN);
    map.insert("1851", CountryCode::CAN);
    map.insert("1867", CountryCode::CAN);
    map.insert("1873", CountryCode::CAN);
    map.insert("1879", CountryCode::CAN);
    map.insert("1902", CountryCode::CAN);
    map.insert("1905", CountryCode::CAN);
    map.insert("1942", CountryCode::CAN);

    map.insert("41", CountryCode::CHE);
    map.insert("56", CountryCode::CHL);
    map.insert("237", CountryCode::CMR);
    map.insert("57", CountryCode::COL);
    map.insert("506", CountryCode::CRI);
    map.insert("420", CountryCode::CZE);
    map.insert("49", CountryCode::DEU);
    map.insert("45", CountryCode::DNK);
    map.insert("593", CountryCode::ECU);
    map.insert("34", CountryCode::ESP);
    map.insert("33", CountryCode::FRA);
    map.insert("44", CountryCode::GBR);
    map.insert("233", CountryCode::GHA);
    map.insert("36", CountryCode::HUN);
    map.insert("62", CountryCode::IDN);
    map.insert("91", CountryCode::IND);
    map.insert("353", CountryCode::IRL);
    map.insert("972", CountryCode::ISR);
    map.insert("39", CountryCode::ITA);
    map.insert("81", CountryCode::JPN);
    map.insert("7", CountryCode::KAZ);
    map.insert("254", CountryCode::KEN);
    map.insert("82", CountryCode::KOR);
    map.insert("370", CountryCode::LTU);
    map.insert("52", CountryCode::MEX);
    map.insert("60", CountryCode::MYS);
    map.insert("234", CountryCode::NGA);
    map.insert("31", CountryCode::NLD);
    map.insert("47", CountryCode::NOR);
    map.insert("64", CountryCode::NZL);
    map.insert("63", CountryCode::PHL);
    map.insert("48", CountryCode::POL);
    map.insert("351", CountryCode::PRT);
    map.insert("40", CountryCode::ROU);
    map.insert("65", CountryCode::SGP);
    map.insert("503", CountryCode::SLV);
    map.insert("46", CountryCode::SWE);
    map.insert("66", CountryCode::THA);
    map.insert("90", CountryCode::TUR);
    map.insert("886", CountryCode::TWN);
    map.insert("256", CountryCode::UGA);
    map.insert("1", CountryCode::USA);
    map.insert("598", CountryCode::URY);
    map.insert("84", CountryCode::VNM);
    map.insert("27", CountryCode::ZAF);
    map
}

fn get_twilio_supported_sms_countries() -> &'static HashMap<&'static str, CountryCode> {
    SUPPORTED_PREFIX_TO_COUNTRY_CODE.get_or_init(init_twilio_supported_sms_countries)
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
                .values()
                .any(|c| c == &country_code),
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
        result = get_twilio_supported_sms_countries().get(search);
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
        assert_eq!(
            find_supported_sms_country_code("+12065555555".to_string()),
            Some(CountryCode::USA)
        );
        assert_eq!(
            find_supported_sms_country_code("+12045555555".to_string()),
            Some(CountryCode::CAN)
        );
    }
}
