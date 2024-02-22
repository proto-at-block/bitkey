use exchange_rate::currency_conversion::SpotExchangeRateProvider;
use types::currencies::CurrencyCode::{BTC, USD};
use types::exchange_rate::bitstamp::BitstampRateProvider;
use types::exchange_rate::cash::CashAppRateProvider;

#[tokio::test]
#[ignore = "TODO[W-4965] Add testing infrastructure for external network dependencies"]
async fn get_cash_app_exchange_latest_price_quote() {
    let provider = CashAppRateProvider::new();
    let rate_res = provider.rate(&BTC, &USD).await;
    assert!(rate_res.is_ok(), "rate_res: {:?}", rate_res)
}

#[tokio::test]
async fn get_bitstamp_exchange_latest_price_quote() {
    let provider = BitstampRateProvider::new();

    let rate_res = provider.rate(&BTC, &USD).await;
    assert!(rate_res.is_ok())
}
