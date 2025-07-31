use crate::daily_spend_record::entities::{DailySpendingRecord, SpendingEntry};
use crate::daily_spend_record::service::Service as DailySpendRecordService;
use crate::error::SigningError;
use crate::routes::Config;
use exchange_rate::currency_conversion::sats_for;
use exchange_rate::error::ExchangeRateError;
use exchange_rate::service::Service as ExchangeRateService;
use exchange_rate::{select_exchange_rate_provider, ExchangeRateConfig};
use feature_flags::flag::Flag;
use time::{Duration, OffsetDateTime};
use types::account::identifiers::AccountId;
use types::account::money::Money;
use types::account::spend_limit::SpendingLimit;
use types::exchange_rate::RateProvider;

pub mod daily_spend_record;
pub mod entities;
pub mod error;
pub(crate) mod metrics;
pub mod routes;
pub mod signed_psbt_cache;
pub mod signing_processor;
mod signing_strategies;
pub mod spend_rules;
pub(crate) mod util;

pub(crate) const SERVER_SIGNING_ENABLED: Flag<bool> = Flag::new("f8e-mobile-pay-enabled");

/// Data structure used to represent [`DailySpendingRecord`]s that are relevant to Mobile Pay.
///
/// Currently, 3AM is the start of each Mobile Pay window, so "yesterday's" spending record may
/// still be relevant. See [`get_mobile_pay_spending_record`] for more information.
pub(crate) struct MobilePaySpendingRecord {
    yesterday: DailySpendingRecord,
    today: DailySpendingRecord,
}

impl MobilePaySpendingRecord {
    /// Returns a flattened list of [`SpendingEntry`] from yesterday and today.
    fn spending_entries(&self) -> Vec<&SpendingEntry> {
        vec![
            self.yesterday.get_spending_entries(),
            self.today.get_spending_entries(),
        ]
        .into_iter()
        .flatten()
        .collect()
    }
}

async fn get_mobile_pay_spending_record(
    account_id: &AccountId,
    daily_spend_record_service: &DailySpendRecordService,
) -> Result<MobilePaySpendingRecord, SigningError> {
    // If a spend is before the daily roll-over, we'll need to check yesterday's spending record as well
    let yesterday_spending_record = daily_spend_record_service
        .fetch_or_create_daily_spending_record(
            account_id,
            OffsetDateTime::now_utc()
                .checked_sub(Duration::days(1))
                .ok_or(SigningError::CouldNotGetSpendingRecords(
                    "arithmetic error subtracting date".to_string(),
                ))?
                .date(),
        )
        .await?;
    let today_spending_record = daily_spend_record_service
        .fetch_or_create_daily_spending_record(account_id, OffsetDateTime::now_utc().date())
        .await?;

    Ok(MobilePaySpendingRecord {
        yesterday: yesterday_spending_record,
        today: today_spending_record,
    })
}

impl ExchangeRateConfig for Config {
    fn use_local_currency_exchange(&self) -> bool {
        self.use_local_currency_exchange
    }
}

async fn sats_for_limit(
    limit: &SpendingLimit,
    config: &Config,
    exchange_rate_service: &ExchangeRateService,
) -> Result<u64, ExchangeRateError> {
    match select_exchange_rate_provider(config) {
        RateProvider::Local(provider) => {
            sats_for(exchange_rate_service, provider, &limit.amount).await
        }
        RateProvider::Coingecko(provider) => {
            sats_for(exchange_rate_service, provider, &limit.amount).await
        }
    }
}

async fn sats_for_threshold(
    money: &Money,
    config: &Config,
    exchange_rate_service: &ExchangeRateService,
) -> Result<u64, ExchangeRateError> {
    match select_exchange_rate_provider(config) {
        RateProvider::Local(provider) => sats_for(exchange_rate_service, provider, money).await,
        RateProvider::Coingecko(provider) => sats_for(exchange_rate_service, provider, money).await,
    }
}

#[cfg(test)]
mod tests {
    use std::{env, str::FromStr};

    use bdk_utils::bdk::bitcoin::secp256k1::PublicKey;
    use exchange_rate::select_exchange_rate_provider;
    use types::exchange_rate::RateProvider;

    use crate::routes::Config;

    #[test]
    fn test_select_exchange_rate_provider() {
        env::set_var("COINGECKO_API_KEY", "");
        // Return LocalRateProvider
        match select_exchange_rate_provider(&Config {
            use_local_currency_exchange: true,
            wik_pub_key: PublicKey::from_str(
                "03078451e0c1e12743d2fdd93ae7d03d5cf7813d2f612de10904e1c6a0b87f7071",
            )
            .unwrap(),
        }) {
            RateProvider::Local(_provider) => {}
            _ => panic!("Unexpected exchange rate provider returned"),
        }

        // Return CoingeckoRatePRovider
        match select_exchange_rate_provider(&Config {
            use_local_currency_exchange: false,
            wik_pub_key: PublicKey::from_str(
                "03078451e0c1e12743d2fdd93ae7d03d5cf7813d2f612de10904e1c6a0b87f7071",
            )
            .unwrap(),
        }) {
            RateProvider::Coingecko(_provider) => {}
            _ => panic!("Unexpected exchange rate provider returned"),
        }
    }
}
