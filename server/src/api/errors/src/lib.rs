use axum::response::IntoResponse;
use axum::Json;
use http::StatusCode;
use serde::Serialize;

use strum_macros::Display;
use thiserror::Error;
use tracing::{error, event, Level};

#[derive(Error, Debug, Clone, PartialEq)]
pub enum ApiError {
    #[error("Internal Application Error {0}")]
    GenericInternalApplicationError(String),
    #[error("Bad Request {0}")]
    GenericBadRequest(String),
    #[error("Forbidden {0}")]
    GenericForbidden(String),
    #[error("Unauthorized {0}")]
    GenericUnauthorized(String),
    #[error("Not Found {0}")]
    GenericNotFound(String),
    #[error("Service Unavailable {0}")]
    GenericServiceUnavailable(String),
    #[error("Conflict {0}")]
    GenericConflict(String),
    #[error("Unavailable For Legal Reasons {0}")]
    GenericUnavailableForLegalReasons(String),
    #[error("{code}")]
    Specific {
        code: ErrorCode,
        detail: Option<String>,
        field: Option<String>,
    },
}

// Ref: https://github.com/squareup/go-square/blob/f142a1b4e6d96e0a4ab938e8553cfbe50fbf53b4/xp/connect-public-protos/protos/squareup/connect/v2/resources/error.proto
#[derive(Clone, Debug, Serialize, Display)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum ErrorCategory {
    // An error occurred with the API itself.
    ApiError,

    // An authentication error occurred. Most commonly, the request had
    //   a missing, malformed, or otherwise invalid `Authorization` header.
    AuthenticationError,

    // The request was invalid. Most commonly, a required parameter was
    //   missing, or a provided parameter had an invalid value.
    InvalidRequestError,
}

// Ref: https://github.com/squareup/go-square/blob/f142a1b4e6d96e0a4ab938e8553cfbe50fbf53b4/xp/connect-public-protos/protos/squareup/connect/v2/resources/error.proto
#[derive(Clone, Debug, Serialize, Display, PartialEq)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum ErrorCode {
    // Category: ApiError
    // A general server error occurred.
    InternalServerError,
    // Service Unavailable - a general error occurred.
    ServiceUnavailable,

    // Category: AuthenticationError
    // A general access error occurred.
    Forbidden,
    Unauthorized,
    // Recovery
    CommsVerificationRequired,

    // Category: InvalidRequestError
    // A general error occurred with the request.
    BadRequest,
    // Conflict - a general error occurred.
    Conflict,
    // Not Found - a general error occurred.
    NotFound,
    // Unavailable For Legal Reasons - a general error occurred.
    UnavailableForLegalReasons,

    AccountNotFound,

    // Comms Verification
    CodeMismatch, // Also used for RecoveryRelationship
    CodeExpired,
    // Onboarding
    UnsupportedCountryCode,
    TouchpointAlreadyActive,
    InvalidPhoneNumber,
    InvalidEmailAddress,
    // Onboarding & Recovery
    AppAuthPubkeyInUse,
    HwAuthPubkeyInUse,
    RecoveryAuthPubkeyInUse,
    // Recovery,
    RecoveryAlreadyExists,
    NoRecoveryExists,
    // RecoveryRelationship
    InvitationExpired,
    MaxTrustedContactsReached,
    MaxProtectedCustomersReached,
    // Money Movement,
    NoSpendingLimitExists,
}

// An ErrorCode always maps to a single ErrorCategory
impl From<ErrorCode> for ErrorCategory {
    fn from(value: ErrorCode) -> Self {
        match value {
            ErrorCode::InternalServerError | ErrorCode::ServiceUnavailable => {
                ErrorCategory::ApiError
            }
            ErrorCode::Forbidden
            | ErrorCode::Unauthorized
            | ErrorCode::CommsVerificationRequired => ErrorCategory::AuthenticationError,
            ErrorCode::BadRequest
            | ErrorCode::NotFound
            | ErrorCode::UnsupportedCountryCode
            | ErrorCode::TouchpointAlreadyActive
            | ErrorCode::CodeMismatch
            | ErrorCode::CodeExpired
            | ErrorCode::RecoveryAlreadyExists
            | ErrorCode::NoRecoveryExists
            | ErrorCode::Conflict
            | ErrorCode::NoSpendingLimitExists
            | ErrorCode::AppAuthPubkeyInUse
            | ErrorCode::HwAuthPubkeyInUse
            | ErrorCode::RecoveryAuthPubkeyInUse
            | ErrorCode::InvalidPhoneNumber
            | ErrorCode::InvalidEmailAddress
            | ErrorCode::InvitationExpired
            | ErrorCode::AccountNotFound
            | ErrorCode::MaxTrustedContactsReached
            | ErrorCode::MaxProtectedCustomersReached
            | ErrorCode::UnavailableForLegalReasons => ErrorCategory::InvalidRequestError,
        }
    }
}

// An ErrorCode always maps to a single StatusCode
impl From<ErrorCode> for StatusCode {
    fn from(value: ErrorCode) -> Self {
        match value {
            ErrorCode::InternalServerError => StatusCode::INTERNAL_SERVER_ERROR,
            ErrorCode::ServiceUnavailable => StatusCode::SERVICE_UNAVAILABLE,
            ErrorCode::Forbidden | ErrorCode::CommsVerificationRequired => StatusCode::FORBIDDEN,
            ErrorCode::UnavailableForLegalReasons => StatusCode::UNAVAILABLE_FOR_LEGAL_REASONS,
            ErrorCode::BadRequest
            | ErrorCode::UnsupportedCountryCode
            | ErrorCode::CodeMismatch
            | ErrorCode::CodeExpired
            | ErrorCode::AppAuthPubkeyInUse
            | ErrorCode::HwAuthPubkeyInUse
            | ErrorCode::RecoveryAuthPubkeyInUse
            | ErrorCode::InvalidPhoneNumber
            | ErrorCode::InvalidEmailAddress => StatusCode::BAD_REQUEST,
            ErrorCode::NotFound | ErrorCode::AccountNotFound => StatusCode::NOT_FOUND,
            ErrorCode::TouchpointAlreadyActive
            | ErrorCode::RecoveryAlreadyExists
            | ErrorCode::NoRecoveryExists
            | ErrorCode::Conflict
            | ErrorCode::InvitationExpired
            | ErrorCode::MaxTrustedContactsReached
            | ErrorCode::MaxProtectedCustomersReached => StatusCode::CONFLICT,
            ErrorCode::NoSpendingLimitExists => StatusCode::METHOD_NOT_ALLOWED,
            ErrorCode::Unauthorized => StatusCode::UNAUTHORIZED,
        }
    }
}

// Ref: https://plathome.sqprod.co/styleguide/guidance/error-overview?bu=block&p=rest
#[derive(Clone, Debug, Serialize)]
pub struct ErrorResponseBodyError {
    pub category: ErrorCategory,
    pub code: ErrorCode,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub detail: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub field: Option<String>,
}

// Ref: https://plathome.sqprod.co/styleguide/guidance/error-overview?bu=block&p=rest
#[derive(Clone, Debug, Serialize)]
pub struct ErrorResponseBody {
    pub errors: Vec<ErrorResponseBodyError>,
}

impl IntoResponse for ApiError {
    fn into_response(self) -> axum::response::Response {
        let (code, detail, field) = match self {
            ApiError::GenericInternalApplicationError(message) => {
                (ErrorCode::InternalServerError, Some(message), None)
            }
            ApiError::GenericBadRequest(message) => (ErrorCode::BadRequest, Some(message), None),
            ApiError::GenericForbidden(message) => (ErrorCode::Forbidden, Some(message), None),
            ApiError::GenericUnauthorized(message) => {
                (ErrorCode::Unauthorized, Some(message), None)
            }
            ApiError::GenericNotFound(message) => (ErrorCode::NotFound, Some(message), None),
            ApiError::GenericServiceUnavailable(message) => {
                (ErrorCode::ServiceUnavailable, Some(message), None)
            }
            ApiError::GenericConflict(message) => (ErrorCode::Conflict, Some(message), None),
            ApiError::GenericUnavailableForLegalReasons(message) => {
                (ErrorCode::UnavailableForLegalReasons, Some(message), None)
            }
            ApiError::Specific {
                code,
                detail,
                field,
            } => (code, detail, field),
        };

        if let Some(detail) = detail.as_ref() {
            error!(detail);
        }

        let error = ErrorResponseBodyError {
            category: code.clone().into(),
            code: code.clone(),
            detail,
            field,
        };

        let body = ErrorResponseBody {
            errors: vec![error],
        };

        (StatusCode::from(code), Json(body)).into_response()
    }
}

//TODO: Move over to thiserror
#[derive(Debug)]
pub enum RouteError {
    InvalidIdentifier(external_identifier::Error),
    NoActiveAuthKeys,
    NoActiveSpendKeyset,
    DatetimeFormatError,
    MutateDatetimeError,
    InvalidNetworkForNewKeyset,
    InvalidNetworkForNewKeyDefinition,
}

impl From<RouteError> for ApiError {
    fn from(val: RouteError) -> Self {
        match val {
            RouteError::InvalidIdentifier(id) => {
                event!(Level::WARN, "Invalid ID {}", id,);
                ApiError::GenericBadRequest("Invalid ID".to_string())
            }
            RouteError::NoActiveAuthKeys => {
                let err = "Account without active authentication keyset";
                event!(Level::ERROR, err);
                ApiError::GenericInternalApplicationError(err.to_string())
            }
            RouteError::NoActiveSpendKeyset => {
                event!(Level::ERROR, "Account without active spending keyset");
                ApiError::GenericInternalApplicationError(
                    "Account without active spending keyset".to_string(),
                )
            }
            RouteError::DatetimeFormatError => {
                event!(Level::ERROR, "Issue formatting OffsetDateTime");
                ApiError::GenericInternalApplicationError("Failure formatting Datetime".to_string())
            }
            RouteError::MutateDatetimeError => {
                event!(Level::ERROR, "Issue updating the OffsetDateTime");
                ApiError::GenericInternalApplicationError("Failure updating Datetime".to_string())
            }
            RouteError::InvalidNetworkForNewKeyset => {
                let msg = "Request for different network than existing keyset";
                event!(Level::ERROR, msg);
                ApiError::GenericBadRequest(msg.to_string())
            }
            RouteError::InvalidNetworkForNewKeyDefinition => {
                let msg = "Request for different network than existing key definition";
                event!(Level::ERROR, msg);
                ApiError::GenericBadRequest(msg.to_string())
            }
        }
    }
}
