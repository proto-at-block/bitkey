use core::str;
use std::collections::HashMap;
use std::str::FromStr;

use async_trait::async_trait;
use authn_authz::routes::{
    AuthenticateWithHardwareRequest, AuthenticateWithHardwareResponse, AuthenticationRequest,
    AuthenticationResponse, GetTokensRequest, GetTokensResponse, NoiseInitiateBundleRequest,
    NoiseInitiateBundleResponse,
};
use axum::body::Body;
use axum::Router;
use exchange_rate::routes::SupportedFiatCurrenciesResponse;
use experimentation::routes::{
    GetAccountFeatureFlagsRequest, GetAppInstallationFeatureFlagsRequest, GetFeatureFlagsResponse,
};
use export_tools::routes::GetAccountDescriptorResponse;
use hmac::{Hmac, Mac};
use http::HeaderMap;
use http::{header::CONTENT_TYPE, HeaderValue, Method, Request};
use http_body_util::BodyExt as ExternalBodyExt;
use linear::routes::WebhookResponse;
use mobile_pay::routes::{
    GetMobilePayResponse, MobilePaySetupRequest, MobilePaySetupResponse, SignTransactionData,
    SignTransactionResponse,
};
use notification::routes::{
    RegisterWatchAddressRequest, RegisterWatchAddressResponse, SendTestPushData,
    SendTestPushResponse, SetNotificationsTriggersRequest, SetNotificationsTriggersResponse,
};
use onboarding::routes::{
    AccountActivateTouchpointRequest, AccountActivateTouchpointResponse,
    AccountAddDeviceTokenRequest, AccountAddDeviceTokenResponse, AccountAddTouchpointRequest,
    AccountAddTouchpointResponse, AccountVerifyTouchpointRequest, AccountVerifyTouchpointResponse,
    ActivateSpendingKeyDefinitionRequest, ActivateSpendingKeyDefinitionResponse, BdkConfigResponse,
    CompleteOnboardingRequest, CompleteOnboardingResponse, ContinueDistributedKeygenRequest,
    ContinueDistributedKeygenResponse, CreateAccountRequest, CreateAccountResponse,
    CreateKeysetRequest, CreateKeysetResponse, GetAccountKeysetsResponse, GetAccountStatusResponse,
    InititateDistributedKeygenRequest, InititateDistributedKeygenResponse,
    RotateSpendingKeysetRequest, UpdateDescriptorBackupsRequest, UpdateDescriptorBackupsResponse,
    UpgradeAccountRequest,
};
use privileged_action::routes::{
    CancelPendingDelayAndNotifyInstanceByTokenRequest,
    CancelPendingDelayAndNotifyInstanceByTokenResponse,
    ConfigurePrivilegedActionDelayDurationsRequest,
    ConfigurePrivilegedActionDelayDurationsResponse, GetPendingDelayAndNotifyInstancesResponse,
    GetPrivilegedActionDefinitionsResponse,
};
use recovery::routes::delay_notify::{
    CompleteDelayNotifyRequest, CreateAccountDelayNotifyRequest, EvaluatePinRequest,
    EvaluatePinResponse, RotateAuthenticationKeysRequest, RotateAuthenticationKeysResponse,
    SendAccountVerificationCodeRequest, SendAccountVerificationCodeResponse,
    UpdateDelayForTestRecoveryRequest, VerifyAccountVerificationCodeRequest,
    VerifyAccountVerificationCodeResponse,
};
use recovery::routes::distributed_keys::{
    CreateSelfSovereignBackupRequest, CreateSelfSovereignBackupResponse,
};
use recovery::routes::inheritance::{
    CancelInheritanceClaimRequest, CancelInheritanceClaimResponse, CreateInheritanceClaimRequest,
    CreateInheritanceClaimResponse, GetInheritanceClaimsResponse, UploadInheritancePackagesRequest,
    UploadInheritancePackagesResponse,
};
use recovery::routes::relationship::{
    CreateRecoveryRelationshipRequest, CreateRelationshipRequest, CreateRelationshipResponse,
    EndorseRecoveryRelationshipsRequest, EndorseRecoveryRelationshipsResponse,
    GetRecoveryBackupResponse, GetRecoveryRelationshipInvitationForCodeResponse,
    GetRecoveryRelationshipsResponse, UpdateRecoveryRelationshipRequest,
    UpdateRecoveryRelationshipResponse, UploadRecoveryBackupRequest, UploadRecoveryBackupResponse,
};
use recovery::routes::reset_fingerprint::{ResetFingerprintRequest, ResetFingerprintResponse};
use recovery::routes::social_challenge::{
    FetchSocialChallengeResponse, RespondToSocialChallengeRequest,
    RespondToSocialChallengeResponse, StartSocialChallengeRequest, StartSocialChallengeResponse,
    VerifySocialChallengeRequest, VerifySocialChallengeResponse,
};
use recovery::state_machine::pending_recovery::PendingRecoveryResponse;
use recovery::state_machine::rotated_keyset::RotatedKeysetResponse;
use recovery::state_machine::RecoveryResponse;
use serde::{de::DeserializeOwned, Serialize};
use sha2::Sha256;
use tokio::sync::Mutex;
use tower::Service;
use transaction_verification::routes::{
    GetTransactionVerificationPolicyResponse, InitiateTransactionVerificationRequest,
    PutTransactionVerificationPolicyRequest, PutTransactionVerificationPolicyResponse,
};
use types::account::identifiers::{AccountId, KeysetId};
use types::notification::NotificationsPreferences;
use types::privileged_action::router::generic::{
    PrivilegedActionRequest, PrivilegedActionResponse,
};
use types::recovery::trusted_contacts::TrustedContactRole;
use types::transaction_verification::router::InitiateTransactionVerificationView;

use super::{AuthenticatedRequestExt, CognitoAuthentication, Response};
use crate::test_utils::{AuthenticatedRequest, ExtendRequest};
use crate::tests::{TestAuthenticationKeys, TestContext};

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

trait HttpBodyExt {
    fn json_request(self, body: Body, method: Method) -> Request<Body>;
}

impl HttpBodyExt for http::request::Builder {
    fn json_request(self, body: Body, method: Method) -> Request<Body> {
        self.method(method)
            .header("Content-Type", "application/json")
            .body(body)
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
            let pubkey = match request {
                CreateAccountRequest::Full { auth, .. } => auth.app,
                CreateAccountRequest::Lite { auth, .. } => auth.recovery,
                CreateAccountRequest::Software { auth, .. } => auth.app,
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
        request: &PrivilegedActionRequest<AccountActivateTouchpointRequest>,
        app_signed: bool,
        hw_signed: bool,
        keys: &TestAuthenticationKeys,
    ) -> Response<PrivilegedActionResponse<AccountActivateTouchpointResponse>> {
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

    pub(crate) async fn get_transaction_verification_policy(
        &self,
        account_id: &AccountId,
        keys: &TestAuthenticationKeys,
    ) -> Response<GetTransactionVerificationPolicyResponse> {
        Request::builder()
            .uri(format!("/api/accounts/{account_id}/tx-verify/policy"))
            .authenticated(account_id, Some(keys.app.secret_key), None)
            .get()
            .call(&self.router)
            .await
    }

    pub(crate) async fn update_transaction_verification_policy(
        &self,
        account_id: &AccountId,
        app_signed: bool,
        hw_signed: bool,
        keys: &TestAuthenticationKeys,
        request: &PutTransactionVerificationPolicyRequest,
    ) -> Response<PutTransactionVerificationPolicyResponse> {
        Request::builder()
            .uri(format!("/api/accounts/{account_id}/tx-verify/policy"))
            .authenticated(
                account_id,
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

    pub(crate) async fn initiate_transaction_verification(
        &self,
        account_id: &AccountId,
        keys: &TestAuthenticationKeys,
        request: &InitiateTransactionVerificationRequest,
    ) -> Response<InitiateTransactionVerificationView> {
        Request::builder()
            .uri(format!("/api/accounts/{account_id}/tx-verify/requests"))
            .authenticated(account_id, Some(keys.app.secret_key), None)
            .post(request)
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

    pub(crate) async fn get_bdk_configuration(
        &self,
        headers: HeaderMap,
    ) -> Response<BdkConfigResponse> {
        Request::builder()
            .uri("/api/bdk-configuration")
            .with_headers(headers)
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

    #[deprecated]
    pub(crate) async fn create_recovery_relationship(
        &self,
        account_id: &str,
        request: &CreateRecoveryRelationshipRequest,
        auth: &CognitoAuthentication,
        keys: &TestAuthenticationKeys,
    ) -> Response<CreateRelationshipResponse> {
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

    pub(crate) async fn create_relationship(
        &self,
        account_id: &str,
        request: &CreateRelationshipRequest,
        auth: &CognitoAuthentication,
        keys: &TestAuthenticationKeys,
    ) -> Response<CreateRelationshipResponse> {
        Request::builder()
            .uri(format!("/api/accounts/{account_id}/relationships"))
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

    pub(crate) async fn make_request_with_auth<T>(
        &self,
        uri: &str,
        account_id: &str,
        method: &Method,
        request_body: Body,
        auth: &CognitoAuthentication,
        keys: &TestAuthenticationKeys,
    ) -> Response<T>
    where
        T: DeserializeOwned + Serialize + Sync + Send,
    {
        Request::builder()
            .uri(uri)
            .with_authentication(
                auth,
                &AccountId::from_str(account_id).unwrap(),
                (
                    keys.app.secret_key,
                    keys.hw.secret_key,
                    keys.recovery.secret_key,
                ),
            )
            .json_request(request_body, method.to_owned())
            .call(&self.router)
            .await
    }

    pub(crate) async fn make_request<T>(
        &self,
        uri: &str,
        method: &Method,
        request_body: Body,
    ) -> Response<T>
    where
        T: DeserializeOwned + Serialize + Sync + Send,
    {
        Request::builder()
            .uri(uri)
            .json_request(request_body, method.to_owned())
            .call(&self.router)
            .await
    }

    pub(crate) async fn make_request_with_headers<T>(
        &self,
        uri: &str,
        method: &Method,
        headers: HeaderMap,
        request_body: Body,
    ) -> Response<T>
    where
        T: DeserializeOwned + Serialize + Sync + Send,
    {
        Request::builder()
            .uri(uri)
            .with_headers(headers)
            .json_request(request_body, method.to_owned())
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

    pub(crate) async fn get_relationships(
        &self,
        account_id: &str,
        trusted_contact_role: Option<&TrustedContactRole>,
    ) -> Response<GetRecoveryRelationshipsResponse> {
        let query = match trusted_contact_role {
            Some(role) => format!("trusted_contact_role={}", role),
            None => "".to_string(),
        };
        Request::builder()
            .uri(format!("/api/accounts/{account_id}/relationships?{query}"))
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

    pub(crate) async fn get_full_account_feature_flags(
        &self,
        account_id: &str,
        headers: HeaderMap,
        request: &GetAccountFeatureFlagsRequest,
    ) -> Response<GetFeatureFlagsResponse> {
        Request::builder()
            .uri(format!("/api/accounts/{account_id}/feature-flags"))
            .with_headers(headers)
            .authenticated(&AccountId::from_str(account_id).unwrap(), None, None)
            .post(request)
            .call(&self.router)
            .await
    }

    pub(crate) async fn get_lite_account_feature_flags(
        &self,
        account_id: &str,
        headers: HeaderMap,
        request: &GetAccountFeatureFlagsRequest,
    ) -> Response<GetFeatureFlagsResponse> {
        Request::builder()
            .uri(format!("/api/accounts/{account_id}/feature-flags"))
            .with_headers(headers)
            .recovery_authenticated(&AccountId::from_str(account_id).unwrap())
            .post(request)
            .call(&self.router)
            .await
    }

    pub(crate) async fn get_app_installation_feature_flags(
        &self,
        headers: HeaderMap,
        request: &GetAppInstallationFeatureFlagsRequest,
    ) -> Response<GetFeatureFlagsResponse> {
        Request::builder()
            .uri("/api/feature-flags".to_string())
            .with_headers(headers)
            .post(request)
            .call(&self.router)
            .await
    }

    pub(crate) async fn initiate_distributed_keygen(
        &self,
        account_id: &str,
        request: &InititateDistributedKeygenRequest,
    ) -> Response<InititateDistributedKeygenResponse> {
        Request::builder()
            .uri(format!("/api/accounts/{account_id}/distributed-keygen"))
            .authenticated(&AccountId::from_str(account_id).unwrap(), None, None)
            .post(request)
            .call(&self.router)
            .await
    }

    pub(crate) async fn continue_distributed_keygen(
        &self,
        account_id: &str,
        key_definition_id: &str,
        request: &ContinueDistributedKeygenRequest,
    ) -> Response<ContinueDistributedKeygenResponse> {
        Request::builder()
            .uri(format!(
                "/api/accounts/{account_id}/distributed-keygen/{key_definition_id}"
            ))
            .authenticated(&AccountId::from_str(account_id).unwrap(), None, None)
            .put(request)
            .call(&self.router)
            .await
    }

    pub(crate) async fn activate_spending_key_definition(
        &self,
        account_id: &str,
        request: &ActivateSpendingKeyDefinitionRequest,
    ) -> Response<ActivateSpendingKeyDefinitionResponse> {
        Request::builder()
            .uri(format!(
                "/api/accounts/{account_id}/spending-key-definition"
            ))
            .authenticated(&AccountId::from_str(account_id).unwrap(), None, None)
            .put(request)
            .call(&self.router)
            .await
    }

    pub(crate) async fn create_self_sovereign_backup(
        &self,
        account_id: &str,
        request: &CreateSelfSovereignBackupRequest,
    ) -> Response<CreateSelfSovereignBackupResponse> {
        Request::builder()
            .uri(format!("/api/accounts/{account_id}/self-sovereign-backup"))
            .authenticated(&AccountId::from_str(account_id).unwrap(), None, None)
            .post(request)
            .call(&self.router)
            .await
    }

    pub(crate) async fn configure_privileged_action_delay_durations(
        &self,
        account_id: &str,
        request: &PrivilegedActionRequest<ConfigurePrivilegedActionDelayDurationsRequest>,
        auth: &CognitoAuthentication,
        keys: &TestAuthenticationKeys,
    ) -> Response<PrivilegedActionResponse<ConfigurePrivilegedActionDelayDurationsResponse>> {
        Request::builder()
            .uri(format!(
                "/api/accounts/{account_id}/privileged-actions/delays"
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
            .put(request)
            .call(&self.router)
            .await
    }

    pub(crate) async fn get_privileged_action_definitions(
        &self,
        account_id: &str,
        auth: &CognitoAuthentication,
        keys: &TestAuthenticationKeys,
    ) -> Response<GetPrivilegedActionDefinitionsResponse> {
        Request::builder()
            .uri(format!(
                "/api/accounts/{account_id}/privileged-actions/definitions"
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
            .get()
            .call(&self.router)
            .await
    }

    pub(crate) async fn get_pending_delay_and_notify_instances(
        &self,
        account_id: &str,
        auth: &CognitoAuthentication,
        keys: &TestAuthenticationKeys,
    ) -> Response<GetPendingDelayAndNotifyInstancesResponse> {
        Request::builder()
            .uri(format!(
                "/api/accounts/{account_id}/privileged-actions/instances"
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
            .get()
            .call(&self.router)
            .await
    }

    pub(crate) async fn cancel_pending_delay_and_notify_instance_by_token(
        &self,
        request: &CancelPendingDelayAndNotifyInstanceByTokenRequest,
    ) -> Response<CancelPendingDelayAndNotifyInstanceByTokenResponse> {
        Request::builder()
            .uri("/api/privileged-actions/cancel".to_string())
            .post(request)
            .call(&self.router)
            .await
    }

    pub(crate) async fn get_account_descriptors(
        &self,
        account_id: &str,
    ) -> Response<GetAccountDescriptorResponse> {
        Request::builder()
            .uri(format!("/api/accounts/{account_id}/descriptors"))
            .authenticated(&AccountId::from_str(account_id).unwrap(), None, None)
            .get()
            .call(&self.router)
            .await
    }

    pub(crate) async fn start_inheritance_claim(
        &self,
        account_id: &str,
        request: &CreateInheritanceClaimRequest,
        keys: &TestAuthenticationKeys,
    ) -> Response<CreateInheritanceClaimResponse> {
        Request::builder()
            .uri(format!(
                "/api/accounts/{account_id}/recovery/inheritance/claims"
            ))
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
            .post(&request)
            .call(&self.router)
            .await
    }

    pub(crate) async fn package_upload(
        &self,
        account_id: &str,
        request: &UploadInheritancePackagesRequest,
        keys: &TestAuthenticationKeys,
    ) -> Response<UploadInheritancePackagesResponse> {
        Request::builder()
            .uri(format!(
                "/api/accounts/{account_id}/recovery/inheritance/packages"
            ))
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
            .post(&request)
            .call(&self.router)
            .await
    }

    pub(crate) async fn cancel_inheritance_claim(
        &self,
        account_id: &str,
        inheritance_claim_id: &str,
        request: &CancelInheritanceClaimRequest,
        keys_account_id: &str,
        keys: &TestAuthenticationKeys,
    ) -> Response<CancelInheritanceClaimResponse> {
        Request::builder()
            .uri(format!(
                "/api/accounts/{account_id}/recovery/inheritance/claims/{inheritance_claim_id}/cancel"
            ))
            .with_authentication(
                &CognitoAuthentication::Wallet {
                    is_app_signed: false,
                    is_hardware_signed: false,
                },
                &AccountId::from_str(keys_account_id).unwrap(),
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

    pub(crate) async fn get_inheritance_claims(
        &self,
        account_id: &str,
        keys: &TestAuthenticationKeys,
    ) -> Response<GetInheritanceClaimsResponse> {
        Request::builder()
            .uri(format!(
                "/api/accounts/{account_id}/recovery/inheritance/claims"
            ))
            .with_authentication(
                &CognitoAuthentication::Recovery,
                &AccountId::from_str(account_id).unwrap(),
                (
                    keys.app.secret_key,
                    keys.hw.secret_key,
                    keys.recovery.secret_key,
                ),
            )
            .get()
            .call(&self.router)
            .await
    }

    pub(crate) async fn recovery_backup_upload(
        &self,
        account_id: &str,
        request: &UploadRecoveryBackupRequest,
        keys: &TestAuthenticationKeys,
    ) -> Response<UploadRecoveryBackupResponse> {
        Request::builder()
            .uri(format!("/api/accounts/{account_id}/recovery/backup"))
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
            .post(&request)
            .call(&self.router)
            .await
    }

    pub(crate) async fn fetch_recovery_backup(
        &self,
        account_id: &str,
        keys: &TestAuthenticationKeys,
    ) -> Response<GetRecoveryBackupResponse> {
        Request::builder()
            .uri(format!("/api/accounts/{account_id}/recovery/backup"))
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
            .get()
            .call(&self.router)
            .await
    }

    pub(crate) async fn initate_noise_secure_channel(
        &self,
        request: &NoiseInitiateBundleRequest,
    ) -> Response<NoiseInitiateBundleResponse> {
        Request::builder()
            .uri("/api/secure-channel/initiate".to_string())
            .post(request)
            .call(&self.router)
            .await
    }

    pub(crate) async fn evaluate_pin(
        &self,
        account_id: &str,
        request: &EvaluatePinRequest,
    ) -> Response<EvaluatePinResponse> {
        Request::builder()
            .uri(format!("/api/accounts/{account_id}/recovery/evaluate-pin"))
            .recovery_authenticated(&AccountId::from_str(account_id).unwrap())
            .post(&request)
            .call(&self.router)
            .await
    }

    pub(crate) async fn reset_fingerprint(
        &self,
        account_id: &str,
        request: &PrivilegedActionRequest<ResetFingerprintRequest>,
    ) -> Response<PrivilegedActionResponse<ResetFingerprintResponse>> {
        Request::builder()
            .uri(format!("/api/accounts/{account_id}/fingerprint-reset"))
            .authenticated(&AccountId::from_str(account_id).unwrap(), None, None)
            .post(&request)
            .call(&self.router)
            .await
    }

    pub(crate) async fn linear_webhook(
        &self,
        body_bytes: String,
        webhook_secret: Option<String>,
    ) -> Response<WebhookResponse> {
        let mut builder = Request::builder()
            .uri("/api/linear/webhook".to_string())
            .method(Method::POST)
            .body(Body::from(body_bytes.clone()))
            .unwrap();

        let headers = builder.headers_mut();
        headers.insert("content-type", HeaderValue::from_static("application/json"));

        if let Some(webhook_secret) = webhook_secret {
            let mut mac = Hmac::<Sha256>::new_from_slice(webhook_secret.as_bytes()).unwrap();
            mac.update(body_bytes.as_bytes());
            let signature = hex::encode(mac.finalize().into_bytes());

            headers.insert(
                "linear-signature",
                HeaderValue::from_str(&signature).unwrap(),
            );
        }

        builder.call(&self.router).await
    }

    pub(crate) async fn update_descriptor_backups(
        &self,
        account_id: &str,
        request: &UpdateDescriptorBackupsRequest,
        keys: Option<&TestAuthenticationKeys>,
    ) -> Response<UpdateDescriptorBackupsResponse> {
        Request::builder()
            .uri(format!("/api/accounts/{account_id}/descriptor-backups"))
            .authenticated(
                &AccountId::from_str(account_id).unwrap(),
                keys.map(|k| k.app.secret_key),
                keys.map(|k| k.hw.secret_key),
            )
            .put(request)
            .call(&self.router)
            .await
    }

    pub(crate) async fn set_notifications_triggers(
        &self,
        account_id: &str,
        request: &SetNotificationsTriggersRequest,
    ) -> Response<SetNotificationsTriggersResponse> {
        Request::builder()
            .uri(format!("/api/accounts/{account_id}/notifications/triggers"))
            .authenticated(&AccountId::from_str(account_id).unwrap(), None, None)
            .put(request)
            .call(&self.router)
            .await
    }
}
