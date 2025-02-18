use thiserror::Error;

use errors::ApiError;

#[derive(Debug, Error)]
pub enum PromotionCodeError {
    #[error("Database error due to error: {0}")]
    DatabaseError(#[from] database::ddb::DatabaseError),
    #[error(transparent)]
    ReqwestError(#[from] reqwest::Error),
    #[error(transparent)]
    SerdeJsonError(#[from] serde_json::Error),
    #[error(transparent)]
    HttpMiddlewareError(#[from] reqwest_middleware::Error),
}

impl From<PromotionCodeError> for ApiError {
    fn from(val: PromotionCodeError) -> Self {
        let err_msg = val.to_string();
        match val {
            PromotionCodeError::DatabaseError(_)
            | PromotionCodeError::ReqwestError(_)
            | PromotionCodeError::SerdeJsonError(_)
            | PromotionCodeError::HttpMiddlewareError(_) => {
                ApiError::GenericInternalApplicationError(err_msg)
            }
        }
    }
}
