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
