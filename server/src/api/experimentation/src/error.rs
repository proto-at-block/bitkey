use errors::ApiError;
use feature_flags::Error as FeatureFlagsError;
use thiserror::Error;

#[derive(Debug, Error)]
pub enum ExperimentationError {
    #[error(transparent)]
    FeatureFlagsError(#[from] feature_flags::Error),
}

impl From<ExperimentationError> for ApiError {
    fn from(e: ExperimentationError) -> Self {
        let err_msg = e.to_string();
        match e {
            ExperimentationError::FeatureFlagsError(inner_err) => match inner_err {
                FeatureFlagsError::NotFound(_) => ApiError::GenericNotFound(err_msg),
                _ => ApiError::GenericInternalApplicationError(err_msg),
            },
        }
    }
}
