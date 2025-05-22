use async_trait::async_trait;
use bdk_utils::constants::ONE_BTC_IN_SATOSHIS;
use time::OffsetDateTime;
use tracing::instrument;
use types::account::money::Money;
use types::currencies::CurrencyCode::BTC;
use types::currencies::{Currency, CurrencyCode};
use types::exchange_rate::bitstamp::{BitstampRate, BitstampRateProvider};
use types::exchange_rate::cash::{CashAppQuote, CashAppRateProvider};
use types::exchange_rate::coingecko::{
    CoingeckoCurrencyQuote, CoingeckoQuote, RateProvider as CoingeckoRateProvider,
};
use types::exchange_rate::local_rate_provider::{LocalRateProvider, LocalRateType};
use types::exchange_rate::PriceAt;

use crate::chart::ExchangeRateChartProvider;
use crate::error::ExchangeRateError::{
    ProviderUnreachable, UnsupportedDestinationCurrency, UnsupportedSourceCurrency,
};
use crate::error::{ExchangeRateError, ProviderResponseError};
use crate::service::Service;
use crate::{ExchangeRateProvider, ExchangeRateProviderType};

#[instrument(err, skip(exchange_rate_service, rate_provider, money))]
pub async fn sats_for<T>(
    exchange_rate_service: &Service,
    rate_provider: T,
    money: &Money,
) -> Result<u64, ExchangeRateError>
where
    T: SpotExchangeRateProvider + 'static,
{
    let rate = exchange_rate_service
        .get_latest_rate(rate_provider, money.currency_code)
        .await?
        .rate;

    let x = ONE_BTC_IN_SATOSHIS as f64  // 1 BTC in SATOSHIs
        / rate                               // 1 USD in SATOSHIs
        * 0.01                               // 1 US cent in SATOSHIs //TODO: use whatever shift we need coming from "from" currency
        * (money.amount as f64); // amount in SATOSHIs
    Ok(x as u64)
}

/// Trait for exchange rate providers that support spot rates.
#[async_trait]
pub trait SpotExchangeRateProvider: ExchangeRateProvider {
    type ResponseType: Send;

    /// Get the exchange rate for the given currency pair. Returns an error if the source currency
    /// is not supported by the provider.
    #[instrument(skip(self))]
    async fn rate(&self, from: &CurrencyCode, to: &CurrencyCode) -> Result<f64, ExchangeRateError> {
        match from {
            BTC => {
                let destination_currency: Currency = (*to).into();

                match destination_currency {
                    Currency::Fiat(fiat_currency) => {
                        if !Currency::supported_fiat_currencies().contains(&fiat_currency) {
                            return Err(UnsupportedDestinationCurrency(*to));
                        }

                        let response = self.request(to).await?;
                        self.parse_response(response).await
                    }
                    Currency::Bitcoin(_) => Ok(1.0),
                }
            }
            c => Err(UnsupportedSourceCurrency(*c)),
        }
    }

    /// Parses response from the provider into a float.
    async fn parse_response(&self, response: Self::ResponseType) -> Result<f64, ExchangeRateError>;

    /// Makes a request to the provider for the given currency.
    async fn request(
        &self,
        currency: &CurrencyCode,
    ) -> Result<Self::ResponseType, ExchangeRateError>;
}

impl ExchangeRateProvider for BitstampRateProvider {
    fn root_url(&self) -> &str {
        &self.root_url
    }

    fn rate_provider_type() -> ExchangeRateProviderType {
        ExchangeRateProviderType::Bitstamp
    }
}

#[async_trait]
impl SpotExchangeRateProvider for BitstampRateProvider {
    type ResponseType = BitstampRate;

    async fn request(
        &self,
        currency: &CurrencyCode,
    ) -> Result<Self::ResponseType, ExchangeRateError> {
        self.rate_request(currency)
            .send()
            .await
            .map_err(ProviderUnreachable)?
            .json::<BitstampRate>()
            .await
            .map_err(|_| ProviderResponseError::Deserialization(Self::rate_provider_type()).into())
    }

    async fn parse_response(&self, response: Self::ResponseType) -> Result<f64, ExchangeRateError> {
        let ask_float = response
            .ask
            .parse::<f64>()
            .map_err(|_| ProviderResponseError::Deserialization(Self::rate_provider_type()))?;
        Ok(ask_float)
    }
}

impl ExchangeRateProvider for CashAppRateProvider {
    fn root_url(&self) -> &str {
        &self.root_url
    }

    fn rate_provider_type() -> ExchangeRateProviderType {
        ExchangeRateProviderType::CashApp
    }
}

#[async_trait]
impl SpotExchangeRateProvider for CashAppRateProvider {
    type ResponseType = CashAppQuote;

    async fn request(
        &self,
        currency: &CurrencyCode,
    ) -> Result<Self::ResponseType, ExchangeRateError> {
        self.rate_request(currency)
            .send()
            .await
            .map_err(ProviderUnreachable)?
            .json::<CashAppQuote>()
            .await
            .map_err(|_| ProviderResponseError::Deserialization(Self::rate_provider_type()).into())
    }

    async fn parse_response(&self, response: Self::ResponseType) -> Result<f64, ExchangeRateError> {
        let latest_quote =
            response
                .exchange_data
                .rates
                .first()
                .ok_or(ProviderResponseError::MissingData(
                    Self::rate_provider_type(),
                ))?;

        Ok(latest_quote.base_value_cents as f64 / 100.0)
    }
}

#[async_trait]
impl SpotExchangeRateProvider for CoingeckoRateProvider {
    type ResponseType = CoingeckoCurrencyQuote;

    async fn request(
        &self,
        currency: &CurrencyCode,
    ) -> Result<Self::ResponseType, ExchangeRateError> {
        self.rate_request(currency)
            .send()
            .await
            .map_err(ProviderUnreachable)?
            .json::<CoingeckoQuote>()
            .await
            .map_err(|_| ProviderResponseError::Deserialization(Self::rate_provider_type()).into())
            .and_then(|r| {
                let quote = r
                    .bitcoin
                    .get(&currency.to_string().to_lowercase())
                    .ok_or_else(|| ProviderResponseError::MissingData(Self::rate_provider_type()))?
                    .to_owned();
                Ok(CoingeckoCurrencyQuote { quote })
            })
    }

    async fn parse_response(&self, response: Self::ResponseType) -> Result<f64, ExchangeRateError> {
        Ok(response.quote)
    }
}

impl ExchangeRateProvider for LocalRateProvider {
    fn root_url(&self) -> &str {
        "http://localhost/"
    }

    fn rate_provider_type() -> ExchangeRateProviderType {
        ExchangeRateProviderType::Local
    }
}

#[async_trait]
impl ExchangeRateChartProvider for LocalRateProvider {
    async fn full_chart_data(
        &self,
        _currency: &CurrencyCode,
        _days: u16,
    ) -> Result<Vec<PriceAt>, ExchangeRateError> {
        self.increment_network_call_count().await;

        let price = self
            .rate_to_return
            .ok_or_else(|| ProviderResponseError::Deserialization(Self::rate_provider_type()))?;

        let timestamp =
            OffsetDateTime::from_unix_timestamp(self.rate_timestamp.ok_or_else(|| {
                ProviderResponseError::Deserialization(Self::rate_provider_type())
            })?)
            .map_err(|_| ProviderResponseError::Deserialization(Self::rate_provider_type()))?;

        Ok(vec![PriceAt { timestamp, price }])
    }
}

#[async_trait]
impl SpotExchangeRateProvider for LocalRateProvider {
    type ResponseType = LocalRateType;

    async fn request(
        &self,
        _currency: &CurrencyCode,
    ) -> Result<Self::ResponseType, ExchangeRateError> {
        self.increment_network_call_count().await; // Safely increment

        self.rate_to_return
            .ok_or_else(|| {
                ProviderResponseError::Deserialization(Self::rate_provider_type()).into()
            })
            .map(|rate| LocalRateType { rate })
    }

    async fn parse_response(&self, response: Self::ResponseType) -> Result<f64, ExchangeRateError> {
        Ok(response.rate)
    }
}

#[cfg(test)]
mod sats_for_tests {
    use types::account::money::Money;
    use types::currencies::CurrencyCode::{JPY, USD};
    use types::exchange_rate::local_rate_provider::LocalRateProvider;

    use crate::currency_conversion::sats_for;
    use crate::service::Service;

    #[tokio::test]
    async fn test_sats_for_conversion() {
        let rate_provider = LocalRateProvider::new();
        let test_amounts = vec![(1, 44), (100, 4409)];
        for (dollar_amount, sat_amount) in test_amounts {
            let result = sats_for(
                &Service::new(),
                rate_provider.clone(),
                &Money {
                    amount: dollar_amount as u64,
                    currency_code: USD,
                },
            )
            .await
            .unwrap();
            assert_eq!(result, sat_amount);
        }
    }

    #[tokio::test]
    async fn test_jpy_should_fail_test() {
        let rate_provider = LocalRateProvider::new();
        let rate_result = sats_for(
            &Service::new(),
            rate_provider,
            &Money {
                amount: 1,
                currency_code: JPY,
            },
        )
        .await;
        assert!(rate_result.is_err())
    }
}

#[cfg(test)]
mod local_rate_provider_tests {
    use types::currencies::CurrencyCode::{BTC, EUR, USD, XXX};
    use types::exchange_rate::local_rate_provider::{LocalRateProvider, LOCAL_ONE_BTC_IN_FIAT};

    use crate::currency_conversion::SpotExchangeRateProvider;
    use crate::error::ExchangeRateError::{
        UnsupportedDestinationCurrency, UnsupportedSourceCurrency,
    };

    #[tokio::test]
    async fn identity_from_btc_to_btc() {
        let x = LocalRateProvider::new().rate(&BTC, &BTC).await.unwrap();
        assert_eq!(x, 1.0);
    }

    #[tokio::test]
    async fn fixed_constant_from_btc_to_usd() {
        let x = LocalRateProvider::new().rate(&BTC, &USD).await.unwrap();
        assert_eq!(x, LOCAL_ONE_BTC_IN_FIAT);
    }

    #[tokio::test]
    async fn fail_from_btc_to_unsupported_fiat_currency() {
        match LocalRateProvider::new().rate(&BTC, &XXX).await {
            Err(UnsupportedDestinationCurrency(c)) => {
                assert_eq!(c, XXX);
            }
            result => panic!("Unexpected result: {result:?}"),
        }
    }

    #[tokio::test]
    async fn fail_from_non_btc() {
        match LocalRateProvider::new().rate(&EUR, &BTC).await {
            Err(UnsupportedSourceCurrency(c)) => {
                assert_eq!(c, EUR);
            }
            result => panic!("Unexpected result: {result:?}"),
        }
    }
}
