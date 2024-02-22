use axum::{
    extract::State,
    http::StatusCode,
    response::{IntoResponse, Response},
    routing::get,
    Router,
};

#[derive(Clone)]
pub struct Service;

impl Service {
    pub fn new(
        _feature_flags: feature_flags::service::Service,
    ) -> Result<Self, feature_flags::Error> {
        Ok(Self {})
    }
}

impl From<Service> for Router {
    fn from(value: Service) -> Self {
        Router::new().route("/", get(handler)).with_state(value)
    }
}

async fn handler(State(_service): State<Service>) -> Response {
    (StatusCode::OK, "ok").into_response()
}
