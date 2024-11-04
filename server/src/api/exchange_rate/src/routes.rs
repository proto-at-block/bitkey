use axum::extract::State;
use axum::routing::{get, post};
use axum::{Json, Router};
use serde::{Deserialize, Serialize};
use time::OffsetDateTime;
use tracing::instrument;
use utoipa::{OpenApi, ToSchema};

use account::service::Service as AccountService;
use errors::ApiError;

use feature_flags::service::Service as FeatureFlagsService;
use http_server::router::RouterBuilder;
use http_server::swagger::{SwaggerEndpoint, Url};
use types::currencies::{
    Currency, CurrencyCode, CurrencyData, FiatCurrency, FiatDisplayConfiguration,
};
use types::exchange_rate::coingecko::RateProvider as CoingeckoRateProvider;
use types::exchange_rate::{ExchangeRate, ExchangeRateChartData};
use types::serde::{deserialize_iso_4217, deserialize_ts_vec};

use crate::service::Service as ExchangeRateService;

#[derive(Clone, axum_macros::FromRef)]
pub struct RouteState(
    pub ExchangeRateService,
    pub FeatureFlagsService,
    pub AccountService,
);

impl RouterBuilder for RouteState {
    fn unauthed_router(&self) -> Router {
        Router::new()
            .route("/api/exchange-rates", get(get_supported_price_data))
            .route(
                "/api/exchange-rates/currencies",
                get(get_supported_currencies),
            )
            .with_state(self.to_owned())
    }

    fn basic_validation_router(&self) -> Router {
        Router::new()
            .route(
                "/api/exchange-rates/historical",
                post(get_historical_price_data),
            )
            .route("/api/exchange-rates/chart", post(get_chart_data))
            .with_state(self.to_owned())
    }
}
impl From<RouteState> for SwaggerEndpoint {
    fn from(_: RouteState) -> Self {
        (
            Url::new("Exchange Rates", "/docs/exchange-rate/openapi.json"),
            ApiDoc::openapi(),
        )
    }
}

#[derive(OpenApi)]
#[openapi(
    paths(
        get_supported_currencies,
        get_supported_price_data,
        get_historical_price_data,
    ),
    components(
        schemas(SupportedFiatCurrenciesResponse, FiatCurrency, SupportedPriceDataResponse, ExchangeRate, CurrencyData, FiatDisplayConfiguration, HistoricalPriceQuery, HistoricalPriceResponse)
    ),
    tags(
        (name = "Exchange Rates", description = "Exchange Rate Price Data"),
    )
)]
struct ApiDoc;

const ADD_AUD_AND_CAD_CURRENCIES_FLAG_KEY: &str = "f8e-add-aud-and-cad-currencies";

#[instrument(err)]
#[utoipa::path(
    get,
    path = "/api/exchange-rates/currencies",
    responses(
        (status = 200, description = "Retrieved a list of supported fiat currencies", body=SupportedFiatCurrenciesResponse)
    ),
)]
pub async fn get_supported_currencies() -> Result<Json<SupportedFiatCurrenciesResponse>, ApiError> {
    Ok(Json(SupportedFiatCurrenciesResponse {
        supported_currencies: Currency::supported_fiat_currencies(),
    }))
}

#[derive(Serialize, Deserialize, Debug, PartialEq, ToSchema)]
pub struct SupportedFiatCurrenciesResponse {
    pub supported_currencies: Vec<FiatCurrency>,
}

#[instrument(err, skip(exchange_rate_service))]
#[utoipa::path(
    get,
    path = "/api/exchange-rates",
    responses(
        (status = 200, description = "Retrieved a list of supported fiat currencies", body=SupportedFiatCurrenciesResponse)
    ),
)]
pub async fn get_supported_price_data(
    State(exchange_rate_service): State<ExchangeRateService>,
) -> Result<Json<SupportedPriceDataResponse>, ApiError> {
    let exchange_rates = exchange_rate_service
        .get_latest_rates(CoingeckoRateProvider::new())
        .await?;
    Ok(Json(SupportedPriceDataResponse { exchange_rates }))
}

#[derive(Serialize, Deserialize, Debug, PartialEq, ToSchema)]
pub struct SupportedPriceDataResponse {
    exchange_rates: Vec<ExchangeRate>,
}

#[instrument(err, skip(exchange_rate_service))]
#[utoipa::path(
    post,
    path = "/api/exchange-rates/historical",
    responses(
        (status = 200, description = "Retrieve price of bitcoin at a specific time for a specific currency.", body=HistoricalPriceResponse)
    ),
)]
pub async fn get_historical_price_data(
    State(exchange_rate_service): State<ExchangeRateService>,
    Json(request): Json<HistoricalPriceQuery>,
) -> Result<Json<HistoricalPriceResponse>, ApiError> {
    let exchange_rates = exchange_rate_service
        .get_historical_rates(
            CoingeckoRateProvider::new(),
            request.currency_code,
            request.timestamps,
        )
        .await?;

    Ok(Json(HistoricalPriceResponse { exchange_rates }))
}

#[instrument(err, skip(exchange_rate_service))]
#[utoipa::path(
    post,
    path = "/api/exchange-rates/chart",
    responses(
        (status = 200, description = "Retrieve the historical chart data of bitcoin for a specific currency.", body=ExchangeRateChartData)
    ),
)]
pub async fn get_chart_data(
    State(exchange_rate_service): State<ExchangeRateService>,
    Json(request): Json<ChartDataQuery>,
) -> Result<Json<ExchangeRateChartData>, ApiError> {
    let exchange_rates = exchange_rate_service
        .fetch_chart_data(
            CoingeckoRateProvider::new(),
            request.currency_code,
            request.days,
            request.max_price_points,
        )
        .await?;

    Ok(Json(exchange_rates))
}

#[derive(Debug, Deserialize, ToSchema)]
pub struct HistoricalPriceQuery {
    #[serde(deserialize_with = "deserialize_iso_4217")]
    currency_code: CurrencyCode,
    #[serde(deserialize_with = "deserialize_ts_vec")]
    timestamps: Vec<OffsetDateTime>,
}

#[derive(Serialize, Deserialize, Debug, PartialEq, ToSchema)]
pub struct HistoricalPriceResponse {
    pub exchange_rates: Vec<ExchangeRate>,
}

#[derive(Debug, Deserialize, ToSchema)]
pub struct ChartDataQuery {
    #[serde(deserialize_with = "deserialize_iso_4217")]
    currency_code: CurrencyCode,
    /// The `days` field specifies the number of days to fetch data for.
    days: u16,
    /// The `max_price_points` field specifies the maximum number of price points to return.
    /// The first and last price points by timestamp are always included.
    /// The rest of the price points are selected such that they are uniformly distributed.
    max_price_points: usize,
}
