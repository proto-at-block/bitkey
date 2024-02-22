use crate::test_utils::AuthenticatedRequest;
use http::request::Builder;
use types::account::identifiers::AccountId;

pub mod axum;
pub mod worker;

#[derive(Debug, PartialEq, Eq)]
pub(crate) struct Response<T> {
    pub status_code: http::StatusCode,
    pub body: Option<T>,
    pub body_string: String,
}

#[derive(Debug)]
pub(super) enum CognitoAuthentication {
    Wallet {
        is_app_signed: bool,
        is_hardware_signed: bool,
    },
    Recovery,
}

pub(super) trait AuthenticatedRequestExt {
    fn with_authentication(self, auth: &CognitoAuthentication, account_id: &AccountId) -> Self;
}

impl AuthenticatedRequestExt for Builder {
    fn with_authentication(self, auth: &CognitoAuthentication, account_id: &AccountId) -> Self {
        match auth {
            CognitoAuthentication::Wallet {
                is_app_signed,
                is_hardware_signed,
            } => self.authenticated(account_id, *is_app_signed, *is_hardware_signed),
            CognitoAuthentication::Recovery => self.recovery_authenticated(account_id),
        }
    }
}
