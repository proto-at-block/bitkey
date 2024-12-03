use axum::extract::Path;
use axum::extract::Query;
use axum::routing::get;
use axum::{extract::State, routing::post, Json, Router};
use experimentation::claims::ExperimentationClaims;

use http_server::router::RouterBuilder;
use serde::{Deserialize, Serialize};
use utoipa::{OpenApi, ToSchema};

use account::service::Service as AccountService;
use aws_utils::secrets_manager::{FetchSecret, SecretsManager};
use feature_flags::service::Service as FeatureFlagsService;
use http_server::swagger::{SwaggerEndpoint, Url};
use partnerships_lib::models::partners::Partner;
use partnerships_lib::models::{PartnerTransaction, PurchaseOptions, SaleQuote, TransactionType};
use partnerships_lib::{
    errors::ApiError,
    models::{partners::PartnerInfo, PaymentMethod, Quote, RedirectInfo},
    Partnerships,
};

use types::currencies::CurrencyCode;
use userpool::userpool::UserPoolService;

#[derive(Clone, axum_macros::FromRef)]
pub struct RouteState(pub Partnerships, pub UserPoolService, pub AccountService);

impl RouteState {
    pub async fn new(
        user_pool_service: UserPoolService,
        feature_flag_service: FeatureFlagsService,
        account_service: AccountService,
    ) -> Self {
        let secrets_manager = SecretsManager::new().await;
        Self::from_secrets_manager(
            secrets_manager,
            user_pool_service,
            feature_flag_service,
            account_service,
        )
        .await
    }

    pub async fn from_secrets_manager(
        secrets_manager: impl FetchSecret,
        user_pool_service: UserPoolService,
        feature_flag_service: FeatureFlagsService,
        account_service: AccountService,
    ) -> Self {
        Self(
            Partnerships::new(secrets_manager, feature_flag_service).await,
            user_pool_service,
            account_service,
        )
    }
}

impl RouterBuilder for RouteState {
    fn basic_validation_router(&self) -> Router {
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
            .route(
                "/api/partnerships/partners/:partner/transactions/:id",
                get(get_partner_transaction),
            )
            .route("/api/partnerships/partners/:partner", get(get_partner))
            .route("/api/partnerships/sales/quotes", post(list_sale_quotes))
            .route(
                "/api/partnerships/sales/redirects",
                post(get_sales_redirect),
            )
            .with_state(self.to_owned())
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
            ListPurchaseQuotesRequest,
            ListPurchaseQuotesResponse,
            GetPurchaseRedirectRequest,
            GetRedirectResponse
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
        (status = 200, description = "List of transfer partners was successfully retrieved", body=GetRedirectResponse),
        (status = 400, description = "Country code is invalid or not supported"),
    ),
)]
async fn list_transfer_partners(
    State(partnerships): State<Partnerships>,
    experimentation_claims: ExperimentationClaims,
    Json(request): Json<ListTransferPartnersRequest>,
) -> Result<Json<ListTransferPartnersResponse>, ApiError> {
    Ok(Json(ListTransferPartnersResponse {
        partners: partnerships.transfer_partners(
            request.country,
            &experimentation_claims.account_context_key()?,
        ),
    }))
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct GetTransferRedirectRequest {
    pub partner: String,
    pub address: String,
    pub partner_transaction_id: Option<String>,
}

#[utoipa::path(
    post,
    path = "/api/partnerships/transfers/redirect",
    params(),
    request_body = GetTransferRedirectRequest,
    responses(
        (status = 200, description = "Transfer redirect URL for a given partner was successfully retrieved", body=GetRedirectResponse),
        (status = 503, description = "Partner is temporarily unavailable"),
    ),
)]
async fn get_transfer_redirect(
    State(partnerships): State<Partnerships>,
    Json(request): Json<GetTransferRedirectRequest>,
) -> Result<Json<GetRedirectResponse>, ApiError> {
    let redirect_info = partnerships
        .transfer_redirect_url(
            request.address,
            request.partner,
            request.partner_transaction_id,
        )
        .await?;
    Ok(Json(GetRedirectResponse { redirect_info }))
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
    experimentation_claims: ExperimentationClaims,
    Json(request): Json<ListPurchaseQuotesRequest>,
) -> Result<Json<ListPurchaseQuotesResponse>, ApiError> {
    let quotes = partnerships
        .quote(
            request.country,
            request.fiat_amount,
            request.fiat_currency,
            request.payment_method,
            &experimentation_claims.account_context_key()?,
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
    pub partner_transaction_id: Option<String>,
}

#[derive(Serialize, Deserialize, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct GetRedirectResponse {
    pub redirect_info: RedirectInfo,
}

#[utoipa::path(
    post,
    path = "/api/partnerships/purchases/redirect",
    params(),
    request_body = GetRedirectRequest,
    responses(
        (status = 200, description = "Purchase redirect URL for a given partner was successfully retrieved", body=GetRedirectResponse),
        (status = 503, description = "Partner is temporarily unavailable"),
    ),
)]
async fn get_purchase_redirect(
    State(partnerships): State<Partnerships>,
    Json(request): Json<GetPurchaseRedirectRequest>,
) -> Result<Json<GetRedirectResponse>, ApiError> {
    let redirect_info = partnerships
        .purchase_redirect_url(
            request.address,
            request.fiat_amount,
            request.fiat_currency,
            request.partner,
            request.payment_method,
            request.quote_id,
            request.partner_transaction_id,
        )
        .await?;
    Ok(Json(GetRedirectResponse { redirect_info }))
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
    (status = 200, description = "Purchase options were successfully retrieved", body=PurchaseOptionsResponse),
    (status = 422, description = "Invalid parameters"),
    ),
)]
async fn get_purchase_options(
    State(partnerships): State<Partnerships>,
    experimentation_claims: ExperimentationClaims,
    request: Query<PurchaseOptionsRequest>,
) -> Result<Json<PurchaseOptionsResponse>, ApiError> {
    let purchase_options = partnerships
        .purchase_options(
            request.country.clone(),
            request.fiat_currency,
            &experimentation_claims.account_context_key()?,
        )
        .await;
    Ok(Json(PurchaseOptionsResponse { purchase_options }))
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
pub struct GetPartnerTransactionRequest {
    partner: String,
    id: String,
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
pub struct GetPartnerTransactionQuery {
    #[serde(rename = "type")]
    transaction_type: Option<TransactionType>,
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
pub struct GetPartnerTransactionResponse {
    transaction: PartnerTransaction,
}

#[utoipa::path(
    get,
    path = "/api/partnerships/partners/:partner/transactions/:id",
    params(
        ("partner" = String, Path, description = "Partner name"),
        ("id" = String, Path, description = "Partner Transaction ID")
    ),
    responses(
        (status = 200, description = "Partner transaction was successfully retrieved", body=GetPartnerTransactionResponse),
        (status = 404, description = "Partner transaction not found"),
    ),
)]
async fn get_partner_transaction(
    State(partnerships): State<Partnerships>,
    request: Path<GetPartnerTransactionRequest>,
    query: Query<GetPartnerTransactionQuery>,
) -> Result<Json<GetPartnerTransactionResponse>, ApiError> {
    let transaction_type = query.transaction_type.unwrap_or(TransactionType::Purchase);
    let partner_transaction = partnerships
        .get_partner_transaction(&request.id, &request.partner, transaction_type)
        .await?;
    Ok(Json(GetPartnerTransactionResponse {
        transaction: partner_transaction,
    }))
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
pub struct GetPartnerRequest {
    partner: Partner,
}

#[utoipa::path(
    get,
    path = "/api/partnerships/partners/:partner",
    params(("partner" = String, Path, description = "Partner name")),
    responses(
        (status = 200, description = "Partner info was successfully retrieved", body=PartnerInfo),
        (status = 404, description = "Partner not found"),
    ),
)]
async fn get_partner(
    State(partnerships): State<Partnerships>,
    request: Path<GetPartnerRequest>,
) -> Result<Json<PartnerInfo>, ApiError> {
    let partner_info = partnerships.get_partner(request.partner).await?;
    Ok(Json(partner_info))
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct ListSaleQuotesRequest {
    //TODO: W-5560 use ISO country code based enum
    pub country: String,
    pub crypto_amount: f64,
    pub fiat_currency: CurrencyCode,
}

#[derive(Serialize, Deserialize, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct ListSaleQuotesResponse {
    quotes: Vec<SaleQuote>,
}

#[utoipa::path(
    post,
    path = "/api/partnerships/sales/quotes",
    params(),
    request_body = ListSaleQuotesRequest,
    responses(
        (status = 200, description = "Sale quotes were successfully retrieved", body=ListSaleQuotesResponse),
        (status = 422, description = "Invalid parameters"),
    ),
)]
async fn list_sale_quotes(
    State(partnerships): State<Partnerships>,
    experimentation_claims: ExperimentationClaims,
    Json(request): Json<ListSaleQuotesRequest>,
) -> Result<Json<ListSaleQuotesResponse>, ApiError> {
    let quotes = partnerships
        .sale_quote(
            request.country,
            request.crypto_amount,
            request.fiat_currency,
            experimentation_claims.account_context_key()?,
        )
        .await;
    Ok(Json(ListSaleQuotesResponse { quotes }))
}

#[derive(Debug, Serialize, Deserialize, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct GetSaleRedirectRequest {
    pub refund_address: Option<String>,
    pub fiat_amount: f64,
    pub fiat_currency: CurrencyCode,
    pub crypto_amount: Option<f64>,
    pub crypto_currency: Option<CurrencyCode>,
    pub partner: String,
    pub quote_id: Option<String>,
}

#[utoipa::path(
    post,
    path = "/api/partnerships/sales/redirect",
    params(),
    request_body = GetSaleRedirectRequest,
    responses(
        (status = 200, description = "Sale redirect URL for a given partner was successfully retrieved", body=GetRedirectResponse),
        (status = 503, description = "Partner is temporarily unavailable"),
    ),
)]
async fn get_sales_redirect(
    State(partnerships): State<Partnerships>,
    Json(request): Json<GetSaleRedirectRequest>,
) -> Result<Json<GetRedirectResponse>, ApiError> {
    let redirect_info = partnerships
        .sale_redirect_url(
            request.refund_address,
            request.fiat_amount,
            request.fiat_currency,
            request.crypto_amount,
            request.crypto_currency,
            request.partner,
            request.quote_id,
        )
        .await?;
    Ok(Json(GetRedirectResponse { redirect_info }))
}
