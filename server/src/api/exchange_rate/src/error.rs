use crate::ExchangeRateProviderType;
use errors::ApiError;
use thiserror::Error;
use types::currencies::CurrencyCode;

#[derive(Debug, Error)]
pub enum ExchangeRateError {
    #[error("Could not retrieve or update exchange rate in cache")]
    CacheRead,
    #[error("Cannot convert from unsupported source currency {0}")]
    UnsupportedSourceCurrency(CurrencyCode),
    #[error("Cannot convert from BTC to unsupported destination currency {0}")]
    UnsupportedDestinationCurrency(CurrencyCode),
    #[error("Could not retrieve exchange rate from provider {0}")]
    ProviderUnreachable(#[from] reqwest::Error),
    #[error(transparent)]
    ProviderResponseInvalid(#[from] ProviderResponseError),
    #[error("Could not retrieve rates from {0:?} due to rate limits")]
    ProviderRateLimited(ExchangeRateProviderType),
}

impl From<ExchangeRateError> for ApiError {
    fn from(value: ExchangeRateError) -> Self {
        let err_msg = value.to_string();

        match value {
            ExchangeRateError::UnsupportedDestinationCurrency(_)
            | ExchangeRateError::UnsupportedSourceCurrency(_)
            | ExchangeRateError::ProviderRateLimited(_) => ApiError::GenericBadRequest(err_msg),
            ExchangeRateError::CacheRead
            | ExchangeRateError::ProviderUnreachable(_)
            | ExchangeRateError::ProviderResponseInvalid(_) => {
                ApiError::GenericInternalApplicationError(err_msg)
            }
        }
    }
}

#[derive(Clone, Debug, Error)]
pub enum ProviderResponseError {
    #[error("Invalid deserialize response from provider: {0}")]
    Deserialization(ExchangeRateProviderType),
    #[error("Deserialization succeeded, but response was empty from provider: {0}")]
    MissingData(ExchangeRateProviderType),
    #[error("{0}")]
    Generic(String),
}

impl From<ProviderResponseError> for ApiError {
    fn from(value: ProviderResponseError) -> Self {
        let err_msg = value.to_string();

        match value {
            ProviderResponseError::Deserialization(_)
            | ProviderResponseError::MissingData(_)
            | ProviderResponseError::Generic(_) => ApiError::GenericBadRequest(err_msg),
        }
    }
}
