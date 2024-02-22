use errors::ApiError;
use thiserror::Error;

#[derive(Debug, Error)]
pub enum NotificationValidationError {
    #[error("Failure converting payload into Validator")]
    ToValidatorError,
}

impl From<NotificationValidationError> for ApiError {
    fn from(val: NotificationValidationError) -> Self {
        let err_msg = val.to_string();
        match val {
            NotificationValidationError::ToValidatorError => {
                ApiError::GenericInternalApplicationError(err_msg)
            }
        }
    }
}
