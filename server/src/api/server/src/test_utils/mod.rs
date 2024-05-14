use authn_authz::key_claims::{APP_SIG_HEADER, HW_SIG_HEADER};
use bdk_utils::bdk::bitcoin::secp256k1::SecretKey;
use http::{header, request::Builder};
use types::{account::identifiers::AccountId, authn_authz::cognito::CognitoUser};
use userpool::test_utils::get_test_access_token_for_cognito_user;

pub trait AuthenticatedRequest {
    fn authenticated(
        self,
        account_id: &AccountId,
        app_seckey: Option<SecretKey>,
        hw_seckey: Option<SecretKey>,
    ) -> Self;
    fn authenticated_with_access_token(self, access_token: &str) -> Self;
    fn recovery_authenticated(self, account_id: &AccountId) -> Self;
}

impl AuthenticatedRequest for Builder {
    fn authenticated(
        mut self,
        account_id: &AccountId,
        app_seckey: Option<SecretKey>,
        hw_seckey: Option<SecretKey>,
    ) -> Self {
        let access_token =
            get_test_access_token_for_cognito_user(&CognitoUser::Wallet(account_id.to_owned()));
        let headers = self.headers_mut().unwrap();
        headers.insert(
            header::AUTHORIZATION,
            format!("Bearer {access_token}").parse().unwrap(),
        );
        if let Some(key) = app_seckey {
            headers.insert(
                APP_SIG_HEADER,
                authn_authz::test_utils::sign_with_app_key(&access_token, key)
                    .parse()
                    .unwrap(),
            );
        }
        if let Some(key) = hw_seckey {
            headers.insert(
                HW_SIG_HEADER,
                authn_authz::test_utils::sign_with_hw_key(&access_token, key)
                    .parse()
                    .unwrap(),
            );
        }
        self
    }

    fn authenticated_with_access_token(mut self, access_token: &str) -> Self {
        let headers = self.headers_mut().unwrap();
        headers.insert(
            header::AUTHORIZATION,
            format!("Bearer {access_token}").parse().unwrap(),
        );
        self
    }

    fn recovery_authenticated(mut self, account_id: &AccountId) -> Self {
        let access_token =
            get_test_access_token_for_cognito_user(&CognitoUser::Recovery(account_id.to_owned()));
        let headers = self.headers_mut().unwrap();
        headers.insert(
            header::AUTHORIZATION,
            format!("Bearer {access_token}").parse().unwrap(),
        );
        self
    }
}
