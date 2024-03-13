use errors::{ApiError, ErrorCode};
use thiserror::Error;

#[derive(Debug, Error)]
pub enum NotificationClientsError {
    #[error("Failed to send targeted email via Iterable")]
    IterableSendTargetedEmailError,
    #[error("Failed to update user via Iterable")]
    IterableUpdateUserError,
    #[error("Failed to get user via Iterable")]
    IterableGetUserError,
    #[error("Failed to update user subscriptions via Iterable")]
    IterableUpdateUserSubscriptionsError,
    #[error("Failed to update user subscriptions via Iterable; nonexistent user")]
    IterableUpdateUserSubscriptionsNonexistentUserError,
    #[error("Failed to subscribe user via Iterable")]
    IterableSubscribeUserError,
    #[error("Timeout exceeded waiting for touchpoint user")]
    IterableWaitForTouchpointUserError,
    #[error("Iterable considered the email address invalid")]
    IterableInvalidEmailAddressError,
    #[error("Failed to create message via Twilio")]
    TwilioCreateMessageError,
    #[error("Failed to lookup phone number via Twilio")]
    TwilioLookupError,
    #[error("Unsupported SMS country code")]
    TwilioUnsupportedSmsCountryCodeError,
    #[error(transparent)]
    ReqwestError(#[from] reqwest::Error),
    #[error(transparent)]
    MacError(#[from] hmac::digest::MacError),
    #[error(transparent)]
    Sha1InvalidKeyLength(#[from] sha1::digest::InvalidLength),
    #[error(transparent)]
    Base64DecodeError(#[from] base64::DecodeError),
}

impl From<NotificationClientsError> for ApiError {
    fn from(val: NotificationClientsError) -> Self {
        let err_msg = val.to_string();
        match val {
            NotificationClientsError::IterableSendTargetedEmailError
            | NotificationClientsError::IterableUpdateUserError
            | NotificationClientsError::IterableUpdateUserSubscriptionsError
            | NotificationClientsError::IterableUpdateUserSubscriptionsNonexistentUserError
            | NotificationClientsError::IterableSubscribeUserError
            | NotificationClientsError::IterableWaitForTouchpointUserError
            | NotificationClientsError::IterableGetUserError
            | NotificationClientsError::TwilioCreateMessageError
            | NotificationClientsError::TwilioLookupError
            | NotificationClientsError::TwilioUnsupportedSmsCountryCodeError
            | NotificationClientsError::ReqwestError(_)
            | NotificationClientsError::Sha1InvalidKeyLength(_) => {
                ApiError::GenericInternalApplicationError(err_msg)
            }
            NotificationClientsError::IterableInvalidEmailAddressError => ApiError::Specific {
                code: ErrorCode::InvalidEmailAddress,
                detail: Some(err_msg),
                field: None,
            },
            NotificationClientsError::MacError(_)
            | NotificationClientsError::Base64DecodeError(_) => {
                ApiError::GenericUnauthorized(err_msg)
            }
        }
    }
}
