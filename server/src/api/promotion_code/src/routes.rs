use axum::routing::post;
use axum::Router;
use axum::{extract::State, Json};
use axum_extra::{
    headers::{authorization::Bearer, Authorization},
    TypedHeader,
};
use serde::{Deserialize, Serialize};
use tracing::instrument;
use utoipa::openapi::security::{Http, HttpAuthScheme, SecurityScheme};
use utoipa::{Modify, OpenApi, ToSchema};

use errors::ApiError;
use http_server::router::RouterBuilder;
use http_server::swagger::{SwaggerEndpoint, Url};

use crate::service::Service as PromotionCodeService;
use crate::web_shop::{WebShopClient, WebShopMode};
use crate::webhook::{WebhookMode, WebhookValidator};

#[derive(Clone, Debug, Deserialize)]
pub struct Config {
    pub web_shop: WebShopMode,
    pub webhook: WebhookMode,
}

#[derive(Clone, axum_macros::FromRef)]
pub struct RouteState(
    pub Config,
    pub PromotionCodeService,
    pub WebShopClient,
    pub WebhookValidator,
);

impl RouterBuilder for RouteState {
    fn unauthed_router(&self) -> Router {
        Router::new()
            .route(
                "/api/promotion-code-redemptions",
                post(handle_promotion_code_redemption_webhook),
            )
            .with_state(self.to_owned())
    }
}

impl From<RouteState> for SwaggerEndpoint {
    fn from(_: RouteState) -> Self {
        (
            Url::new("Promotion Codes", "/docs/promotion-codes/openapi.json"),
            ApiDoc::openapi(),
        )
    }
}

#[derive(OpenApi)]
#[openapi(
    paths(
        handle_promotion_code_redemption_webhook,
    ),
    components(
        schemas(
            CodeRedemptionWebhookRequest,
            CodeRedemptionWebhookResponse,
        )
    ),
    tags(
        (name = "Promotion Codes", description = "Promotion codes endpoints")
    ),
    modifiers(&SecurityAddon)
)]
struct ApiDoc;

struct SecurityAddon;

impl Modify for SecurityAddon {
    fn modify(&self, openapi: &mut utoipa::openapi::OpenApi) {
        let components = openapi
            .components
            .as_mut()
            .expect("Should have component since it is already registered.");
        components.add_security_scheme(
            "bearerAuth",
            SecurityScheme::Http(Http::new(HttpAuthScheme::Bearer)),
        )
    }
}

pub const WEBHOOK_API_KEY_HEADER: &str = "Webhook-Internal-Api-Key";

#[derive(Debug, Serialize, Deserialize, ToSchema)]
pub struct CodeRedemptionWebhookRequest {
    pub code: String,
}

#[derive(Debug, Deserialize, Serialize, ToSchema)]
pub struct CodeRedemptionWebhookResponse {}

#[instrument(skip(webhook_validator, bearer, promotion_code_service), fields(code = %redemption.code))]
#[utoipa::path(
    post,
    path = "/api/promotion-code-redemptions",
    request_body = CodeRedemptionWebhookRequest,
    responses(
        (status = 200, description = "Redemption processed successfully", body = CodeRedemptionWebhookResponse),
        (status = 400, description = "Bad Request - missing or invalid request"),
        (status = 401, description = "Unauthorized - invalid API key"),
        (status = 500, description = "Internal server error processing redemption")
    ),
    security(
        ("bearerAuth" = [])
    ),
    tag = "Webhooks"
)]
async fn handle_promotion_code_redemption_webhook(
    State(promotion_code_service): State<PromotionCodeService>,
    State(webhook_validator): State<WebhookValidator>,
    TypedHeader(bearer): TypedHeader<Authorization<Bearer>>,
    Json(redemption): Json<CodeRedemptionWebhookRequest>,
) -> Result<Json<CodeRedemptionWebhookResponse>, ApiError> {
    webhook_validator.validate(bearer.token())?;
    promotion_code_service
        .mark_code_as_redeemed(&redemption.code)
        .await?;

    Ok(Json(CodeRedemptionWebhookResponse {}))
}
