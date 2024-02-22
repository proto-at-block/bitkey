use authn_authz::userpool::UserPoolService;
use aws_utils::secrets_manager::{FetchSecret, SecretsManager};
use axum::extract::Query;
use axum::routing::get;
use axum::{extract::State, routing::post, Json, Router};
use partnerships_lib::{
    errors::ApiError,
    models::{partners::PartnerInfo, PaymentMethod, Quote, RedirectInfo},
    Partnerships,
};
use serde::{Deserialize, Serialize};
use utoipa::{OpenApi, ToSchema};

use http_server::swagger::{SwaggerEndpoint, Url};
use partnerships_lib::models::PurchaseOptions;
use types::currencies::CurrencyCode;

#[derive(Clone, axum_macros::FromRef)]
pub struct RouteState(pub Partnerships, pub UserPoolService);

impl RouteState {
    pub async fn new(user_pool_service: UserPoolService) -> Self {
        let secrets_manager = SecretsManager::new().await;
        Self::from_secrets_manager(secrets_manager, user_pool_service).await
    }

    pub async fn from_secrets_manager(
        secrets_manager: impl FetchSecret,
        user_pool_service: UserPoolService,
    ) -> Self {
        Self(Partnerships::new(secrets_manager).await, user_pool_service)
    }
}

impl From<RouteState> for Router {
    fn from(value: RouteState) -> Self {
        Router::new()
            .route("/api/partnerships/transfers", post(list_transfer_partners))
            .route(
                "/api/partnerships/transfers/redirects",
                post(get_transfer_redirect),
            )
            .route(
                "/api/partnerships/purchases/quotes",
                post(list_purchase_quotes),
            )
            .route(
                "/api/partnerships/purchases/redirects",
                post(get_purchase_redirect),
            )
            .route(
                "/api/partnerships/purchases/options",
                get(get_purchase_options),
            )
            .with_state(value)
    }
}

impl From<RouteState> for SwaggerEndpoint {
    fn from(_: RouteState) -> Self {
        (
            Url::new("Partnerships", "/partnerships/openapi.json"),
            ApiDoc::openapi(),
        )
    }
}

#[derive(OpenApi)]
#[openapi(
    paths(
        list_transfer_partners,
        get_transfer_redirect,
        list_purchase_quotes,
        get_purchase_redirect,
    ),
    components(
        schemas(
            ListTransferPartnersResponse,
            GetTransferRedirectRequest,
            GetTransferRedirectResponse,
            ListPurchaseQuotesRequest,
            ListPurchaseQuotesResponse,
            GetPurchaseRedirectRequest,
            GetPurchaseRedirectResponse
        )
    ),
    tags(
        (name = "Partnerships", description = "Partner interactions entrypoints")
    )
)]
struct ApiDoc;

#[derive(Debug, Serialize, Deserialize, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct ListTransferPartnersRequest {
    //TODO use ISO country code based enum
    pub country: String,
}

#[derive(Serialize, Deserialize, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct ListTransferPartnersResponse {
    pub partners: Vec<PartnerInfo>,
    //TODO replace with partner definition that includes additional partner info
}

#[utoipa::path(
    post,
    path = "/api/partnerships/transfers",
    params(),
    request_body = ListTransferPartnersRequest,
    responses(
        (status = 200, description = "List of transfer partners was successfully retrieved", body=GetTransferRedirectResponse),
        (status = 400, description = "Country code is invalid or not supported"),
    ),
)]
async fn list_transfer_partners(
    State(partnerships): State<Partnerships>,
    Json(request): Json<ListTransferPartnersRequest>,
) -> Result<Json<ListTransferPartnersResponse>, ApiError> {
    Ok(Json(ListTransferPartnersResponse {
        partners: partnerships.transfer_partners(request.country),
    }))
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct GetTransferRedirectRequest {
    pub partner: String,
    pub address: String,
}

#[derive(Serialize, Deserialize, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct GetTransferRedirectResponse {
    pub redirect_info: RedirectInfo,
}

#[utoipa::path(
    post,
    path = "/api/partnerships/transfers/redirect",
    params(),
    request_body = GetTransferRedirectRequest,
    responses(
        (status = 200, description = "Transfer redirect URL for a given partner was successfully retrieved", body=GetTransferRedirectResponse),
        (status = 503, description = "Partner is temporarily unavailable"),
    ),
)]
async fn get_transfer_redirect(
    State(partnerships): State<Partnerships>,
    Json(request): Json<GetTransferRedirectRequest>,
) -> Result<Json<GetTransferRedirectResponse>, ApiError> {
    let redirect_info = partnerships
        .transfer_redirect_url(request.address, request.partner)
        .await?;
    Ok(Json(GetTransferRedirectResponse { redirect_info }))
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct ListPurchaseQuotesRequest {
    //TODO: W-5560 use ISO country code based enum
    pub country: String,
    //TODO: W-5560 use money format
    pub fiat_amount: f64,
    pub fiat_currency: CurrencyCode,
    pub payment_method: PaymentMethod,
}

#[derive(Serialize, Deserialize, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct ListPurchaseQuotesResponse {
    quotes: Vec<Quote>,
}

#[utoipa::path(
    post,
    path = "/api/partnerships/purchases/quotes",
    params(),
    request_body = ListPurchaseQuotesRequest,
    responses(
        (status = 200, description = "Purchase quotes were successfully retrieved", body=ListPurchaseQuotesResponse),
        (status = 422, description = "Invalid parameters"),
    ),
)]
async fn list_purchase_quotes(
    State(partnerships): State<Partnerships>,
    Json(request): Json<ListPurchaseQuotesRequest>,
) -> Result<Json<ListPurchaseQuotesResponse>, ApiError> {
    let quotes = partnerships
        .quote(
            request.country,
            request.fiat_amount,
            request.fiat_currency,
            request.payment_method,
        )
        .await;
    Ok(Json(ListPurchaseQuotesResponse { quotes }))
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct GetPurchaseRedirectRequest {
    pub address: String,
    pub fiat_amount: f64,
    pub fiat_currency: CurrencyCode,
    pub partner: String,
    pub payment_method: PaymentMethod,
    pub quote_id: Option<String>,
}

#[derive(Serialize, Deserialize, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct GetPurchaseRedirectResponse {
    pub redirect_info: RedirectInfo,
}

#[utoipa::path(
    post,
    path = "/api/partnerships/purchases/redirect",
    params(),
    request_body = GetPurchaseRedirectRequest,
    responses(
        (status = 200, description = "Purchase redirect URL for a given partner was successfully retrieved", body=GetPurchaseRedirectResponse),
        (status = 503, description = "Partner is temporarily unavailable"),
    ),
)]
async fn get_purchase_redirect(
    State(partnerships): State<Partnerships>,
    Json(request): Json<GetPurchaseRedirectRequest>,
) -> Result<Json<GetPurchaseRedirectResponse>, ApiError> {
    let redirect_info = partnerships
        .purchase_redirect_url(
            request.address,
            request.fiat_amount,
            request.fiat_currency,
            request.partner,
            request.payment_method,
            request.quote_id,
        )
        .await?;
    Ok(Json(GetPurchaseRedirectResponse { redirect_info }))
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct PurchaseOptionsRequest {
    //TODO: W-5560 use ISO country code based enum
    pub country: String,
    pub fiat_currency: CurrencyCode,
}

#[derive(Serialize, Deserialize, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct PurchaseOptionsResponse {
    purchase_options: PurchaseOptions,
}

#[utoipa::path(
    get,
    path = "/api/partnerships/purchases/options",
    params(
        ("country" = String, Query, description = "Country code"),
        ("fiat_currency" = FiatCurrency, Query, description = "Currency code")
    ),
    responses(
    (status = 200, description = "Purchase options were successfully retrieved", body=PurchaseOptionsRequest),
    (status = 422, description = "Invalid parameters"),
    ),
)]
async fn get_purchase_options(
    State(partnerships): State<Partnerships>,
    request: Query<PurchaseOptionsRequest>,
) -> Result<Json<PurchaseOptionsResponse>, ApiError> {
    let purchase_options = partnerships
        .purchase_options(request.country.clone(), request.fiat_currency.clone())
        .await;
    Ok(Json(PurchaseOptionsResponse { purchase_options }))
}
