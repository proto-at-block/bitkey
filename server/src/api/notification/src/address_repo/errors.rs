use bdk_utils::bdk::bitcoin::address;
use database::ddb::DatabaseError;
use errors::ApiError;
use thiserror::Error;
use tracing::{event, Level};
use types::account::identifiers::AccountId;

#[derive(Debug, Error)]
pub enum Error {
    #[error("Address exists, but AccountId is different: [{0}: {1}]")]
    AccountMismatchError(String, AccountId),
    #[error(transparent)]
    BdkAddressError(#[from] address::Error),
    #[error(transparent)]
    DatabaseError(#[from] DatabaseError),
    #[error("Internal error: {0}")]
    InternalError(String),
}

impl From<Error> for ApiError {
    fn from(val: Error) -> Self {
        match val {
            Error::AccountMismatchError(addr, acct) => {
                event!(
                    Level::ERROR,
                    "Address associated with multiple account ids. Uh oh!: {} -> {}",
                    addr,
                    acct.to_string()
                );
            }
            Error::BdkAddressError(err) => {
                event!(
                    Level::ERROR,
                    "Address serialization error: {}",
                    err.to_string()
                );
                return ApiError::GenericBadRequest(err.to_string());
            }
            Error::DatabaseError(err) => {
                event!(Level::ERROR, "Database error: {}", err.to_string());
            }
            Error::InternalError(err_msg) => {
                event!(Level::ERROR, "Unexpected error: {}", err_msg);
            }
        }
        ApiError::GenericInternalApplicationError("Unexpected internal error".to_string())
    }
}
