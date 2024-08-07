use crate::error::ExchangeRateError::ProviderRateLimited;
use crate::error::{ExchangeRateError, ProviderResponseError};
use crate::{ExchangeRateProvider, ExchangeRateProviderType};
use async_trait::async_trait;
use futures::future::join_all;
use reqwest::StatusCode;
use std::collections::HashMap;
use time::OffsetDateTime;
use tracing::{error, event, Level};
use types::currencies::CurrencyCode;
use types::exchange_rate::coingecko::{
    RateProvider as CoingeckoRateProvider, Response as CoingeckoResponse,
};
use types::exchange_rate::coinmarketcap::{
    RateProvider as CoinmarketcapRateProvider, Response as CoinmarketcapResponse,
};

#[async_trait]
pub trait HistoricalExchangeRateProvider: ExchangeRateProvider {
    type ResponseType: Send;

    /// Get the exchange rate for the given currency pair. Returns an error if the destination currency
    /// is not supported by the provider.
    async fn rate(
        &self,
        currencies: &Vec<CurrencyCode>,
        at_time: OffsetDateTime,
    ) -> Result<HashMap<String, f64>, ExchangeRateError> {
        let response = self.request(currencies, at_time).await?;

        let rates = self.parse_response(response).await?;
        Ok(rates)
    }

    // Parses response from the provider into an ExchangeRate.
    async fn parse_response(
        &self,
        response: Self::ResponseType,
    ) -> Result<HashMap<String, f64>, ExchangeRateError>;

    async fn request(
        &self,
        currencies: &Vec<CurrencyCode>,
        at_time: OffsetDateTime,
    ) -> Result<Self::ResponseType, ExchangeRateError>;
}

impl ExchangeRateProvider for CoinmarketcapRateProvider {
    fn root_url(&self) -> &str {
        &self.root_url
    }

    fn rate_provider_type() -> ExchangeRateProviderType {
        ExchangeRateProviderType::Coinmarketcap
    }
}

#[async_trait]
impl HistoricalExchangeRateProvider for CoinmarketcapRateProvider {
    type ResponseType = CoinmarketcapResponse;

    async fn request(
        &self,
        currencies: &Vec<CurrencyCode>,
        at_time: OffsetDateTime,
    ) -> Result<Self::ResponseType, ExchangeRateError> {
        let response = self
            .historical_rate_request(currencies, &at_time)
            .send()
            .await;

        match response {
            Ok(r) => match r.status() {
                StatusCode::OK => r.json::<Self::ResponseType>().await.map_err(|_| {
                    ProviderResponseError::Deserialization(Self::rate_provider_type()).into()
                }),
                StatusCode::TOO_MANY_REQUESTS => {
                    event!(Level::ERROR, "Coinmarketcap rate limit reached");
                    Err(ProviderRateLimited(Self::rate_provider_type()))
                }
                _ => Err(ProviderResponseError::Generic(r.status().to_string()).into()),
            },
            Err(e) => {
                event!(Level::ERROR, "Unable to reach Coinmarketcap API");
                Err(ExchangeRateError::ProviderUnreachable(e))
            }
        }
    }

    async fn parse_response(
        &self,
        response: Self::ResponseType,
    ) -> Result<HashMap<String, f64>, ExchangeRateError> {
        // Check if BTC data available
        let btc_data = response
            .data
            .btc
            .first()
            .ok_or_else(|| ProviderResponseError::MissingData(Self::rate_provider_type()))?;

        // Check if we received any quotes at all, of any currency.
        let quote_data = btc_data
            .quotes
            .first()
            .ok_or_else(|| ProviderResponseError::MissingData(Self::rate_provider_type()))?;

        // Map the quote data into a hashmap of currency code to price
        Ok(quote_data
            .quote
            .iter()
            .map(|(currency, value)| (currency.clone(), value.price))
            .collect())
    }
}

impl ExchangeRateProvider for CoingeckoRateProvider {
    fn root_url(&self) -> &str {
        &self.root_url
    }

    fn rate_provider_type() -> ExchangeRateProviderType {
        ExchangeRateProviderType::Coingecko
    }
}

#[async_trait]
impl HistoricalExchangeRateProvider for CoingeckoRateProvider {
    type ResponseType = Vec<(CurrencyCode, CoingeckoResponse)>;

    async fn request(
        &self,
        currencies: &Vec<CurrencyCode>,
        at_time: OffsetDateTime,
    ) -> Result<Self::ResponseType, ExchangeRateError> {
        let request_futures = currencies.iter().map(|currency_code| async move {
            match self
                .historical_rate_request(currency_code, &at_time)
                .send()
                .await
            {
                Ok(response) => match response.status() {
                    StatusCode::OK => match response.json::<CoingeckoResponse>().await {
                        Ok(response) => Some((currency_code.clone(), response)),
                        Err(e) => {
                            event!(
                                Level::ERROR,
                                "Failed to deserialize Coingecko response for currency {} at {}: {}",
                                currency_code,
                                at_time,
                                e
                            );
                            None
                        }
                    },
                    StatusCode::TOO_MANY_REQUESTS => {
                        event!(Level::ERROR, "Coingecko API rate limit reached");
                        None
                    }
                    _ => {
                        event!(
                            Level::ERROR,
                            "Encountered error from Coingecko API: {}",
                            response.status()
                        );
                        None
                    }
                },
                Err(e) => {
                    error!(
                        "Failed to get historical rate for currency {} at {}: {}",
                        currency_code, at_time, e
                    );
                    None
                }
            }
        });

        Ok(join_all(request_futures)
            .await
            .into_iter()
            .flatten()
            .collect::<Vec<_>>())
    }

    async fn parse_response(
        &self,
        response: Self::ResponseType,
    ) -> Result<HashMap<String, f64>, ExchangeRateError> {
        let mut rates = HashMap::new();

        for (currency, response) in response {
            let price = response
                .prices
                .first()
                .ok_or_else(|| ProviderResponseError::MissingData(Self::rate_provider_type()))?
                .price;

            rates.insert(currency.to_string(), price);
        }

        Ok(rates)
    }
}
