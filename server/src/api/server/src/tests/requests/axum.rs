use async_trait::async_trait;

use axum::body::Body;
use axum::Router;

use experimentation::routes::{
    GetAccountFeatureFlagsRequest, GetAppInstallationFeatureFlagsRequest, GetFeatureFlagsResponse,
};
use http::{header::CONTENT_TYPE, HeaderValue, Method, Request};
use http_body_util::BodyExt as ExternalBodyExt;
use recovery::state_machine::pending_recovery::PendingRecoveryResponse;
use recovery::state_machine::rotated_keyset::RotatedKeysetResponse;
use serde::{de::DeserializeOwned, Serialize};
use std::collections::HashMap;
use std::str::FromStr;
use tokio::sync::Mutex;
use tower::Service;
use types::notification::NotificationsPreferences;

use authn_authz::routes::{
    AuthenticateWithHardwareRequest, AuthenticateWithHardwareResponse,
    AuthenticateWithRecoveryAuthkeyRequest, AuthenticateWithRecoveryResponse,
    AuthenticationRequest, AuthenticationResponse, GetTokensRequest, GetTokensResponse,
};
use exchange_rate::routes::SupportedFiatCurrenciesResponse;
use mobile_pay::routes::{
    GetMobilePayResponse, MobilePaySetupRequest, MobilePaySetupResponse, SignTransactionData,
    SignTransactionResponse,
};
use notification::routes::{
    RegisterWatchAddressRequest, RegisterWatchAddressResponse, SendTestPushData,
    SendTestPushResponse,
};
use onboarding::routes::{
    AccountActivateTouchpointRequest, AccountActivateTouchpointResponse,
    AccountAddDeviceTokenRequest, AccountAddDeviceTokenResponse, AccountAddTouchpointRequest,
    AccountAddTouchpointResponse, AccountGetTouchpointsResponse, AccountVerifyTouchpointRequest,
    AccountVerifyTouchpointResponse, BdkConfigResponse, CompleteOnboardingRequest,
    CompleteOnboardingResponse, CreateAccountRequest, CreateAccountResponse, CreateKeysetRequest,
    CreateKeysetResponse, GetAccountKeysetsResponse, GetAccountStatusResponse,
    RotateSpendingKeysetRequest, UpgradeAccountRequest,
};
use types::account::identifiers::{AccountId, KeysetId};

use recovery::routes::{
    CompleteDelayNotifyRequest, CreateAccountDelayNotifyRequest, CreateRecoveryRelationshipRequest,
    CreateRecoveryRelationshipResponse, EndorseRecoveryRelationshipsRequest,
    EndorseRecoveryRelationshipsResponse, FetchSocialChallengeResponse,
    GetRecoveryRelationshipInvitationForCodeResponse, GetRecoveryRelationshipsResponse,
    RespondToSocialChallengeRequest, RespondToSocialChallengeResponse,
    RotateAuthenticationKeysRequest, RotateAuthenticationKeysResponse,
    SendAccountVerificationCodeRequest, SendAccountVerificationCodeResponse,
    StartSocialChallengeRequest, StartSocialChallengeResponse, UpdateDelayForTestRecoveryRequest,
    UpdateRecoveryRelationshipRequest, UpdateRecoveryRelationshipResponse,
    VerifyAccountVerificationCodeRequest, VerifyAccountVerificationCodeResponse,
    VerifySocialChallengeRequest, VerifySocialChallengeResponse,
};
use recovery::state_machine::RecoveryResponse;

use crate::test_utils::AuthenticatedRequest;
use crate::tests::{TestAuthenticationKeys, TestContext};

use super::{AuthenticatedRequestExt, CognitoAuthentication, Response};

pub struct TestClient {
    router: Mutex<Router>,
}

trait NoBodyExt {
    fn get(self) -> http::Request<Body>;
    fn delete(self) -> http::Request<Body>;
}

impl NoBodyExt for http::request::Builder {
    fn get(self) -> http::Request<Body> {
        self.method(Method::GET).body(Body::empty()).unwrap()
    }

    fn delete(self) -> http::Request<Body> {
        self.method(Method::DELETE).body(Body::empty()).unwrap()
    }
}

trait BodyExt<B> {
    fn post(self, body: B) -> http::Request<Body>;
    fn post_form(self, body: B) -> http::Request<Body>;
    fn put(self, body: B) -> http::Request<Body>;
}

impl<B> BodyExt<B> for http::request::Builder
where
    B: Serialize + Sync,
{
    fn post(self, body: B) -> http::Request<Body> {
        self.method(Method::POST)
            .header("Content-Type", "application/json")
            .body(Body::from(serde_json::to_vec(&body).unwrap()))
            .unwrap()
    }

    fn post_form(self, body: B) -> http::Request<Body> {
        self.method(Method::POST)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .body(Body::from(serde_urlencoded::to_string(&body).unwrap()))
            .unwrap()
    }

    fn put(self, body: B) -> http::Request<Body> {
        self.method(Method::PUT)
            .header("Content-Type", "application/json")
            .body(Body::from(serde_json::to_vec(&body).unwrap()))
            .unwrap()
    }
}

#[async_trait]
trait IntoResponseExt<R> {
    async fn call(self, router: &Mutex<Router>) -> Response<R>;
}

#[async_trait]
impl<R> IntoResponseExt<R> for http::Request<Body>
where
    R: DeserializeOwned,
{
    async fn call(self, router: &Mutex<Router>) -> Response<R> {
        let response = router.lock().await.call(self).await.unwrap();

        let status_code = response.status();
        let content_type = response
            .headers()
            .get(CONTENT_TYPE)
            .unwrap_or(&HeaderValue::from_static(""))
            .to_owned();

        let raw_body = response.into_body().collect().await.unwrap().to_bytes();
        let body = serde_json::from_slice(&raw_body).ok();
        if body.is_some() {
            assert_eq!(content_type, HeaderValue::from_static("application/json"));
        }

        Response {
            status_code,
            body,
            body_string: String::from_utf8_lossy(&raw_body).to_string(),
        }
    }
}

impl TestClient {
    pub(crate) async fn new(router: Router) -> Self {
        Self {
            router: Mutex::new(router),
        }
    }

    pub(crate) async fn create_account(
        &self,
        context: &mut TestContext,
        request: &CreateAccountRequest,
    ) -> Response<CreateAccountResponse> {
        let response: Response<CreateAccountResponse> = Request::builder()
            .uri("/api/accounts")
            .post(request)
            .call(&self.router)
            .await;
        if let Some(r) = response.body.as_ref() {
            let account_id = r.account_id.clone();
            let pubkey = if let CreateAccountRequest::Full { .. } = request {
                request.auth_keys().0.unwrap()
            } else {
                request.auth_keys().2.unwrap()
            };
            context.associate_with_account(&account_id, pubkey);
        }
        response
    }

    pub(crate) async fn upgrade_account(
        &self,
        context: &mut TestContext,
        account_id: &str,
        request: &UpgradeAccountRequest,
    ) -> Response<CreateAccountResponse> {
        let response: Response<CreateAccountResponse> = Request::builder()
            .uri(format!("/api/accounts/{account_id}/upgrade"))
            .recovery_authenticated(&AccountId::from_str(account_id).unwrap())
            .post(request)
            .call(&self.router)
            .await;
        if let Some(r) = response.body.as_ref() {
            let account_id = r.account_id.clone();
            context.associate_with_account(&account_id, request.auth.app);
        }
        response
    }

    pub(crate) async fn create_keyset(
        &self,
        account_id: &str,
        request: &CreateKeysetRequest,
        keys: &TestAuthenticationKeys,
    ) -> Response<CreateKeysetResponse> {
        let account_id = AccountId::from_str(account_id).expect("Account id not valid");
        Request::builder()
            .uri(format!("/api/accounts/{account_id}/keysets"))
            .authenticated(
                &account_id,
                Some(keys.app.secret_key),
                Some(keys.hw.secret_key),
            )
            .post(request)
            .call(&self.router)
            .await
    }

    pub(crate) async fn add_device_token(
        &self,
        account_id: &str,
        request: &AccountAddDeviceTokenRequest,
    ) -> Response<AccountAddDeviceTokenResponse> {
        Request::builder()
            .uri(format!("/api/accounts/{account_id}/device-token"))
            .authenticated(
                &AccountId::from_str(account_id).expect("Account id not valid"),
                None,
                None,
            )
            .post(request)
            .call(&self.router)
            .await
    }

    pub(crate) async fn add_device_token_with_access_token(
        &self,
        account_id: &str,
        request: &AccountAddDeviceTokenRequest,
        access_token: &str,
    ) -> Response<AccountAddDeviceTokenResponse> {
        Request::builder()
            .uri(format!("/api/accounts/{account_id}/device-token"))
            .authenticated_with_access_token(access_token)
            .post(request)
            .call(&self.router)
            .await
    }

    pub(crate) async fn add_touchpoint(
        &self,
        account_id: &str,
        request: &AccountAddTouchpointRequest,
    ) -> Response<AccountAddTouchpointResponse> {
        Request::builder()
            .uri(format!("/api/accounts/{account_id}/touchpoints"))
            .authenticated(
                &AccountId::from_str(account_id).expect("Account id not valid"),
                None,
                None,
            )
            .post(request)
            .call(&self.router)
            .await
    }

    pub(crate) async fn verify_touchpoint(
        &self,
        account_id: &str,
        touchpoint_id: &str,
        request: &AccountVerifyTouchpointRequest,
    ) -> Response<AccountVerifyTouchpointResponse> {
        Request::builder()
            .uri(format!(
                "/api/accounts/{account_id}/touchpoints/{touchpoint_id}/verify"
            ))
            .authenticated(
                &AccountId::from_str(account_id).expect("Account id not valid"),
                None,
                None,
            )
            .post(request)
            .call(&self.router)
            .await
    }

    pub(crate) async fn activate_touchpoint(
        &self,
        account_id: &str,
        touchpoint_id: &str,
        request: &AccountActivateTouchpointRequest,
        app_signed: bool,
        hw_signed: bool,
        keys: &TestAuthenticationKeys,
    ) -> Response<AccountActivateTouchpointResponse> {
        Request::builder()
            .uri(format!(
                "/api/accounts/{account_id}/touchpoints/{touchpoint_id}/activate"
            ))
            .authenticated(
                &AccountId::from_str(account_id).expect("Account id not valid"),
                if app_signed {
                    Some(keys.app.secret_key)
                } else {
                    None
                },
                if hw_signed {
                    Some(keys.hw.secret_key)
                } else {
                    None
                },
            )
            .post(request)
            .call(&self.router)
            .await
    }

    pub(crate) async fn get_touchpoints(
        &self,
        account_id: &str,
    ) -> Response<AccountGetTouchpointsResponse> {
        Request::builder()
            .uri(format!("/api/accounts/{account_id}/touchpoints"))
            .authenticated(
                &AccountId::from_str(account_id).expect("Account id not valid"),
                None,
                None,
            )
            .get()
            .call(&self.router)
            .await
    }

    pub(crate) async fn put_mobile_pay(
        &self,
        account_id: &AccountId,
        request: &MobilePaySetupRequest,
        keys: &TestAuthenticationKeys,
    ) -> Response<MobilePaySetupResponse> {
        self.put_mobile_pay_with_keyproof_account_id(account_id, account_id, request, keys)
            .await
    }

    pub(crate) async fn put_mobile_pay_with_keyproof_account_id(
        &self,
        keyproof_account_id: &AccountId,
        account_id: &AccountId,
        request: &MobilePaySetupRequest,
        keyproof_account_keys: &TestAuthenticationKeys,
    ) -> Response<MobilePaySetupResponse> {
        Request::builder()
            .uri(format!("/api/accounts/{account_id}/mobile-pay"))
            .authenticated(
                keyproof_account_id,
                Some(keyproof_account_keys.app.secret_key),
                Some(keyproof_account_keys.hw.secret_key),
            )
            .put(request)
            .call(&self.router)
            .await
    }

    pub(crate) async fn get_mobile_pay(
        &self,
        account_id: &AccountId,
        keys: &TestAuthenticationKeys,
    ) -> Response<GetMobilePayResponse> {
        Request::builder()
            .uri(format!("/api/accounts/{account_id}/mobile-pay"))
            .authenticated(account_id, Some(keys.app.secret_key), None)
            .get()
            .call(&self.router)
            .await
    }

    pub(crate) async fn setup_mobile_pay_unauthenticated(
        &self,
        account_id: &AccountId,
        request: &MobilePaySetupRequest,
    ) -> Response<MobilePaySetupResponse> {
        Request::builder()
            .uri(format!("/api/accounts/{account_id}/mobile-pay"))
            .put(request)
            .call(&self.router)
            .await
    }

    pub(crate) async fn delete_mobile_pay(
        &self,
        account_id: &AccountId,
        keys: &TestAuthenticationKeys,
    ) -> Response<()> {
        Request::builder()
            .uri(format!("/api/accounts/{account_id}/mobile-pay"))
            .authenticated(account_id, Some(keys.app.secret_key), None)
            .delete()
            .call(&self.router)
            .await
    }

    pub(crate) async fn create_delay_notify_recovery(
        &self,
        account_id: &str,
        request: &CreateAccountDelayNotifyRequest,
        app_signed: bool,
        hw_signed: bool,
        keys: &TestAuthenticationKeys,
    ) -> Response<PendingRecoveryResponse> {
        Request::builder()
            .uri(format!("/api/accounts/{account_id}/delay-notify"))
            .authenticated(
                &AccountId::from_str(account_id).expect("Account id not valid"),
                if app_signed {
                    Some(keys.app.secret_key)
                } else {
                    None
                },
                if hw_signed {
                    Some(keys.hw.secret_key)
                } else {
                    None
                },
            )
            .post(request)
            .call(&self.router)
            .await
    }

    pub(crate) async fn update_delay_for_test_recovery(
        &self,
        account_id: &str,
        request: &UpdateDelayForTestRecoveryRequest,
        app_signed: bool,
        hw_signed: bool,
        keys: &TestAuthenticationKeys,
    ) -> Response<PendingRecoveryResponse> {
        Request::builder()
            .uri(format!("/api/accounts/{account_id}/delay-notify/test"))
            .authenticated(
                &AccountId::from_str(account_id).expect("Account id not valid"),
                if app_signed {
                    Some(keys.app.secret_key)
                } else {
                    None
                },
                if hw_signed {
                    Some(keys.hw.secret_key)
                } else {
                    None
                },
            )
            .put(request)
            .call(&self.router)
            .await
    }

    pub(crate) async fn get_recovery_status(&self, account_id: &str) -> Response<RecoveryResponse> {
        Request::builder()
            .uri(format!("/api/accounts/{account_id}/recovery"))
            .authenticated(
                &AccountId::from_str(account_id).expect("Account id not valid"),
                None,
                None,
            )
            .get()
            .call(&self.router)
            .await
    }

    pub(crate) async fn cancel_delay_notify_recovery(
        &self,
        account_id: &str,
        app_signed: bool,
        hw_signed: bool,
        keys: &TestAuthenticationKeys,
    ) -> Response<()> {
        Request::builder()
            .uri(format!("/api/accounts/{account_id}/delay-notify"))
            .authenticated(
                &AccountId::from_str(account_id).expect("Account id not valid"),
                if app_signed {
                    Some(keys.app.secret_key)
                } else {
                    None
                },
                if hw_signed {
                    Some(keys.hw.secret_key)
                } else {
                    None
                },
            )
            .delete()
            .call(&self.router)
            .await
    }

    pub(crate) async fn complete_delay_notify_recovery(
        &self,
        account_id: &str,
        request: &CompleteDelayNotifyRequest,
    ) -> Response<RotatedKeysetResponse> {
        Request::builder()
            .uri(format!("/api/accounts/{account_id}/delay-notify/complete"))
            .post(&request)
            .call(&self.router)
            .await
    }

    pub(crate) async fn send_delay_notify_verification_code(
        &self,
        account_id: &str,
        request: &SendAccountVerificationCodeRequest,
        app_signed: bool,
        hw_signed: bool,
        keys: &TestAuthenticationKeys,
    ) -> Response<SendAccountVerificationCodeResponse> {
        Request::builder()
            .uri(format!(
                "/api/accounts/{account_id}/delay-notify/send-verification-code"
            ))
            .authenticated(
                &AccountId::from_str(account_id).unwrap(),
                if app_signed {
                    Some(keys.app.secret_key)
                } else {
                    None
                },
                if hw_signed {
                    Some(keys.hw.secret_key)
                } else {
                    None
                },
            )
            .post(&request)
            .call(&self.router)
            .await
    }

    pub(crate) async fn verify_delay_notify_verification_code(
        &self,
        account_id: &str,
        request: &VerifyAccountVerificationCodeRequest,
        app_signed: bool,
        hw_signed: bool,
        keys: &TestAuthenticationKeys,
    ) -> Response<VerifyAccountVerificationCodeResponse> {
        Request::builder()
            .uri(format!(
                "/api/accounts/{account_id}/delay-notify/verify-code"
            ))
            .authenticated(
                &AccountId::from_str(account_id).unwrap(),
                if app_signed {
                    Some(keys.app.secret_key)
                } else {
                    None
                },
                if hw_signed {
                    Some(keys.hw.secret_key)
                } else {
                    None
                },
            )
            .post(&request)
            .call(&self.router)
            .await
    }

    pub(crate) async fn get_account_keysets(
        &self,
        account_id: &str,
    ) -> Response<GetAccountKeysetsResponse> {
        Request::builder()
            .uri(format!("/api/accounts/{account_id}/keysets"))
            .authenticated(&AccountId::from_str(account_id).unwrap(), None, None)
            .get()
            .call(&self.router)
            .await
    }

    pub(crate) async fn rotate_authentication_keys(
        &self,
        context: &mut TestContext,
        account_id: &str,
        request: &RotateAuthenticationKeysRequest,
        keys: &TestAuthenticationKeys,
    ) -> Response<RotateAuthenticationKeysResponse> {
        let account_id = AccountId::from_str(account_id).expect("Account id not valid");
        let response = self
            .rotate_authentication_keys_with_keyproof_account_id(
                context,
                &account_id,
                &account_id,
                request,
                keys,
            )
            .await;
        if response.body.is_some() {
            context.associate_with_account(&account_id, request.application.key);
        }
        response
    }

    pub(crate) async fn rotate_authentication_keys_with_keyproof_account_id(
        &self,
        context: &mut TestContext,
        keyproof_account_id: &AccountId,
        account_id: &AccountId,
        request: &RotateAuthenticationKeysRequest,
        keys: &TestAuthenticationKeys,
    ) -> Response<RotateAuthenticationKeysResponse> {
        let response = Request::builder()
            .method("POST")
            .uri(format!("/api/accounts/{account_id}/authentication-keys"))
            .authenticated(
                keyproof_account_id,
                Some(keys.app.secret_key),
                Some(keys.hw.secret_key),
            )
            .post(&request)
            .call(&self.router)
            .await;
        if response.body.is_some() {
            context.associate_with_account(account_id, request.application.key);
        }
        response
    }

    pub(crate) async fn rotate_to_spending_keyset(
        &self,
        account_id: &str,
        keyset_id: &str,
        request: &RotateSpendingKeysetRequest,
        keys: &TestAuthenticationKeys,
    ) -> Response<GetAccountKeysetsResponse> {
        Request::builder()
            .uri(format!("/api/accounts/{account_id}/keysets/{keyset_id}"))
            .authenticated(
                &AccountId::from_str(account_id).unwrap(),
                Some(keys.app.secret_key),
                Some(keys.hw.secret_key),
            )
            .put(&request)
            .call(&self.router)
            .await
    }

    pub(crate) async fn get_account_status(
        &self,
        account_id: &str,
    ) -> Response<GetAccountStatusResponse> {
        Request::builder()
            .uri(format!("/api/accounts/{account_id}"))
            .authenticated(&AccountId::from_str(account_id).unwrap(), None, None)
            .get()
            .call(&self.router)
            .await
    }

    pub(crate) async fn sign_transaction_with_active_keyset(
        &self,
        account_id: &AccountId,
        request: &SignTransactionData,
    ) -> Response<SignTransactionResponse> {
        Request::builder()
            .uri(format!("/api/accounts/{account_id}/sign-transaction"))
            .authenticated(account_id, None, None)
            .post(&request)
            .call(&self.router)
            .await
    }

    pub(crate) async fn sign_transaction_with_keyset(
        &self,
        account_id: &AccountId,
        keyset_id: &KeysetId,
        request: &SignTransactionData,
    ) -> Response<SignTransactionResponse> {
        self.sign_transaction_with_keyset_and_keyproof_account_id(
            account_id, account_id, keyset_id, request,
        )
        .await
    }

    pub(crate) async fn sign_transaction_with_keyset_and_keyproof_account_id(
        &self,
        account_id: &AccountId,
        keyproof_account_id: &AccountId,
        keyset_id: &KeysetId,
        request: &SignTransactionData,
    ) -> Response<SignTransactionResponse> {
        Request::builder()
            .uri(format!(
                "/api/accounts/{account_id}/keysets/{keyset_id}/sign-transaction"
            ))
            .authenticated(keyproof_account_id, None, None)
            .post(&request)
            .call(&self.router)
            .await
    }

    pub(crate) async fn send_test_push(
        &self,
        account_id: &str,
        request: &SendTestPushData,
    ) -> Response<SendTestPushResponse> {
        Request::builder()
            .uri(format!("/api/accounts/{account_id}/notifications/test"))
            .authenticated(&AccountId::from_str(account_id).unwrap(), None, None)
            .post(&request)
            .call(&self.router)
            .await
    }

    pub(crate) async fn authenticate_with_recovery_authkey(
        &self,
        request: &AuthenticateWithRecoveryAuthkeyRequest,
    ) -> Response<AuthenticateWithRecoveryResponse> {
        Request::builder()
            .uri("/api/recovery-auth")
            .post(&request)
            .call(&self.router)
            .await
    }

    pub(crate) async fn authenticate_with_hardware(
        &self,
        request: &AuthenticateWithHardwareRequest,
    ) -> Response<AuthenticateWithHardwareResponse> {
        Request::builder()
            .uri("/api/hw-auth")
            .post(&request)
            .call(&self.router)
            .await
    }

    pub(crate) async fn authenticate(
        &self,
        request: &AuthenticationRequest,
    ) -> Response<AuthenticationResponse> {
        Request::builder()
            .uri("/api/authenticate")
            .post(&request)
            .call(&self.router)
            .await
    }

    pub(crate) async fn get_tokens(
        &self,
        request: &GetTokensRequest,
    ) -> Response<GetTokensResponse> {
        Request::builder()
            .uri("/api/authenticate/tokens")
            .post(&request)
            .call(&self.router)
            .await
    }

    pub(crate) async fn track_analytics_event(&self, request: Vec<u8>) -> Response<()> {
        let req = http::request::Builder::new()
            .method("POST")
            .uri("/api/analytics/events")
            .body(Body::from(request))
            .unwrap();
        req.call(&self.router).await
    }

    pub(crate) async fn complete_onboarding(
        &self,
        account_id: &str,
        request: &CompleteOnboardingRequest,
    ) -> Response<CompleteOnboardingResponse> {
        Request::builder()
            .uri(format!("/api/accounts/{account_id}/complete-onboarding"))
            .authenticated(&AccountId::from_str(account_id).unwrap(), None, None)
            .post(request)
            .call(&self.router)
            .await
    }

    pub(crate) async fn register_watch_address(
        &self,
        account_id: &AccountId,
        request: &RegisterWatchAddressRequest,
    ) -> Response<RegisterWatchAddressResponse> {
        Request::builder()
            .uri(format!(
                "/api/accounts/{account_id}/notifications/addresses"
            ))
            .authenticated(account_id, None, None)
            .post(&request)
            .call(&self.router)
            .await
    }

    pub(crate) async fn register_watch_address_unauth(
        &self,
        account_id: &AccountId,
        request: &RegisterWatchAddressRequest,
    ) -> Response<RegisterWatchAddressResponse> {
        Request::builder()
            .uri(format!(
                "/api/accounts/{account_id}/notifications/addresses"
            ))
            .post(&request)
            .call(&self.router)
            .await
    }

    pub(crate) async fn get_bdk_configuration(&self) -> Response<BdkConfigResponse> {
        Request::builder()
            .uri("/api/bdk-configuration")
            .get()
            .call(&self.router)
            .await
    }

    pub(crate) async fn twilio_status_callback(
        &self,
        body: &HashMap<String, String>,
        signature: Option<String>,
    ) -> axum::response::Response {
        let mut builder = Request::builder().uri("/api/twilio/status-callback");

        if let Some(signature) = signature {
            builder = builder.header("X-Twilio-Signature", signature);
        }

        let req = builder.post_form(body);

        self.router.lock().await.call(req).await.unwrap()
    }

    pub(crate) async fn create_recovery_relationship(
        &self,
        account_id: &str,
        request: &CreateRecoveryRelationshipRequest,
        auth: &CognitoAuthentication,
        keys: &TestAuthenticationKeys,
    ) -> Response<CreateRecoveryRelationshipResponse> {
        Request::builder()
            .uri(format!("/api/accounts/{account_id}/recovery/relationships"))
            .with_authentication(
                auth,
                &AccountId::from_str(account_id).unwrap(),
                (
                    keys.app.secret_key,
                    keys.hw.secret_key,
                    keys.recovery.secret_key,
                ),
            )
            .post(&request)
            .call(&self.router)
            .await
    }

    pub(crate) async fn update_recovery_relationship(
        &self,
        account_id: &str,
        recovery_relationship_id: &str,
        request: &UpdateRecoveryRelationshipRequest,
        auth: &CognitoAuthentication,
        keys: &TestAuthenticationKeys,
    ) -> Response<UpdateRecoveryRelationshipResponse> {
        Request::builder()
            .uri(format!(
                "/api/accounts/{account_id}/recovery/relationships/{recovery_relationship_id}"
            ))
            .with_authentication(
                auth,
                &AccountId::from_str(account_id).unwrap(),
                (
                    keys.app.secret_key,
                    keys.hw.secret_key,
                    keys.recovery.secret_key,
                ),
            )
            .put(&request)
            .call(&self.router)
            .await
    }

    pub(crate) async fn endorse_recovery_relationship(
        &self,
        account_id: &str,
        request: &EndorseRecoveryRelationshipsRequest,
        keys: &TestAuthenticationKeys,
    ) -> Response<EndorseRecoveryRelationshipsResponse> {
        Request::builder()
            .uri(format!("/api/accounts/{account_id}/recovery/relationships"))
            .with_authentication(
                &CognitoAuthentication::Wallet {
                    is_app_signed: false,
                    is_hardware_signed: false,
                },
                &AccountId::from_str(account_id).unwrap(),
                (
                    keys.app.secret_key,
                    keys.hw.secret_key,
                    keys.recovery.secret_key,
                ),
            )
            .put(&request)
            .call(&self.router)
            .await
    }

    pub(crate) async fn delete_recovery_relationship(
        &self,
        account_id: &str,
        recovery_relationship_id: &str,
        auth: &CognitoAuthentication,
        keys: &TestAuthenticationKeys,
    ) -> Response<()> {
        Request::builder()
            .uri(format!(
                "/api/accounts/{account_id}/recovery/relationships/{recovery_relationship_id}"
            ))
            .with_authentication(
                auth,
                &AccountId::from_str(account_id).unwrap(),
                (
                    keys.app.secret_key,
                    keys.hw.secret_key,
                    keys.recovery.secret_key,
                ),
            )
            .delete()
            .call(&self.router)
            .await
    }

    pub(crate) async fn get_recovery_relationships(
        &self,
        account_id: &str,
    ) -> Response<GetRecoveryRelationshipsResponse> {
        Request::builder()
            .uri(format!("/api/accounts/{account_id}/recovery/relationships"))
            .authenticated(&AccountId::from_str(account_id).unwrap(), None, None)
            .get()
            .call(&self.router)
            .await
    }

    pub(crate) async fn get_recovery_relationship_invitation_for_code(
        &self,
        account_id: &str,
        code: &str,
    ) -> Response<GetRecoveryRelationshipInvitationForCodeResponse> {
        Request::builder()
            .uri(format!(
                "/api/accounts/{account_id}/recovery/relationship-invitations/{code}"
            ))
            .authenticated(&AccountId::from_str(account_id).unwrap(), None, None)
            .get()
            .call(&self.router)
            .await
    }

    pub(crate) async fn get_supported_fiat_currencies(
        &self,
    ) -> Response<SupportedFiatCurrenciesResponse> {
        Request::builder()
            .uri("/api/exchange-rates/currencies".to_string())
            .get()
            .call(&self.router)
            .await
    }

    pub(crate) async fn start_social_challenge(
        &self,
        account_id: &str,
        request: &StartSocialChallengeRequest,
    ) -> Response<StartSocialChallengeResponse> {
        Request::builder()
            .uri(format!(
                "/api/accounts/{account_id}/recovery/social-challenges"
            ))
            .recovery_authenticated(&AccountId::from_str(account_id).unwrap())
            .post(&request)
            .call(&self.router)
            .await
    }

    pub(crate) async fn verify_social_challenge(
        &self,
        account_id: &str,
        request: &VerifySocialChallengeRequest,
    ) -> Response<VerifySocialChallengeResponse> {
        Request::builder()
            .uri(format!(
                "/api/accounts/{account_id}/recovery/verify-social-challenge"
            ))
            .recovery_authenticated(&AccountId::from_str(account_id).unwrap())
            .post(&request)
            .call(&self.router)
            .await
    }

    pub(crate) async fn respond_to_social_challenge(
        &self,
        account_id: &str,
        social_challenge_id: &str,
        request: &RespondToSocialChallengeRequest,
    ) -> Response<RespondToSocialChallengeResponse> {
        Request::builder()
            .uri(format!(
                "/api/accounts/{account_id}/recovery/social-challenges/{social_challenge_id}"
            ))
            .recovery_authenticated(&AccountId::from_str(account_id).unwrap())
            .put(&request)
            .call(&self.router)
            .await
    }

    pub(crate) async fn fetch_social_challenge(
        &self,
        account_id: &str,
        social_challenge_id: &str,
    ) -> Response<FetchSocialChallengeResponse> {
        Request::builder()
            .uri(format!(
                "/api/accounts/{account_id}/recovery/social-challenges/{social_challenge_id}"
            ))
            .recovery_authenticated(&AccountId::from_str(account_id).unwrap())
            .get()
            .call(&self.router)
            .await
    }

    pub(crate) async fn delete_account(
        &self,
        account_id: &str,
        auth: &CognitoAuthentication,
        keys: &TestAuthenticationKeys,
    ) -> Response<()> {
        Request::builder()
            .uri(format!("/api/accounts/{account_id}"))
            .with_authentication(
                auth,
                &AccountId::from_str(account_id).unwrap(),
                (
                    keys.app.secret_key,
                    keys.hw.secret_key,
                    keys.recovery.secret_key,
                ),
            )
            .delete()
            .call(&self.router)
            .await
    }

    pub(crate) async fn set_notifications_preferences(
        &self,
        account_id: &str,
        request: &NotificationsPreferences,
        app_signed: bool,
        hw_signed: bool,
        keys: &TestAuthenticationKeys,
    ) -> Response<NotificationsPreferences> {
        Request::builder()
            .uri(format!(
                "/api/accounts/{account_id}/notifications-preferences"
            ))
            .authenticated(
                &AccountId::from_str(account_id).unwrap(),
                if app_signed {
                    Some(keys.app.secret_key)
                } else {
                    None
                },
                if hw_signed {
                    Some(keys.hw.secret_key)
                } else {
                    None
                },
            )
            .put(request)
            .call(&self.router)
            .await
    }

    pub(crate) async fn get_notifications_preferences(
        &self,
        account_id: &str,
    ) -> Response<NotificationsPreferences> {
        Request::builder()
            .uri(format!(
                "/api/accounts/{account_id}/notifications-preferences"
            ))
            .authenticated(&AccountId::from_str(account_id).unwrap(), None, None)
            .get()
            .call(&self.router)
            .await
    }

    pub(crate) async fn get_account_feature_flags(
        &self,
        account_id: &str,
        request: &GetAccountFeatureFlagsRequest,
    ) -> Response<GetFeatureFlagsResponse> {
        Request::builder()
            .uri(format!("/api/accounts/{account_id}/feature-flags"))
            .authenticated(&AccountId::from_str(account_id).unwrap(), None, None)
            .post(request)
            .call(&self.router)
            .await
    }

    pub(crate) async fn get_app_installation_feature_flags(
        &self,
        request: &GetAppInstallationFeatureFlagsRequest,
    ) -> Response<GetFeatureFlagsResponse> {
        Request::builder()
            .uri("/api/feature-flags".to_string())
            .post(request)
            .call(&self.router)
            .await
    }
}
