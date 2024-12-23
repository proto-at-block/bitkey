use axum::http::StatusCode;
use axum::response::IntoResponse;
use axum::Json;
use serde_json::json;
use strum_macros::Display;
use thiserror::Error;
use tracing::{event, Level};

#[derive(Error, Debug, Clone)]
pub enum ApiError {
    #[error("Internal Application Error {0}")]
    InternalApplicationError(String),
    #[error("Bad Request {0}")]
    BadRequest(String),
    #[error("Resource Not Found {0}")]
    NotFound(String),
}

#[derive(Debug, Display)]
pub enum AnalyticsError {
    InvalidEvent(String),
    DestinationError(String),
}

impl IntoResponse for ApiError {
    fn into_response(self) -> axum::response::Response {
        let (status, message) = match self {
            ApiError::InternalApplicationError(message) => {
                (StatusCode::INTERNAL_SERVER_ERROR, message)
            }
            ApiError::BadRequest(message) => (StatusCode::BAD_REQUEST, message),
            ApiError::NotFound(message) => (StatusCode::NOT_FOUND, message),
        };

        let body = Json(json!({
            "message": message,
        }));

        (status, body).into_response()
    }
}

impl From<AnalyticsError> for ApiError {
    fn from(val: AnalyticsError) -> Self {
        match val {
            AnalyticsError::InvalidEvent(e) => {
                event!(Level::ERROR, "Invalid event: {}", e);
                ApiError::BadRequest("Invalid event".to_string())
            }
            AnalyticsError::DestinationError(e) => {
                event!(
                    Level::ERROR,
                    "Failed to send the event to destination: {}",
                    e
                );
                ApiError::InternalApplicationError("Destination error".to_string())
            }
        }
    }
}
