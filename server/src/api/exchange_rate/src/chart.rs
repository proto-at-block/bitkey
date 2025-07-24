use crate::error::ExchangeRateError::ProviderRateLimited;
use crate::error::{ExchangeRateError, ProviderResponseError};
use crate::ExchangeRateProvider;
use async_trait::async_trait;
use reqwest::StatusCode;

use types::currencies::CurrencyCode;
use types::exchange_rate::coingecko::{
    RateProvider as CoingeckoRateProvider, Response as CoingeckoResponse,
};
use types::exchange_rate::PriceAt;

#[async_trait]
pub trait ExchangeRateChartProvider: ExchangeRateProvider {
    async fn chart_data(
        &self,
        fiat_currency: &CurrencyCode,
        days: u16,
        max_price_points: usize,
    ) -> Result<Vec<PriceAt>, ExchangeRateError> {
        let prices = self.full_chart_data(fiat_currency, days).await?;
        Ok(Self::select_price_points(&prices, max_price_points))
    }

    async fn full_chart_data(
        &self,
        fiat_currency: &CurrencyCode,
        days: u16,
    ) -> Result<Vec<PriceAt>, ExchangeRateError>;

    fn select_price_points(prices: &[PriceAt], max_price_points: usize) -> Vec<PriceAt> {
        let total_prices = prices.len();
        if max_price_points >= total_prices || max_price_points <= 2 {
            return prices.to_vec();
        }

        let mut sorted_prices = prices.to_vec();
        sorted_prices.sort_by_key(|price_at| price_at.timestamp);

        let window_size = total_prices / max_price_points;
        let mut sma_prices = Vec::new();

        // Calculate the Simple Moving Average
        for window in sorted_prices.windows(window_size) {
            let avg_price: f64 = window.iter().map(|p| p.price).sum::<f64>() / window_size as f64;
            let timestamp = window[window_size / 2].timestamp; // Use the middle timestamp for the SMA point
            sma_prices.push(PriceAt {
                timestamp,
                price: avg_price,
            });
        }

        // Select points from the SMA results
        let num_points_to_select = max_price_points - 1; // latest price point included later
                                                         // Calculate interval to evenly distribute selected points, adding num_points_to_select - 1
                                                         // will round up the division to ensure we get the correct number of points
        let interval = sma_prices.len().div_ceil(num_points_to_select);
        let mut selected_prices: Vec<PriceAt> = sma_prices
            .into_iter()
            .step_by(interval)
            .take(max_price_points - 1)
            .collect();

        // Always include the latest price point if available
        if let Some(last_price) = sorted_prices.last() {
            selected_prices.push(last_price.clone());
        }

        selected_prices
    }
}

#[async_trait]
impl ExchangeRateChartProvider for CoingeckoRateProvider {
    async fn full_chart_data(
        &self,
        fiat_currency: &CurrencyCode,
        days: u16,
    ) -> Result<Vec<PriceAt>, ExchangeRateError> {
        let response = self
            .rate_chart_request(fiat_currency, days)
            .send()
            .await
            .map_err(ExchangeRateError::ProviderUnreachable)?;

        match response.status() {
            StatusCode::OK => {
                let response = response.json::<CoingeckoResponse>().await.map_err(|_| {
                    ProviderResponseError::Deserialization(Self::rate_provider_type())
                })?;
                let prices_points = response.prices.into_iter().map(PriceAt::from).collect();
                Ok(prices_points)
            }
            StatusCode::TOO_MANY_REQUESTS => Err(ProviderRateLimited(Self::rate_provider_type())),
            _ => Err(ProviderResponseError::Generic(response.status().to_string()).into()),
        }
    }
}
