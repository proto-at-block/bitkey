use axum::{body::Bytes, extract::State, routing::post, Json, Router};
use axum_extra::TypedHeader;
use errors::ApiError;
use http_server::{
    router::RouterBuilder,
    swagger::{SwaggerEndpoint, Url},
};
use serde::{Deserialize, Serialize};
use strum_macros::EnumString;
use tracing::instrument;
use utoipa::{OpenApi, ToSchema};

use crate::{
    entities::{Issue, IssueSla, Webhook},
    metrics,
    webhook::{
        linear_signature, process_issue_sla_breached, process_issue_update,
        validate_and_parse_webhook,
    },
};

type WebhookSecret = String;

pub const TEST_LINEAR_WEBHOOK_SECRET: &str = "linear-webhook-secret";

#[derive(Deserialize, EnumString, Clone, PartialEq)]
#[serde(rename_all = "lowercase")]
pub enum LinearMode {
    Test,
    Environment,
}

#[derive(Deserialize)]
pub struct Config {
    pub linear: LinearMode,
}

#[derive(Clone, axum_macros::FromRef)]
pub struct RouteState(pub WebhookSecret);

impl RouteState {
    pub fn new(config: Config) -> Self {
        match config.linear {
            LinearMode::Environment => {
                let webhook_secret = std::env::var("LINEAR_WEBHOOK_SECRET")
                    .expect("LINEAR_WEBHOOK_SECRET must be set");
                Self(webhook_secret)
            }
            LinearMode::Test => Self(TEST_LINEAR_WEBHOOK_SECRET.to_string()),
        }
    }
}

impl RouterBuilder for RouteState {
    fn unauthed_router(&self) -> Router {
        Router::new()
            .route("/api/linear/webhook", post(webhook))
            .route_layer(metrics::FACTORY.route_layer("linear".to_owned()))
            .with_state(self.to_owned())
    }
}

impl From<RouteState> for SwaggerEndpoint {
    fn from(_: RouteState) -> Self {
        (
            Url::new("Linear", "/docs/linear/openapi.json"),
            ApiDoc::openapi(),
        )
    }
}

#[derive(OpenApi)]
#[openapi(
    paths(
        webhook,
    ),
    components(
        schemas(
            Webhook,
            WebhookResponse,
        )
    ),
    tags(
        (name = "Linear", description = "Linear webhook processing")
    )
)]
struct ApiDoc;

#[derive(Debug, Serialize, Deserialize, ToSchema)]
pub struct WebhookResponse {}

#[utoipa::path(
    post,
    path = "/api/linear/webhook",
    responses(
        (status = 200, description = "Webhook processing succeeded", body=WebhookResponse),
        (status = 500, description = "Webhook processing failed")
    )
)]
#[instrument(err, skip(webhook_secret, signature, body_bytes))]
pub async fn webhook(
    State(webhook_secret): State<WebhookSecret>,
    TypedHeader(signature): TypedHeader<linear_signature::Header>,
    body_bytes: Bytes,
) -> Result<Json<WebhookResponse>, ApiError> {
    let request = validate_and_parse_webhook(webhook_secret, signature, body_bytes)?;

    match request {
        Webhook::Issue(Issue::Update(issue_update)) => process_issue_update(issue_update),
        Webhook::IssueSla(IssueSla::Breached(issue_sla_breached)) => {
            process_issue_sla_breached(issue_sla_breached)
        }
        _ => {}
    }

    Ok(Json(WebhookResponse {}))
}
