use types::exchange_rate::{
    coingecko::RateProvider as CoingeckoRateProvider, local_rate_provider::LocalRateProvider,
    RateProvider,
};

pub mod chart;
pub mod currency_conversion;
pub mod error;
pub mod flags;
pub mod historical;
pub(crate) mod metrics;
pub mod routes;
pub mod service;
#[cfg(test)]
mod tests;

/// Type used to express the exchange rate provider.
#[derive(Clone, Debug)]
pub enum ExchangeRateProviderType {
    Bitstamp,
    CashApp,
    Coingecko,
    Coinmarketcap,
    Local,
}

impl std::fmt::Display for ExchangeRateProviderType {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            ExchangeRateProviderType::Bitstamp => write!(f, "Bitstamp"),
            ExchangeRateProviderType::CashApp => write!(f, "Cash App"),
            ExchangeRateProviderType::Coingecko => write!(f, "Coingecko"),
            ExchangeRateProviderType::Coinmarketcap => write!(f, "Coinmarketcap"),
            ExchangeRateProviderType::Local => write!(f, "Local"),
        }
    }
}

/// Supertrait for all exchange rate providers.
pub trait ExchangeRateProvider: Send + Sync {
    fn root_url(&self) -> &str;
    fn rate_provider_type() -> ExchangeRateProviderType;
}

/// Configuration trait for selecting exchange rate providers
pub trait ExchangeRateConfig {
    fn use_local_currency_exchange(&self) -> bool;
}

/// Selects the appropriate exchange rate provider based on configuration
pub fn select_exchange_rate_provider<T: ExchangeRateConfig>(config: &T) -> RateProvider {
    if config.use_local_currency_exchange() {
        RateProvider::Local(LocalRateProvider::new())
    } else {
        RateProvider::Coingecko(CoingeckoRateProvider::new())
    }
}
