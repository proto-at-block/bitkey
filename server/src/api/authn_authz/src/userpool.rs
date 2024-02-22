use std::collections::HashMap;
use std::str::FromStr;
use std::sync::{Arc, RwLock};

use account::entities::PubkeysToAccount;
use async_trait::async_trait;
use aws_config::BehaviorVersion;
use aws_sdk_cognitoidentityprovider::error::BuildError;
use aws_sdk_cognitoidentityprovider::operation::admin_get_user::AdminGetUserError;
use aws_sdk_cognitoidentityprovider::operation::admin_respond_to_auth_challenge::AdminRespondToAuthChallengeError;
use aws_sdk_cognitoidentityprovider::operation::admin_user_global_sign_out::AdminUserGlobalSignOutError;
use aws_sdk_cognitoidentityprovider::types::{AttributeType, AuthFlowType, ChallengeNameType};
use aws_sdk_cognitoidentityprovider::Client;
use aws_types::SdkConfig;
use dyn_clone::DynClone;
use rand::Rng;
use secp256k1::ecdsa::Signature;
use secp256k1::hashes::sha256;
use secp256k1::PublicKey;
use secp256k1::{Message, Secp256k1};
use serde::{Deserialize, Serialize};
use thiserror::Error;
use tracing::instrument;
use types::account::identifiers::AccountId;

use errors::ApiError;

use self::cognito_user::{CognitoUser, CognitoUsername};
use crate::test_utils::get_test_access_token_for_cognito_user;
use crate::userpool::UserPoolError::InitiateAuthError;

//IMPORTANT: Update terraform files and Lambdas when changing these attributes
const APP_KEY_ATTRIBUTE: &str = "custom:appPubKey";
const HW_KEY_ATTRIBUTE: &str = "custom:hwPubKey";
const RECOVERY_KEY_ATTRIBUTE: &str = "custom:recoveryPubKey";

#[derive(Debug, Error)]
pub enum UserPoolError {
    #[error("Could not create user in user pool: {0}")]
    BuildCognitoRequest(#[from] BuildError),
    #[error("Could not create user in user pool: {0}")]
    CreateUserError(String),
    #[error("Non-existent user in pool")]
    NonExistentUser,
    #[error("Invalid account id provided")]
    InvalidAccountId,
    #[error("Could not change user attributes: {0}")]
    ChangeUserAttributes(String),
    #[error("Could not initiate auth with cognito: {0}")]
    InitiateAuthError(String),
    #[error("Could not get user: {0}")]
    GetUserError(#[from] AdminGetUserError),
    #[error("Could not retrieve user pubkey")]
    GetUserPubkeyError,
    #[error("Could not perform global user signout for user id: {0}")]
    PerformUserSignOut(#[from] AdminUserGlobalSignOutError),
    #[error("Could not complete auth challenge: {0}")]
    AuthChallengeResponseError(#[from] AdminRespondToAuthChallengeError),
    #[error("Missing authentication result from completing authentication challenge")]
    MissingAuthResult,
    #[error("Missing access token from completing authentication challenge")]
    MissingAccessToken,
    #[error("Missing refresh token from completing authentication challenge")]
    MissingRefreshToken,
    #[error("Invalid session")]
    InvalidSession,
    #[error("Invalid challenge response")]
    InvalidChallengeResponse,
    #[error("Wrong user type")]
    WrongUserType,
}

impl From<UserPoolError> for ApiError {
    fn from(value: UserPoolError) -> Self {
        match value {
            UserPoolError::AuthChallengeResponseError(_)
            | UserPoolError::InvalidChallengeResponse => {
                ApiError::GenericBadRequest("Could not ".to_string())
            }
            UserPoolError::InitiateAuthError(error_str) => ApiError::GenericBadRequest(format!(
                "Could not initiate authentication: {error_str}"
            )),
            UserPoolError::NonExistentUser | UserPoolError::InvalidAccountId => {
                ApiError::GenericBadRequest("account not found".to_string())
            }
            UserPoolError::MissingAuthResult => ApiError::GenericInternalApplicationError(
                "Missing auth result from userpool".to_string(),
            ),
            UserPoolError::MissingAccessToken => ApiError::GenericInternalApplicationError(
                "Missing access token from userpool".to_string(),
            ),
            UserPoolError::MissingRefreshToken => ApiError::GenericInternalApplicationError(
                "Missing refresh token from userpool".to_string(),
            ),
            UserPoolError::InvalidSession => {
                ApiError::GenericBadRequest("invalid session".to_string())
            }
            _ => ApiError::GenericInternalApplicationError(
                "Error interacting with userpool".to_string(),
            ),
        }
    }
}

#[derive(Clone, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum CognitoMode {
    Environment,
    Test,
}

#[derive(Deserialize)]
pub struct Config {
    pub cognito: CognitoMode,
}

impl Config {
    pub async fn to_connection(self) -> Box<dyn CognitoIdpConnection> {
        match self.cognito {
            CognitoMode::Environment => Box::new(CognitoConnection::new_from_env().await),
            CognitoMode::Test => Box::new(FakeCognitoConnection::new()),
        }
    }
}

#[derive(Serialize)]
pub struct AuthChallenge {
    pub username: CognitoUsername,
    pub challenge: String,
    pub session: String,
}

#[derive(Serialize)]
pub struct AuthTokens {
    pub access_token: String,
    pub refresh_token: String,
}

#[async_trait]
pub trait CognitoIdpConnection: DynClone + Send + Sync {
    async fn create_new_wallet_user(
        &self,
        username: &CognitoUsername,
        app_key: String,
        hw_key: String,
    ) -> Result<String, UserPoolError>;
    async fn is_existing_cognito_user(
        &self,
        username: &CognitoUsername,
    ) -> Result<bool, UserPoolError>;
    async fn create_new_recovery_user(
        &self,
        username: &CognitoUsername,
        recovery_key: String,
    ) -> Result<String, UserPoolError>;
    async fn confirm_user(&self, username: &CognitoUsername) -> Result<(), UserPoolError>;
    async fn replace_wallet_user_pubkeys(
        &self,
        username: CognitoUsername,
        app_key: String,
        hw_key: String,
    ) -> Result<(), UserPoolError>;
    async fn replace_recovery_pubkey(
        &self,
        username: CognitoUsername,
        recovery_key: String,
    ) -> Result<(), UserPoolError>;
    async fn initiate_auth_for_wallet_user(
        &self,
        username: CognitoUsername,
    ) -> Result<AuthChallenge, UserPoolError>;
    async fn refresh_auth_token(&self, refresh_token: String) -> Result<AuthTokens, UserPoolError>;
    async fn respond_to_auth_challenge(
        &self,
        username: &CognitoUsername,
        session: String,
        challenge_response: String,
    ) -> Result<AuthTokens, UserPoolError>;
    async fn get_pubkeys_for_wallet_user(
        &self,
        username: CognitoUsername,
    ) -> Result<(String, String), UserPoolError>;
    async fn perform_sign_out_for_account(
        &self,
        username: CognitoUsername,
    ) -> Result<(), UserPoolError>;
}

dyn_clone::clone_trait_object!(CognitoIdpConnection);

#[derive(Clone)]
pub struct CognitoConnection {
    pub(crate) sdk_config: SdkConfig,
    pub(crate) user_pool_id: String,
    pub(crate) client_id: String,
}

impl CognitoConnection {
    pub async fn new_from_env() -> Self {
        Self {
            sdk_config: aws_config::load_defaults(BehaviorVersion::latest()).await,
            user_pool_id: std::env::var("COGNITO_USER_POOL")
                .expect("Could not get value of COGNITO_USER_POOL env variable"),
            client_id: std::env::var("COGNITO_CLIENT_ID")
                .expect("Could not get value of COGNITO_CLIENT_ID env variable"),
        }
    }

    fn gen_cognito_idp_client(&self) -> Client {
        Client::new(&self.sdk_config)
    }
}

#[async_trait]
impl CognitoIdpConnection for CognitoConnection {
    #[instrument(err, skip(self, username, app_key, hw_key))]
    async fn create_new_wallet_user(
        &self,
        username: &CognitoUsername,
        app_key: String,
        hw_key: String,
    ) -> Result<String, UserPoolError> {
        let client = self.gen_cognito_idp_client();
        let create_call = client
            .admin_create_user()
            .user_pool_id(&self.user_pool_id)
            .username(username.as_ref())
            .user_attributes(
                AttributeType::builder()
                    .name(APP_KEY_ATTRIBUTE)
                    .value(app_key)
                    .build()?,
            )
            .user_attributes(
                AttributeType::builder()
                    .name(HW_KEY_ATTRIBUTE)
                    .value(hw_key)
                    .build()?,
            );
        create_call
            .send()
            .await
            .map_err(|err| UserPoolError::CreateUserError(err.into_service_error().to_string()))?
            .user()
            .ok_or(UserPoolError::NonExistentUser)?
            .username()
            .ok_or(UserPoolError::NonExistentUser)
            .map(String::from)
    }

    async fn is_existing_cognito_user(
        &self,
        username: &CognitoUsername,
    ) -> Result<bool, UserPoolError> {
        let client = self.gen_cognito_idp_client();
        let result = client
            .admin_get_user()
            .user_pool_id(self.user_pool_id.clone())
            .username(username.as_ref())
            .send()
            .await;

        match result {
            Ok(_) => Ok(true),
            Err(err) => {
                let service_error = err.into_service_error();
                if service_error.is_user_not_found_exception() {
                    Ok(false)
                } else {
                    Err(UserPoolError::GetUserError(service_error))
                }
            }
        }
    }

    #[instrument(err, skip(self, recovery_key))]
    async fn create_new_recovery_user(
        &self,
        username: &CognitoUsername,
        recovery_key: String,
    ) -> Result<String, UserPoolError> {
        let client = self.gen_cognito_idp_client();
        let create_call = client
            .admin_create_user()
            .user_pool_id(&self.user_pool_id)
            .username(username.as_ref())
            .user_attributes(
                AttributeType::builder()
                    .name(RECOVERY_KEY_ATTRIBUTE)
                    .value(recovery_key)
                    .build()?,
            );
        create_call
            .send()
            .await
            .map_err(|err| UserPoolError::CreateUserError(err.into_service_error().to_string()))?
            .user()
            .ok_or(UserPoolError::NonExistentUser)?
            .username()
            .ok_or(UserPoolError::NonExistentUser)
            .map(String::from)
    }

    #[instrument(err, skip(self, username))]
    async fn confirm_user(&self, username: &CognitoUsername) -> Result<(), UserPoolError> {
        // ⚠️ Cognito *requires* that users have a password. Even though we disable password auth,
        // ⚠️ it still forces the user to have one. When we create a user using the `admin_create_user`
        // ⚠️ API, it sets a temporary password and then puts the user into a "FORCE_PASSWORD_RESET"
        // ⚠️ state, meaning that the user can't authenticate until they reset their password.
        // ⚠️ Luckily, we can administratively reset the users password and by marking it as "permanent",
        // ⚠️ the user object in cognito will be in a "CONFIRMED" state, which is what we need.
        // ⚠️ So, even though our users will never use a password, we have to set one. So we're going
        // ⚠️ to generate a random max-length (99 character) password and then throw it away.
        let password: String = rand::thread_rng()
            .sample_iter(rand::distributions::Alphanumeric)
            .take(99)
            .map(char::from)
            .collect();
        let client = self.gen_cognito_idp_client();
        client
            .admin_set_user_password()
            .user_pool_id(&self.user_pool_id)
            .username(username.as_ref())
            .password(password)
            .permanent(true) // ⛔️DO NOT CHANGE THIS TO FALSE OR THE USER WONT BE ABLE TO AUTH
            .send()
            .await
            .map_err(|err| {
                UserPoolError::CreateUserError(format!("Could not reset user password: {err}"))
            })?;
        Ok(())
    }

    async fn replace_wallet_user_pubkeys(
        &self,
        username: CognitoUsername,
        app_key: String,
        hw_key: String,
    ) -> Result<(), UserPoolError> {
        let client = self.gen_cognito_idp_client();
        let update_user_attributes = client
            .admin_update_user_attributes()
            .user_pool_id(self.user_pool_id.clone())
            .username(username)
            .user_attributes(
                AttributeType::builder()
                    .name(APP_KEY_ATTRIBUTE)
                    .value(app_key)
                    .build()?,
            )
            .user_attributes(
                AttributeType::builder()
                    .name(HW_KEY_ATTRIBUTE)
                    .value(hw_key)
                    .build()?,
            );

        update_user_attributes.send().await.map_err(|err| {
            UserPoolError::ChangeUserAttributes(err.into_service_error().to_string())
        })?;

        // TODO: revoke client refresh token - W-1146/revoke-cognito-refresh-token-when-auth-keys-are-rotated

        Ok(())
    }

    async fn replace_recovery_pubkey(
        &self,
        username: CognitoUsername,
        recovery_key: String,
    ) -> Result<(), UserPoolError> {
        let client = self.gen_cognito_idp_client();
        let update_user_attributes = client
            .admin_update_user_attributes()
            .user_pool_id(self.user_pool_id.clone())
            .username(username)
            .user_attributes(
                AttributeType::builder()
                    .name(RECOVERY_KEY_ATTRIBUTE)
                    .value(recovery_key)
                    .build()?,
            );

        update_user_attributes.send().await.map_err(|err| {
            UserPoolError::ChangeUserAttributes(err.into_service_error().to_string())
        })?;

        // TODO: revoke client refresh token - W-1146/revoke-cognito-refresh-token-when-auth-keys-are-rotated

        Ok(())
    }

    async fn initiate_auth_for_wallet_user(
        &self,
        username: CognitoUsername,
    ) -> Result<AuthChallenge, UserPoolError> {
        let client = self.gen_cognito_idp_client();
        let initiate_auth_response = client
            .admin_initiate_auth()
            .user_pool_id(self.user_pool_id.clone())
            .client_id(self.client_id.clone())
            .auth_flow(AuthFlowType::CustomAuth)
            .auth_parameters("USERNAME", username.as_ref())
            .send()
            .await
            .map_err(|err| InitiateAuthError(err.into_service_error().to_string()))?;
        let challenge_str = initiate_auth_response
            .challenge_parameters()
            .ok_or(InitiateAuthError("Missing auth parameters".to_string()))?
            .get("challenge")
            .ok_or(InitiateAuthError("Missing auth challenge".to_string()))?;
        let session = initiate_auth_response
            .session()
            .ok_or(InitiateAuthError("Missing auth session".to_string()))?;
        Ok(AuthChallenge {
            username,
            challenge: challenge_str.to_string(),
            session: session.to_string(),
        })
    }

    async fn refresh_auth_token(&self, refresh_token: String) -> Result<AuthTokens, UserPoolError> {
        let client = self.gen_cognito_idp_client();
        let initiate_auth_response = client
            .admin_initiate_auth()
            .user_pool_id(self.user_pool_id.clone())
            .client_id(self.client_id.clone())
            .auth_flow(AuthFlowType::RefreshTokenAuth)
            .auth_parameters("REFRESH_TOKEN", refresh_token.clone())
            .send()
            .await
            .map_err(|err| InitiateAuthError(err.into_service_error().to_string()))?;
        let auth_result = initiate_auth_response
            .authentication_result
            .ok_or(UserPoolError::MissingAuthResult)?;
        Ok(AuthTokens {
            access_token: auth_result
                .access_token
                .ok_or(UserPoolError::MissingAccessToken)?,
            refresh_token: auth_result.refresh_token.unwrap_or(refresh_token), // if the original refresh token is still good, cognito won't return a new one
        })
    }

    async fn respond_to_auth_challenge(
        &self,
        username: &CognitoUsername,
        session: String,
        challenge_response: String,
    ) -> Result<AuthTokens, UserPoolError> {
        let client = self.gen_cognito_idp_client();
        let response = client
            .admin_respond_to_auth_challenge()
            .user_pool_id(self.user_pool_id.clone())
            .client_id(self.client_id.clone())
            .session(session)
            .challenge_name(ChallengeNameType::CustomChallenge)
            .challenge_responses("USERNAME", username.as_ref())
            .challenge_responses("ANSWER", challenge_response)
            .send()
            .await
            .map_err(|err| UserPoolError::AuthChallengeResponseError(err.into_service_error()))?;
        let authentication_result = response
            .authentication_result
            .ok_or(UserPoolError::MissingAuthResult)?;
        let access_token = authentication_result
            .access_token
            .ok_or(UserPoolError::MissingAccessToken)?;
        let refresh_token = authentication_result
            .refresh_token
            .ok_or(UserPoolError::MissingRefreshToken)?;
        Ok(AuthTokens {
            access_token,
            refresh_token,
        })
    }

    async fn get_pubkeys_for_wallet_user(
        &self,
        username: CognitoUsername,
    ) -> Result<(String, String), UserPoolError> {
        let client = self.gen_cognito_idp_client();
        let user = client
            .admin_get_user()
            .user_pool_id(self.user_pool_id.clone())
            .username(username)
            .send()
            .await
            .map_err(|err| UserPoolError::GetUserError(err.into_service_error()))?;
        let user_attributes = user.user_attributes();
        let mut app_key = "";
        let mut hw_key = "";
        for attr in user_attributes {
            if attr.name() == APP_KEY_ATTRIBUTE {
                app_key = attr.value().ok_or(UserPoolError::GetUserPubkeyError)?;
            } else if attr.name() == HW_KEY_ATTRIBUTE {
                hw_key = attr.value().ok_or(UserPoolError::GetUserPubkeyError)?;
            }
        }
        if app_key.is_empty() {
            return Err(UserPoolError::GetUserPubkeyError);
        }
        Ok((app_key.to_string(), hw_key.to_string()))
    }

    async fn perform_sign_out_for_account(
        &self,
        username: CognitoUsername,
    ) -> Result<(), UserPoolError> {
        let client = self.gen_cognito_idp_client();
        client
            .admin_user_global_sign_out()
            .user_pool_id(self.user_pool_id.clone())
            .username(username)
            .send()
            .await
            .map_err(|err| err.into_service_error())
            .map_err(UserPoolError::from)?;
        Ok(())
    }
}

pub mod cognito_user {
    use serde::{Deserialize, Serialize};
    use std::{fmt, str::FromStr};
    use types::account::identifiers::AccountId;
    use utoipa::ToSchema;

    use super::UserPoolError;

    #[derive(Serialize, Deserialize, Clone, Debug, PartialEq, Eq, ToSchema)]
    pub struct CognitoUsername(String);

    impl CognitoUsername {
        fn new(username: String) -> Self {
            Self(username)
        }
    }

    impl FromStr for CognitoUsername {
        type Err = UserPoolError;

        fn from_str(s: &str) -> Result<Self, Self::Err> {
            let user = CognitoUser::from_str(s)?;
            Ok(user.into())
        }
    }

    impl AsRef<str> for CognitoUsername {
        fn as_ref(&self) -> &str {
            self.0.as_ref()
        }
    }

    impl From<CognitoUsername> for String {
        fn from(u: CognitoUsername) -> Self {
            u.0
        }
    }

    impl fmt::Display for CognitoUsername {
        fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
            write!(f, "{}", self.as_ref())
        }
    }

    #[derive(Clone, Debug, PartialEq, Eq)]
    pub enum CognitoUser {
        Wallet(AccountId),
        Recovery(AccountId),
    }

    impl CognitoUser {
        pub fn get_account_id(&self) -> AccountId {
            match self {
                CognitoUser::Wallet(id) => id.to_owned(),
                CognitoUser::Recovery(id) => id.to_owned(),
            }
        }
    }

    impl From<CognitoUser> for CognitoUsername {
        fn from(u: CognitoUser) -> Self {
            (&u).into()
        }
    }

    impl From<&CognitoUser> for CognitoUsername {
        fn from(u: &CognitoUser) -> Self {
            match u {
                CognitoUser::Wallet(id) => CognitoUsername::new(format!("{}", id)),
                CognitoUser::Recovery(id) => CognitoUsername::new(format!("{}-recovery", id)),
            }
        }
    }

    impl FromStr for CognitoUser {
        type Err = UserPoolError;

        fn from_str(value: &str) -> Result<Self, Self::Err> {
            let (use_recovery_domain, account_id_str) = if value.ends_with("-recovery") {
                let parts: Vec<&str> = value.split("-recovery").collect();
                (true, parts[0])
            } else {
                (false, value)
            };
            let account_id =
                AccountId::from_str(account_id_str).map_err(|_| UserPoolError::NonExistentUser)?;
            if use_recovery_domain {
                Ok(CognitoUser::Recovery(account_id))
            } else {
                Ok(CognitoUser::Wallet(account_id))
            }
        }
    }
}

pub struct CreateWalletUserInput {
    app_key: PublicKey,
    hw_key: PublicKey,
}

impl CreateWalletUserInput {
    #[must_use]
    pub fn new(app_key: PublicKey, hw_key: PublicKey) -> Self {
        Self { app_key, hw_key }
    }
}

pub struct CreateRecoveryUserInput {
    recovery_key: PublicKey,
}

impl CreateRecoveryUserInput {
    #[must_use]
    pub fn new(recovery_key: PublicKey) -> Self {
        Self { recovery_key }
    }
}

#[derive(Clone)]
pub struct UserPoolService {
    cognito_client: Box<dyn CognitoIdpConnection>,
}

impl UserPoolService {
    pub fn new(client: Box<dyn CognitoIdpConnection>) -> Self {
        Self {
            cognito_client: client,
        }
    }

    #[instrument(err, skip(self, account_input, recovery_input))]
    pub async fn create_users(
        &self,
        account_id: &AccountId,
        account_input: Option<CreateWalletUserInput>,
        recovery_input: Option<CreateRecoveryUserInput>,
    ) -> Result<(), UserPoolError> {
        if let Some(input) = account_input {
            let username: CognitoUsername = CognitoUser::Wallet(account_id.to_owned()).into();
            self.cognito_client
                .create_new_wallet_user(
                    &username,
                    input.app_key.to_string(),
                    input.hw_key.to_string(),
                )
                .await?;
            self.cognito_client.confirm_user(&username).await?;
        }

        if let Some(input) = recovery_input {
            let username: CognitoUsername = CognitoUser::Recovery(account_id.to_owned()).into();
            self.cognito_client
                .create_new_recovery_user(&username, input.recovery_key.to_string())
                .await?;
            self.cognito_client.confirm_user(&username).await?;
        }
        Ok(())
    }

    #[instrument(err, skip(self, user))]
    pub async fn is_existing_cognito_user(&self, user: CognitoUser) -> Result<bool, UserPoolError> {
        self.cognito_client
            .is_existing_cognito_user(&user.into())
            .await
    }

    #[instrument(err, skip(self, app_key, hw_key))]
    pub async fn create_wallet_user_if_necessary(
        &self,
        account_id: &AccountId,
        app_key: PublicKey,
        hw_key: PublicKey,
    ) -> Result<(), UserPoolError> {
        let username: CognitoUsername = CognitoUser::Wallet(account_id.to_owned()).into();
        if !self
            .cognito_client
            .is_existing_cognito_user(&username)
            .await?
        {
            self.cognito_client
                .create_new_wallet_user(&username, app_key.to_string(), hw_key.to_string())
                .await?;
            self.cognito_client.confirm_user(&username).await?;
        }
        Ok(())
    }

    #[instrument(err, skip(self, recovery_key))]
    pub async fn create_recovery_user_if_necessary(
        &self,
        account_id: &AccountId,
        recovery_key: PublicKey,
    ) -> Result<(), UserPoolError> {
        let recovery_username: CognitoUsername =
            CognitoUser::Recovery(account_id.to_owned()).into();
        if !self
            .cognito_client
            .is_existing_cognito_user(&recovery_username)
            .await?
        {
            self.cognito_client
                .create_new_recovery_user(&recovery_username, recovery_key.to_string())
                .await?;
            self.cognito_client.confirm_user(&recovery_username).await?;
        }
        Ok(())
    }

    #[instrument(err, skip(self, app_key, hw_key))]
    pub async fn rotate_account_auth_keys(
        &self,
        account_id: &AccountId,
        app_key: PublicKey,
        hw_key: PublicKey,
        recovery_key: Option<PublicKey>,
    ) -> Result<(), UserPoolError> {
        let account_username: CognitoUsername = CognitoUser::Wallet(account_id.clone()).into();
        self.cognito_client
            .replace_wallet_user_pubkeys(
                account_username.clone(),
                app_key.to_string(),
                hw_key.to_string(),
            )
            .await?;
        self.cognito_client
            .perform_sign_out_for_account(account_username)
            .await?;

        if let Some(k) = recovery_key {
            let recovery_username: CognitoUsername =
                CognitoUser::Recovery(account_id.to_owned()).into();
            self.cognito_client
                .replace_recovery_pubkey(recovery_username.clone(), k.to_string())
                .await?;
            self.cognito_client
                .perform_sign_out_for_account(recovery_username)
                .await?;
        }
        Ok(())
    }

    #[instrument(err, skip(self))]
    pub async fn initiate_auth_for_wallet_user(
        &self,
        pubkeys_to_account: &PubkeysToAccount,
    ) -> Result<AuthChallenge, UserPoolError> {
        let username = CognitoUser::Wallet(pubkeys_to_account.id.clone()).into();

        // TODO: Safe to remove this block after W-5688 is resolved
        if let (Some(app_pubkey), Some(hw_pubkey)) = (
            pubkeys_to_account.application_auth_pubkey,
            pubkeys_to_account.hardware_auth_pubkey,
        ) {
            self.create_wallet_user_if_necessary(&pubkeys_to_account.id, app_pubkey, hw_pubkey)
                .await?;
        }

        self.cognito_client
            .initiate_auth_for_wallet_user(username)
            .await
    }

    #[instrument(err, skip(self))]
    pub async fn initiate_auth_for_recovery_user(
        &self,
        pubkeys_to_account: &PubkeysToAccount,
    ) -> Result<AuthChallenge, UserPoolError> {
        let username = CognitoUser::Recovery(pubkeys_to_account.id.clone()).into();

        // TODO: Safe to remove this block after W-5688 is resolved
        if let Some(recovery_pubkey) = pubkeys_to_account.recovery_auth_pubkey {
            self.create_recovery_user_if_necessary(&pubkeys_to_account.id, recovery_pubkey)
                .await?;
        }

        self.cognito_client
            .initiate_auth_for_wallet_user(username)
            .await
    }

    #[instrument(err, skip(self, challenge_response))]
    pub async fn respond_to_auth_challenge(
        &self,
        username: &CognitoUsername,
        session: String,
        challenge_response: String,
    ) -> Result<AuthTokens, UserPoolError> {
        self.cognito_client
            .respond_to_auth_challenge(username, session, challenge_response)
            .await
    }

    #[instrument(err, skip(self, refresh_token))]
    pub async fn refresh_access_token(
        &self,
        refresh_token: String,
    ) -> Result<AuthTokens, UserPoolError> {
        self.cognito_client.refresh_auth_token(refresh_token).await
    }

    #[instrument(err, skip(self))]
    pub async fn get_pubkeys_for_wallet_user(
        &self,
        account_id: AccountId,
    ) -> Result<(String, String), UserPoolError> {
        let username: CognitoUsername = CognitoUser::Wallet(account_id).into();
        self.cognito_client
            .get_pubkeys_for_wallet_user(username)
            .await
    }
}

#[derive(Debug)]
enum UserKeys {
    Wallet {
        app_key: PublicKey,
        hw_key: PublicKey,
    },
    Recovery {
        recovery_key: PublicKey,
    },
}

#[derive(Clone)]
pub struct FakeCognitoConnection {
    user_keys: Arc<RwLock<HashMap<String, UserKeys>>>,
    session_challenges: Arc<RwLock<HashMap<String, String>>>,
    refresh_tokens: Arc<RwLock<HashMap<String, String>>>,
}

impl Default for FakeCognitoConnection {
    fn default() -> Self {
        let mut user_keys = HashMap::new();
        // insert test keys from server/src/key_proof/test_utils.rs:12 used in unit tests
        let wallet_user_keys = UserKeys::Wallet {
            app_key: PublicKey::from_str(
                "02b98a7fb8cc007048625b6446ad49a1b3a722df8c1ca975b87160023e14d19097",
            )
            .expect("could not parse static pubkey"),
            hw_key: PublicKey::from_str(
                "0381aaadc8a5e83f4576df823cf22a5b1969cf704a0d5f6f68bd757410c9917aac",
            )
            .expect("could not parse static pubkey"),
        };
        user_keys.insert(
            "urn:wallet-account:000000000000000000000000000".to_string(),
            wallet_user_keys,
        );
        let recovery_user_keys = UserKeys::Recovery {
            recovery_key: PublicKey::from_str(
                "0381aaadc8a5e83f4576df823cf22a5b1969cf704a0d5f6f68bd757410c9917aac",
            )
            .expect("could not parse static pubkey"),
        };
        user_keys.insert(
            "urn:wallet-account:000000000000000000000000000-recovery".to_string(),
            recovery_user_keys,
        );
        Self {
            user_keys: Arc::new(RwLock::new(user_keys)),
            session_challenges: Arc::new(RwLock::new(HashMap::new())),
            refresh_tokens: Arc::new(RwLock::new(HashMap::new())),
        }
    }
}
impl FakeCognitoConnection {
    pub fn new() -> Self {
        Self::default()
    }
}

#[async_trait]
impl CognitoIdpConnection for FakeCognitoConnection {
    async fn create_new_wallet_user(
        &self,
        username: &CognitoUsername,
        app_key: String,
        hw_key: String,
    ) -> Result<String, UserPoolError> {
        let mut user_keys = self.user_keys.write().map_err(|_| {
            UserPoolError::CreateUserError("Could not get lock on in-mem user database".to_string())
        })?;

        let username_str = username.to_string();
        if user_keys.contains_key(&username_str) {
            return Err(UserPoolError::CreateUserError(
                "User already exists".to_string(),
            )); // todo: is this the right thing to do?
        }
        let new_keys = UserKeys::Wallet {
            app_key: PublicKey::from_str(&app_key)
                .map_err(|e| UserPoolError::CreateUserError(e.to_string()))?,
            hw_key: PublicKey::from_str(&hw_key)
                .map_err(|e| UserPoolError::CreateUserError(e.to_string()))?,
        };
        user_keys.insert(username_str.clone(), new_keys);
        Ok(username_str)
    }

    async fn is_existing_cognito_user(
        &self,
        username: &CognitoUsername,
    ) -> Result<bool, UserPoolError> {
        let cognito_user =
            CognitoUser::from_str(username.as_ref()).expect("Valid Cognito username");
        let cognito_username = Into::<CognitoUsername>::into(cognito_user.clone()).to_string();
        let user_keys = self.user_keys.read().map_err(|_| {
            UserPoolError::CreateUserError("Could not get lock on in-mem user database".to_string())
        })?;
        Ok(user_keys.contains_key(&cognito_username))
    }

    async fn create_new_recovery_user(
        &self,
        username: &CognitoUsername,
        recovery_key: String,
    ) -> Result<String, UserPoolError> {
        let mut user_keys = self.user_keys.write().map_err(|_| {
            UserPoolError::CreateUserError("Could not get lock on in-mem user database".to_string())
        })?;

        let username_str = username.to_string();
        if user_keys.contains_key(&username_str) {
            return Err(UserPoolError::CreateUserError(
                "Recovery user already exists".to_string(),
            ));
        }
        user_keys.insert(
            username_str.clone(),
            UserKeys::Recovery {
                recovery_key: PublicKey::from_str(&recovery_key)
                    .map_err(|e| UserPoolError::CreateUserError(e.to_string()))?,
            },
        );
        Ok(username_str)
    }

    async fn confirm_user(&self, _username: &CognitoUsername) -> Result<(), UserPoolError> {
        Ok(())
    }

    async fn replace_wallet_user_pubkeys(
        &self,
        username: CognitoUsername,
        app_key: String,
        hw_key: String,
    ) -> Result<(), UserPoolError> {
        let mut user_keys = self.user_keys.write().map_err(|_| {
            UserPoolError::CreateUserError("Could not get lock on in-mem user database".to_string())
        })?;
        let new_keys = UserKeys::Wallet {
            app_key: PublicKey::from_str(&app_key)
                .map_err(|e| UserPoolError::CreateUserError(e.to_string()))?,
            hw_key: PublicKey::from_str(&hw_key)
                .map_err(|e| UserPoolError::CreateUserError(e.to_string()))?,
        };
        user_keys.insert(username.to_string(), new_keys);
        Ok(())
    }

    async fn replace_recovery_pubkey(
        &self,
        username: CognitoUsername,
        recovery_key: String,
    ) -> Result<(), UserPoolError> {
        let mut recovery_keys = self.user_keys.write().map_err(|_| {
            UserPoolError::CreateUserError("Could not get lock on in-mem user database".to_string())
        })?;
        recovery_keys.insert(
            username.to_string(),
            UserKeys::Recovery {
                recovery_key: PublicKey::from_str(&recovery_key)
                    .map_err(|e| UserPoolError::CreateUserError(e.to_string()))?,
            },
        );
        Ok(())
    }

    async fn initiate_auth_for_wallet_user(
        &self,
        username: CognitoUsername,
    ) -> Result<AuthChallenge, UserPoolError> {
        let challenge = get_64_random_hex_bytes_for_tests();
        let session = get_64_random_hex_bytes_for_tests();

        let mut session_challenges = self.session_challenges.write().map_err(|_| {
            UserPoolError::InitiateAuthError("Could not write to session store".to_string())
        })?;
        session_challenges.insert(session.to_string(), challenge.to_string());

        Ok(AuthChallenge {
            username,
            challenge,
            session,
        })
    }

    async fn refresh_auth_token(&self, refresh_token: String) -> Result<AuthTokens, UserPoolError> {
        let refresh_token_store = self.refresh_tokens.write().map_err(|_| {
            UserPoolError::InitiateAuthError("Poisoned lock on in-memory userpool".to_string())
        })?;
        let username = refresh_token_store
            .get(&refresh_token)
            .ok_or(UserPoolError::InitiateAuthError(
                "invalid refresh token".to_string(),
            ))?
            .clone();
        let cognito_user = CognitoUser::from_str(username.as_str())?;

        Ok(AuthTokens {
            access_token: get_test_access_token_for_cognito_user(&cognito_user),
            refresh_token,
        })
    }

    async fn respond_to_auth_challenge(
        &self,
        username: &CognitoUsername,
        session: String,
        challenge_response: String,
    ) -> Result<AuthTokens, UserPoolError> {
        let user: CognitoUser = CognitoUser::from_str(username.as_ref())?;
        let secp: Secp256k1<secp256k1::All> = Secp256k1::new();

        let answer_correct = match user {
            CognitoUser::Wallet(_) => {
                let keystore = self.user_keys.read().map_err(|_| {
                    UserPoolError::InitiateAuthError(
                        "Poisoned lock on in-memory userpool".to_string(),
                    )
                })?;
                let UserKeys::Wallet { app_key, hw_key } = keystore
                    .get(username.as_ref())
                    .ok_or(UserPoolError::NonExistentUser)?
                else {
                    return Err(UserPoolError::WrongUserType);
                };
                let session_store = self.session_challenges.read().unwrap();
                let challenge = session_store
                    .get(&session)
                    .ok_or(UserPoolError::InvalidSession)?;
                let message = Message::from_hashed_data::<sha256::Hash>(challenge.as_bytes());

                if let Ok(signature) = Signature::from_str(&challenge_response) {
                    secp.verify_ecdsa(&message, &signature, app_key).is_ok()
                        || secp.verify_ecdsa(&message, &signature, hw_key).is_ok()
                } else {
                    false
                }
            }
            CognitoUser::Recovery(_) => {
                let keystore = self.user_keys.read().map_err(|_| {
                    UserPoolError::InitiateAuthError(
                        "Poisoned lock on in-memory userpool".to_string(),
                    )
                })?;
                let UserKeys::Recovery { recovery_key } = keystore
                    .get(username.as_ref())
                    .ok_or(UserPoolError::NonExistentUser)?
                else {
                    return Err(UserPoolError::WrongUserType);
                };
                let session_store = self.session_challenges.read().unwrap();
                let challenge = session_store
                    .get(&session)
                    .ok_or(UserPoolError::InvalidSession)?;
                let message = Message::from_hashed_data::<sha256::Hash>(challenge.as_bytes());

                if let Ok(signature) = Signature::from_str(&challenge_response) {
                    secp.verify_ecdsa(&message, &signature, recovery_key)
                        .is_ok()
                } else {
                    false
                }
            }
        };

        if answer_correct {
            let username_str = username.to_string();
            // store away a random refresh token that maps to the username so in the future we can re-issue access tokens
            let mut rng = rand::thread_rng();
            let mut refresh_token_bytes: [u8; 64] = [0; 64];
            rng.fill(&mut refresh_token_bytes);
            let refresh_token = hex::encode(refresh_token_bytes);
            let mut refresh_token_store = self.refresh_tokens.write().unwrap();
            refresh_token_store.insert(refresh_token.clone(), username_str.clone());

            Ok(AuthTokens {
                access_token: get_test_access_token_for_cognito_user(&CognitoUser::Wallet(
                    AccountId::from_str(&username_str)
                        .map_err(|_| UserPoolError::NonExistentUser)?,
                )),
                refresh_token,
            })
        } else {
            Err(UserPoolError::InvalidChallengeResponse)
        }
    }

    async fn get_pubkeys_for_wallet_user(
        &self,
        username: CognitoUsername,
    ) -> Result<(String, String), UserPoolError> {
        let user_keys = self
            .user_keys
            .read()
            .map_err(|_| UserPoolError::GetUserPubkeyError)?;
        let UserKeys::Wallet { app_key, hw_key } = user_keys
            .get(username.as_ref())
            .ok_or(UserPoolError::NonExistentUser)?
        else {
            return Err(UserPoolError::WrongUserType);
        };
        Ok((app_key.to_string(), hw_key.to_string()))
    }

    async fn perform_sign_out_for_account(
        &self,
        _username: CognitoUsername,
    ) -> Result<(), UserPoolError> {
        Ok(())
    }
}

fn get_64_random_hex_bytes_for_tests() -> String {
    let mut rng = rand::thread_rng();
    let mut the_bytes: [u8; 64] = [0; 64];
    rng.fill(&mut the_bytes);
    hex::encode(the_bytes)
}
