use authn_authz::{
    key_claims::{APP_SIG_HEADER, HW_SIG_HEADER},
    userpool::cognito_user::CognitoUser,
};
use http::{header, request::Builder};
use types::account::identifiers::AccountId;

pub trait AuthenticatedRequest {
    fn authenticated(self, account_id: &AccountId, app_signed: bool, hw_signed: bool) -> Self;
    fn recovery_authenticated(self, account_id: &AccountId) -> Self;
}

impl AuthenticatedRequest for Builder {
    fn authenticated(mut self, account_id: &AccountId, app_signed: bool, hw_signed: bool) -> Self {
        let access_token = authn_authz::test_utils::get_test_access_token_for_cognito_user(
            &CognitoUser::Wallet(account_id.to_owned()),
        );
        let headers = self.headers_mut().unwrap();
        headers.insert(
            header::AUTHORIZATION,
            format!("Bearer {access_token}").parse().unwrap(),
        );
        if app_signed {
            headers.insert(
                APP_SIG_HEADER,
                authn_authz::test_utils::sign_with_app_key(&access_token)
                    .parse()
                    .unwrap(),
            );
        }
        if hw_signed {
            headers.insert(
                HW_SIG_HEADER,
                authn_authz::test_utils::sign_with_hw_key(&access_token)
                    .parse()
                    .unwrap(),
            );
        }
        self
    }

    fn recovery_authenticated(mut self, account_id: &AccountId) -> Self {
        let access_token = authn_authz::test_utils::get_test_access_token_for_cognito_user(
            &CognitoUser::Recovery(account_id.to_owned()),
        );
        let headers = self.headers_mut().unwrap();
        headers.insert(
            header::AUTHORIZATION,
            format!("Bearer {access_token}").parse().unwrap(),
        );
        self
    }
}
