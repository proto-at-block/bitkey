use errors::ApiError;
use thiserror::Error;

#[derive(Debug, Error)]
pub enum AccountError {
    #[error("Invalid update to account properties")]
    InvalidUpdateAccountProperties,
}

impl From<AccountError> for ApiError {
    fn from(e: AccountError) -> Self {
        let err_msg = e.to_string();
        match e {
            AccountError::InvalidUpdateAccountProperties => {
                ApiError::GenericInternalApplicationError(err_msg)
            }
        }
    }
}
