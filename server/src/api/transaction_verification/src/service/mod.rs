use crate::repository::TransactionVerificationRepository;
use account::service::Service as AccountService;
use exchange_rate::service::Service as ExchangeRateService;
use notification::service::Service as NotificationService;
use serde::Deserialize;
use std::sync::Arc;
use wsm_rust_client::GrantService;

mod fetch;
mod initiate;
pub mod mock;
pub mod tests;

#[derive(Clone, Deserialize)]
pub struct Config {
    // TODO: De-dupe against the version used in mobile_pay and move into exchange_rates.
    pub use_local_currency_exchange: bool,
    pub secure_site_base_url: String,
}

#[derive(Clone)]
pub struct Service {
    config: Config,
    repo: TransactionVerificationRepository,
    account_service: AccountService,
    exchange_rate_service: ExchangeRateService,
    notification_service: NotificationService,
    grant_service: Arc<dyn GrantService + Send + Sync>,
}

impl Service {
    pub fn new(
        config: Config,
        repo: TransactionVerificationRepository,
        account_service: AccountService,
        exchange_rate_service: ExchangeRateService,
        notification_service: NotificationService,
        grant_service: Arc<dyn GrantService + Send + Sync>,
    ) -> Self {
        Self {
            config,
            repo,
            account_service,
            exchange_rate_service,
            notification_service,
            grant_service,
        }
    }
}
