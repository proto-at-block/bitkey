use serde::{Deserialize, Serialize};
use utoipa::ToSchema;

use crate::currencies::{Currency, CurrencyCode};

#[derive(Clone, Copy, Debug, Serialize, Deserialize, PartialEq, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct Money {
    pub amount: u64,
    pub currency_code: CurrencyCode,
}

/// Locale-specific separators for formatting money amounts.
#[derive(Debug, Clone, PartialEq)]
pub struct MoneyLocale {
    pub decimal_separator: char,
    pub grouping_separator: char,
}

impl MoneyLocale {
    /// Creates a MoneyLocale from a BCP 47 language tag.
    /// Falls back to en-US for unknown locales.
    ///
    /// Normalizes the tag before matching: lowercases, converts `_` to `-`,
    /// and strips any extension subtags (e.g. `-u-nu-latn`).
    pub fn from_bcp47(tag: &str) -> Self {
        let normalized = Self::normalize_bcp47(tag);

        // Period decimal, comma grouping
        let dot_comma = MoneyLocale {
            decimal_separator: '.',
            grouping_separator: ',',
        };
        // Comma decimal, period grouping
        let comma_dot = MoneyLocale {
            decimal_separator: ',',
            grouping_separator: '.',
        };
        // Comma decimal, space grouping (ASCII space for firmware rendering)
        let comma_space = MoneyLocale {
            decimal_separator: ',',
            grouping_separator: ' ',
        };

        // Try exact language-region match first, then fall back to language-only.
        // Language-only matching covers tags like "es-419", "pt-BR", "de-AT"
        // that the app can emit but aren't individually listed.
        let lang = normalized.split('-').next().unwrap_or(&normalized);

        match normalized.as_str() {
            "en-us" | "en-gb" | "en-au" | "en-ca" | "ja-jp" => dot_comma,
            "de-de" | "es-es" | "it-it" | "nl-nl" | "pt-pt" | "pt-br" => comma_dot,
            "fr-fr" | "fr-ca" | "fi-fi" => comma_space,
            _ => match lang {
                "en" | "ja" | "zh" | "ko" => dot_comma,
                "de" | "es" | "it" | "nl" | "pt" | "pl" | "tr" | "cs" | "el" | "id" | "ro"
                | "vi" | "hr" | "sk" | "sl" | "bg" | "uk" => comma_dot,
                "fr" | "fi" | "sv" | "nb" | "nn" | "da" | "ru" | "be" | "ka" => comma_space,
                _ => dot_comma,
            },
        }
    }

    /// Normalizes a BCP 47 tag: lowercases, replaces `_` with `-`, and strips
    /// extension subtags (anything from `-u-` onward).
    fn normalize_bcp47(tag: &str) -> String {
        let mut lower = tag.to_lowercase().replace('_', "-");
        // Strip Unicode/extension subtags (e.g. "-u-nu-latn")
        if let Some(idx) = lower.find("-u-") {
            lower.truncate(idx);
        }
        lower
    }
}

impl Money {
    /// Formats this money value for use in Action Proof payloads.
    ///
    /// Produces the canonical format: `"{amount} {CURRENCY}"` (e.g. `"100000 USD"`).
    /// This matches the `ValueFormat::Money` validation in `action-proof`.
    pub fn to_action_proof_value(&self) -> String {
        format!("{} {}", self.amount, self.currency_code)
    }

    /// Formats this money value for human-readable display on hardware.
    ///
    /// Uses ASCII-safe format for firmware rendering:
    /// - Fiat: `"<amount> <CODE>"` (e.g. `"100.00 USD"`, `"10.000,00 EUR"`)
    /// - BTC: `"<amount> BTC"` (e.g. `"0.001 BTC"`, trailing zeros trimmed)
    /// - Locale controls decimal and grouping separators
    pub fn format_display(&self, locale: &MoneyLocale) -> String {
        let currency: Currency = self.currency_code.into();
        match &currency {
            Currency::Fiat(f) => {
                self.format_fiat(f.currency.fractional_digits, &f.currency.text_code, locale)
            }
            Currency::Bitcoin(b) => {
                self.format_btc(b.currency.fractional_digits, &b.currency.text_code, locale)
            }
        }
    }

    fn format_fiat(&self, fractional_digits: u8, text_code: &str, locale: &MoneyLocale) -> String {
        let whole = self.amount / 10u64.pow(fractional_digits as u32);
        let frac = self.amount % 10u64.pow(fractional_digits as u32);

        let whole_str = Self::format_with_grouping(whole, locale.grouping_separator);

        if fractional_digits == 0 {
            format!("{} {}", whole_str, text_code)
        } else {
            format!(
                "{}{}{} {}",
                whole_str,
                locale.decimal_separator,
                Self::pad_left(frac, fractional_digits),
                text_code
            )
        }
    }

    fn format_btc(&self, fractional_digits: u8, text_code: &str, locale: &MoneyLocale) -> String {
        if self.amount == 0 {
            return format!("0 {}", text_code);
        }

        let divisor = 10u64.pow(fractional_digits as u32);
        let whole = self.amount / divisor;
        let frac = self.amount % divisor;

        let whole_str = Self::format_with_grouping(whole, locale.grouping_separator);

        if frac == 0 {
            return format!("{} {}", whole_str, text_code);
        }

        let frac_str = Self::pad_left(frac, fractional_digits);
        let trimmed = frac_str.trim_end_matches('0');
        format!(
            "{}{}{} {}",
            whole_str, locale.decimal_separator, trimmed, text_code
        )
    }

    fn format_with_grouping(n: u64, separator: char) -> String {
        let s = n.to_string();
        if s.len() <= 3 {
            return s;
        }
        let mut result = String::with_capacity(s.len() + s.len() / 3);
        for (i, c) in s.chars().enumerate() {
            if i > 0 && (s.len() - i).is_multiple_of(3) {
                result.push(separator);
            }
            result.push(c);
        }
        result
    }

    fn pad_left(n: u64, width: u8) -> String {
        format!("{:0>width$}", n, width = width as usize)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::currencies::CurrencyCode::*;

    #[test]
    fn test_format_display_usd_en_us() {
        let locale = MoneyLocale::from_bcp47("en-US");
        let money = Money {
            amount: 10000,
            currency_code: USD,
        };
        assert_eq!(money.format_display(&locale), "100.00 USD");
    }

    #[test]
    fn test_format_display_usd_zero() {
        let locale = MoneyLocale::from_bcp47("en-US");
        let money = Money {
            amount: 0,
            currency_code: USD,
        };
        assert_eq!(money.format_display(&locale), "0.00 USD");
    }

    #[test]
    fn test_format_display_usd_large() {
        let locale = MoneyLocale::from_bcp47("en-US");
        let money = Money {
            amount: 1_000_000,
            currency_code: USD,
        };
        assert_eq!(money.format_display(&locale), "10,000.00 USD");
    }

    #[test]
    fn test_format_display_usd_de_de() {
        let locale = MoneyLocale::from_bcp47("de-DE");
        let money = Money {
            amount: 1_000_000,
            currency_code: USD,
        };
        assert_eq!(money.format_display(&locale), "10.000,00 USD");
    }

    #[test]
    fn test_format_display_usd_fr_fr() {
        let locale = MoneyLocale::from_bcp47("fr-FR");
        let money = Money {
            amount: 1_000_000,
            currency_code: USD,
        };
        assert_eq!(money.format_display(&locale), "10 000,00 USD");
    }

    #[test]
    fn test_format_display_eur_en_us() {
        let locale = MoneyLocale::from_bcp47("en-US");
        let money = Money {
            amount: 5099,
            currency_code: EUR,
        };
        assert_eq!(money.format_display(&locale), "50.99 EUR");
    }

    #[test]
    fn test_format_display_gbp() {
        let locale = MoneyLocale::from_bcp47("en-GB");
        let money = Money {
            amount: 150,
            currency_code: GBP,
        };
        assert_eq!(money.format_display(&locale), "1.50 GBP");
    }

    #[test]
    fn test_format_display_jpy() {
        let locale = MoneyLocale::from_bcp47("ja-JP");
        let money = Money {
            amount: 1500,
            currency_code: JPY,
        };
        assert_eq!(money.format_display(&locale), "1,500 JPY");
    }

    #[test]
    fn test_format_display_aud() {
        let locale = MoneyLocale::from_bcp47("en-AU");
        let money = Money {
            amount: 9999,
            currency_code: AUD,
        };
        assert_eq!(money.format_display(&locale), "99.99 AUD");
    }

    #[test]
    fn test_format_display_cad() {
        let locale = MoneyLocale::from_bcp47("en-CA");
        let money = Money {
            amount: 12345,
            currency_code: CAD,
        };
        assert_eq!(money.format_display(&locale), "123.45 CAD");
    }

    #[test]
    fn test_format_display_btc_whole() {
        let locale = MoneyLocale::from_bcp47("en-US");
        let money = Money {
            amount: 100_000_000,
            currency_code: BTC,
        };
        assert_eq!(money.format_display(&locale), "1 BTC");
    }

    #[test]
    fn test_format_display_btc_fractional() {
        let locale = MoneyLocale::from_bcp47("en-US");
        let money = Money {
            amount: 100_000,
            currency_code: BTC,
        };
        assert_eq!(money.format_display(&locale), "0.001 BTC");
    }

    #[test]
    fn test_format_display_btc_zero() {
        let locale = MoneyLocale::from_bcp47("en-US");
        let money = Money {
            amount: 0,
            currency_code: BTC,
        };
        assert_eq!(money.format_display(&locale), "0 BTC");
    }

    #[test]
    fn test_format_display_btc_trailing_zeros_trimmed() {
        let locale = MoneyLocale::from_bcp47("en-US");
        let money = Money {
            amount: 10_000_000,
            currency_code: BTC,
        };
        assert_eq!(money.format_display(&locale), "0.1 BTC");
    }

    #[test]
    fn test_format_display_btc_all_digits() {
        let locale = MoneyLocale::from_bcp47("en-US");
        let money = Money {
            amount: 123_456_789,
            currency_code: BTC,
        };
        assert_eq!(money.format_display(&locale), "1.23456789 BTC");
    }

    #[test]
    fn test_format_display_btc_fractional_de_de() {
        let locale = MoneyLocale::from_bcp47("de-DE");
        let money = Money {
            amount: 100_000,
            currency_code: BTC,
        };
        assert_eq!(money.format_display(&locale), "0,001 BTC");
    }

    #[test]
    fn test_format_display_btc_fractional_fr_fr() {
        let locale = MoneyLocale::from_bcp47("fr-FR");
        let money = Money {
            amount: 123_456_789,
            currency_code: BTC,
        };
        assert_eq!(money.format_display(&locale), "1,23456789 BTC");
    }

    #[test]
    fn test_locale_fallback_unknown() {
        let locale = MoneyLocale::from_bcp47("xx-XX");
        assert_eq!(locale, MoneyLocale::from_bcp47("en-US"));
    }

    #[test]
    fn test_locale_normalization_underscore() {
        let locale = MoneyLocale::from_bcp47("en_US");
        assert_eq!(locale, MoneyLocale::from_bcp47("en-US"));
    }

    #[test]
    fn test_locale_normalization_lowercase() {
        let locale = MoneyLocale::from_bcp47("en-us");
        assert_eq!(locale, MoneyLocale::from_bcp47("en-US"));
    }

    #[test]
    fn test_locale_normalization_extensions_stripped() {
        let locale = MoneyLocale::from_bcp47("en-US-u-nu-latn");
        assert_eq!(locale, MoneyLocale::from_bcp47("en-US"));
    }

    #[test]
    fn test_locale_normalization_underscore_lowercase_combined() {
        let locale = MoneyLocale::from_bcp47("de_de");
        assert_eq!(locale, MoneyLocale::from_bcp47("de-DE"));
    }

    #[test]
    fn test_format_display_btc_large_whole_grouped() {
        let locale = MoneyLocale::from_bcp47("en-US");
        let money = Money {
            amount: 10_000_000_000,
            currency_code: BTC,
        };
        assert_eq!(money.format_display(&locale), "100 BTC");
    }

    #[test]
    fn test_format_display_btc_large_with_frac_grouped() {
        let locale = MoneyLocale::from_bcp47("en-US");
        let money = Money {
            amount: 1_000_100_000_000,
            currency_code: BTC,
        };
        assert_eq!(money.format_display(&locale), "10,001 BTC");
    }

    #[test]
    fn test_locale_es_419_uses_comma_decimal() {
        let locale = MoneyLocale::from_bcp47("es-419");
        assert_eq!(locale.decimal_separator, ',');
        assert_eq!(locale.grouping_separator, '.');
    }

    #[test]
    fn test_locale_pt_br_uses_comma_decimal() {
        let locale = MoneyLocale::from_bcp47("pt-BR");
        assert_eq!(locale.decimal_separator, ',');
        assert_eq!(locale.grouping_separator, '.');
    }

    #[test]
    fn test_locale_es_mx_uses_comma_decimal() {
        let locale = MoneyLocale::from_bcp47("es-MX");
        assert_eq!(locale.decimal_separator, ',');
        assert_eq!(locale.grouping_separator, '.');
    }

    #[test]
    fn test_locale_de_at_uses_comma_decimal() {
        let locale = MoneyLocale::from_bcp47("de-AT");
        assert_eq!(locale.decimal_separator, ',');
        assert_eq!(locale.grouping_separator, '.');
    }

    #[test]
    fn test_locale_fr_be_uses_comma_space() {
        let locale = MoneyLocale::from_bcp47("fr-BE");
        assert_eq!(locale.decimal_separator, ',');
        assert_eq!(locale.grouping_separator, ' ');
    }

    #[test]
    fn test_locale_sv_se_uses_comma_space() {
        let locale = MoneyLocale::from_bcp47("sv-SE");
        assert_eq!(locale.decimal_separator, ',');
        assert_eq!(locale.grouping_separator, ' ');
    }

    #[test]
    fn test_locale_language_only_en() {
        let locale = MoneyLocale::from_bcp47("en");
        assert_eq!(locale.decimal_separator, '.');
        assert_eq!(locale.grouping_separator, ',');
    }

    #[test]
    fn test_locale_language_only_es() {
        let locale = MoneyLocale::from_bcp47("es");
        assert_eq!(locale.decimal_separator, ',');
        assert_eq!(locale.grouping_separator, '.');
    }

    #[test]
    fn test_to_action_proof_value_unchanged() {
        let money = Money {
            amount: 10000,
            currency_code: USD,
        };
        assert_eq!(money.to_action_proof_value(), "10000 USD");
    }
}
