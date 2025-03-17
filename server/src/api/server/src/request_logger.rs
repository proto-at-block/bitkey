use axum::{
    body::Body,
    extract::{Request, State},
    middleware::Next,
    response::{IntoResponse, Response},
};
use feature_flags::{flag::Flag, service::Service as FeatureFlagsService};
use http::StatusCode;
use http_body_util::BodyExt;
use tracing::error;

const REQUEST_LOGGING_ENABLED: Flag<bool> = Flag::new("f8e-request-logging-enabled");

#[derive(Clone)]
pub(crate) struct RequestLoggerState {
    log_requests: bool,
}

impl RequestLoggerState {
    pub(crate) fn new(feature_flags_service: &FeatureFlagsService) -> Self {
        // For safety, assume we're on production unless the ROCKET_PROFILE env var explicitly
        // states otherwise.
        let is_production = std::env::var("ROCKET_PROFILE")
            .map(|profile| profile == "production")
            .unwrap_or(true);

        // Use a flag so we can additionally disable on dev/staging if it gets too noisy or breaks something.
        // Either way, this logging can't be enabled on production because of the above.
        let log_requests = !is_production
            && REQUEST_LOGGING_ENABLED
                .resolver(feature_flags_service)
                .resolve();

        Self { log_requests }
    }
}

pub(crate) async fn log_requests(
    State(state): State<RequestLoggerState>,
    request: Request,
    next: Next,
) -> Response {
    let request = if state.log_requests {
        match log_request(request).await {
            Ok(req) => req,
            Err(resp) => return resp,
        }
    } else {
        request
    };

    let response = next.run(request).await;

    if state.log_requests {
        log_response(response).await
    } else {
        response
    }
}

async fn log_request(request: Request) -> Result<Request, Response> {
    let (parts, body) = request.into_parts();

    let body_bytes = match body.collect().await {
        Ok(b) => b.to_bytes(),
        Err(e) => {
            error!("Failed to read request body: {:?}", e);
            return Err(StatusCode::INTERNAL_SERVER_ERROR.into_response());
        }
    };

    tracing::info!(headers = ?parts.headers, body = ?body_bytes, "Request");

    Ok(Request::from_parts(parts, Body::from(body_bytes)))
}

async fn log_response(response: Response) -> Response {
    let (parts, body) = response.into_parts();

    let body_bytes = match body.collect().await {
        Ok(b) => b.to_bytes(),
        Err(e) => {
            error!("Failed to read response body: {:?}", e);
            return StatusCode::INTERNAL_SERVER_ERROR.into_response();
        }
    };

    tracing::info!(headers = ?parts.headers, body = ?body_bytes, "Response");

    Response::from_parts(parts, Body::from(body_bytes))
}
