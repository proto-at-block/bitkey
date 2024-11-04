use crate::currencies::Currency::{Bitcoin, Fiat};
use crate::currencies::CurrencyCode::{AUD, BTC, CAD, EUR, GBP, JPY, USD, XXX};
use once_cell::sync::Lazy;
use serde::{Deserialize, Serialize};
use std::fmt::{Display, Formatter};
use utoipa::ToSchema;

static SUPPORTED_CURRENCIES: Lazy<Vec<CurrencyCode>> = Lazy::new(|| vec![AUD, CAD, EUR, GBP, USD]);

pub enum Currency {
    Fiat(FiatCurrency),
    Bitcoin(BitcoinCurrency),
}

impl Currency {
    pub fn supported_currency_codes() -> Vec<CurrencyCode> {
        SUPPORTED_CURRENCIES.to_vec()
    }

    pub fn supported_fiat_currencies() -> Vec<FiatCurrency> {
        Self::supported_currency_codes()
            .into_iter()
            .map(Currency::from)
            .filter_map(|c| match c {
                Fiat(f) => Some(f),
                _ => None,
            })
            .collect()
    }
}

pub struct BitcoinCurrency {
    pub currency: CurrencyData,
    fractional_unit_configuration: BitcoinFractionalUnitConfiguration,
}

struct BitcoinFractionalUnitConfiguration {
    name: String,
    name_plural: String,
}

#[derive(Debug, Serialize, Deserialize, PartialEq, ToSchema)]
pub struct FiatCurrency {
    pub currency: CurrencyData,
    fiat_display_configuration: FiatDisplayConfiguration,
}

#[derive(Debug, Serialize, Deserialize, PartialEq, ToSchema)]
pub struct FiatDisplayConfiguration {
    name: String,
    display_country_code: String,
}

#[derive(Clone, Debug, Serialize, Deserialize, PartialEq, ToSchema)]
pub struct CurrencyData {
    pub text_code: String,
    pub unit_symbol: String,
    pub fractional_digits: u8,
}

#[derive(Clone, Debug, Deserialize, PartialEq, Serialize, ToSchema)]
// ISO 4217 currency codes
pub enum CurrencyCode {
    AUD = 36,
    CAD = 124,
    GBP = 826,
    JPY = 392,
    USD = 840,
    EUR = 978,
    XXX = 999,  // Defined as "no currency" by ISO-4217.
    BTC = 1001, // Not an ISO code!
}

impl From<CurrencyCode> for Currency {
    fn from(value: CurrencyCode) -> Self {
        match value {
            AUD => Fiat(FiatCurrency {
                currency: CurrencyData {
                    text_code: "AUD".to_string(),
                    unit_symbol: "$".to_string(),
                    fractional_digits: 2,
                },
                fiat_display_configuration: FiatDisplayConfiguration {
                    name: "Australian Dollar".to_string(),
                    display_country_code: "AU".to_string(),
                },
            }),
            CAD => Fiat(FiatCurrency {
                currency: CurrencyData {
                    text_code: "CAD".to_string(),
                    unit_symbol: "$".to_string(),
                    fractional_digits: 2,
                },
                fiat_display_configuration: FiatDisplayConfiguration {
                    name: "Canadian Dollar".to_string(),
                    display_country_code: "CA".to_string(),
                },
            }),
            GBP => Fiat(FiatCurrency {
                currency: CurrencyData {
                    text_code: "GBP".to_string(),
                    unit_symbol: "£".to_string(),
                    fractional_digits: 2,
                },
                fiat_display_configuration: FiatDisplayConfiguration {
                    name: "Pound Sterling".to_string(),
                    display_country_code: "GB".to_string(),
                },
            }),
            JPY => Fiat(FiatCurrency {
                currency: CurrencyData {
                    text_code: "JPY".to_string(),
                    unit_symbol: "¥".to_string(),
                    fractional_digits: 0,
                },
                fiat_display_configuration: FiatDisplayConfiguration {
                    name: "Japanese Yen".to_string(),
                    display_country_code: "JP".to_string(),
                },
            }),
            USD => Fiat(FiatCurrency {
                currency: CurrencyData {
                    text_code: "USD".to_string(),
                    unit_symbol: "$".to_string(),
                    fractional_digits: 2,
                },
                fiat_display_configuration: FiatDisplayConfiguration {
                    name: "US Dollar".to_string(),
                    display_country_code: "US".to_string(),
                },
            }),
            EUR => Fiat(FiatCurrency {
                currency: CurrencyData {
                    text_code: "EUR".to_string(),
                    unit_symbol: "€".to_string(),
                    fractional_digits: 2,
                },
                fiat_display_configuration: FiatDisplayConfiguration {
                    name: "Euro".to_string(),
                    display_country_code: "EU".to_string(),
                },
            }),
            BTC => Bitcoin(BitcoinCurrency {
                currency: CurrencyData {
                    text_code: "BTC".to_string(),
                    unit_symbol: "₿".to_string(),
                    fractional_digits: 8,
                },
                fractional_unit_configuration: BitcoinFractionalUnitConfiguration {
                    name: "sat".to_string(),
                    name_plural: "sats".to_string(),
                },
            }),
            XXX => Fiat(FiatCurrency {
                currency: CurrencyData {
                    text_code: "XXX".to_string(),
                    unit_symbol: "XXX".to_string(),
                    fractional_digits: 2,
                },
                fiat_display_configuration: FiatDisplayConfiguration {
                    name: "No Currency".to_string(),
                    display_country_code: "XX".to_string(),
                },
            }),
        }
    }
}

impl Display for CurrencyCode {
    fn fmt(&self, f: &mut Formatter<'_>) -> std::fmt::Result {
        let currency: Currency = self.clone().into();

        let text_code = match currency {
            Fiat(f) => f.currency.text_code,
            Bitcoin(b) => b.currency.text_code,
        };

        write!(f, "{}", text_code)
    }
}
