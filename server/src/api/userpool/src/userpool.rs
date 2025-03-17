use std::collections::{HashMap, HashSet};
use std::str::FromStr;
use std::sync::{Arc, RwLock};

use async_trait::async_trait;
use aws_config::BehaviorVersion;
use aws_sdk_cognitoidentityprovider::error::BuildError;
use aws_sdk_cognitoidentityprovider::operation::admin_create_user::AdminCreateUserError;
use aws_sdk_cognitoidentityprovider::operation::admin_get_user::AdminGetUserError;
use aws_sdk_cognitoidentityprovider::operation::admin_respond_to_auth_challenge::AdminRespondToAuthChallengeError;
use aws_sdk_cognitoidentityprovider::operation::admin_update_user_attributes::AdminUpdateUserAttributesError;
use aws_sdk_cognitoidentityprovider::operation::admin_user_global_sign_out::AdminUserGlobalSignOutError;
use aws_sdk_cognitoidentityprovider::operation::get_user::GetUserError;
use aws_sdk_cognitoidentityprovider::types::error::builders::UserNotFoundExceptionBuilder;
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
use types::account::PubkeysToAccount;
use types::authn_authz::cognito::{CognitoUser, CognitoUsername};

use crate::test_utils::get_test_access_token_for_cognito_user;
use crate::userpool::UserPoolError::InitiateAuthError;

//IMPORTANT: Update terraform files and Lambdas when changing these attributes
const PUBLIC_KEY_ATTRIBUTE: &str = "custom:publicKey";

// 5 minutes in seconds
const TEST_EXPIRES_IN_SECONDS: i32 = 300;

#[derive(Debug, Error)]
pub enum UserPoolError {
    #[error("Could not create user in user pool: {0}")]
    BuildCognitoRequest(#[from] BuildError),
    #[error("Could not create user: {0}")]
    AdminCreateUserError(#[from] AdminCreateUserError),
    #[error("Could not create user in user pool: {0}")]
    CreateUserError(String),
    #[error("Non-existent user in pool")]
    NonExistentUser,
    #[error("Invalid account id provided")]
    InvalidAccountId,
    #[error("Could not change user attributes")]
    ChangeUserAttributes(#[from] AdminUpdateUserAttributesError),
    #[error("Could not initiate auth with cognito: {0}")]
    InitiateAuthError(String),
    #[error("Could not get user: {0}")]
    AdminGetUserError(#[from] AdminGetUserError),
    #[error("Could not get user: {0}")]
    GetUserError(#[from] GetUserError),
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
    pub expires_in: i32,
}

#[async_trait]
pub trait CognitoIdpConnection: DynClone + Send + Sync {
    async fn create_new_user(
        &self,
        username: &CognitoUsername,
        public_key: String,
    ) -> Result<String, UserPoolError>;
    async fn is_existing_user(&self, username: &CognitoUsername) -> Result<bool, UserPoolError>;
    async fn confirm_user(&self, username: &CognitoUsername) -> Result<(), UserPoolError>;
    async fn replace_user_pubkey(
        &self,
        username: &CognitoUsername,
        public_key: String,
    ) -> Result<(), UserPoolError>;
    async fn initiate_auth_for_user(
        &self,
        username: &CognitoUsername,
    ) -> Result<AuthChallenge, UserPoolError>;
    async fn refresh_auth_token(&self, refresh_token: String) -> Result<AuthTokens, UserPoolError>;
    async fn respond_to_auth_challenge(
        &self,
        username: &CognitoUsername,
        session: String,
        challenge_response: String,
    ) -> Result<AuthTokens, UserPoolError>;
    async fn get_pubkey_for_user(
        &self,
        username: &CognitoUsername,
    ) -> Result<String, UserPoolError>;
    async fn sign_out_user(&self, username: &CognitoUsername) -> Result<(), UserPoolError>;
    async fn is_access_token_revoked(&self, access_token: String) -> Result<bool, UserPoolError>;
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
    #[instrument(err, skip(self, username, public_key))]
    async fn create_new_user(
        &self,
        username: &CognitoUsername,
        public_key: String,
    ) -> Result<String, UserPoolError> {
        let client = self.gen_cognito_idp_client();
        let create_call = client
            .admin_create_user()
            .user_pool_id(&self.user_pool_id)
            .username(username.as_ref())
            .user_attributes(
                AttributeType::builder()
                    .name(PUBLIC_KEY_ATTRIBUTE)
                    .value(public_key)
                    .build()?,
            );
        create_call
            .send()
            .await
            .map_err(|err| err.into_service_error())?
            .user()
            .ok_or(UserPoolError::NonExistentUser)?
            .username()
            .ok_or(UserPoolError::NonExistentUser)
            .map(String::from)
    }

    #[instrument(err, skip(self, username))]
    async fn is_existing_user(&self, username: &CognitoUsername) -> Result<bool, UserPoolError> {
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
                    Err(UserPoolError::AdminGetUserError(service_error))
                }
            }
        }
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

    #[instrument(err, skip(self, username, public_key))]
    async fn replace_user_pubkey(
        &self,
        username: &CognitoUsername,
        public_key: String,
    ) -> Result<(), UserPoolError> {
        let client = self.gen_cognito_idp_client();
        let update_user_attributes = client
            .admin_update_user_attributes()
            .user_pool_id(self.user_pool_id.clone())
            .username(username.clone())
            .user_attributes(
                AttributeType::builder()
                    .name(PUBLIC_KEY_ATTRIBUTE)
                    .value(public_key)
                    .build()?,
            );

        update_user_attributes
            .send()
            .await
            .map_err(|err| err.into_service_error())?;

        Ok(())
    }

    #[instrument(err, skip(self, username))]
    async fn initiate_auth_for_user(
        &self,
        username: &CognitoUsername,
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
            username: username.clone(),
            challenge: challenge_str.to_string(),
            session: session.to_string(),
        })
    }

    #[instrument(err, skip(self, refresh_token))]
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
            expires_in: auth_result.expires_in,
        })
    }

    #[instrument(err, skip(self, username, session, challenge_response))]
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
        let expires_in = authentication_result.expires_in;
        Ok(AuthTokens {
            access_token,
            refresh_token,
            expires_in,
        })
    }

    #[instrument(err, skip(self, username))]
    async fn get_pubkey_for_user(
        &self,
        username: &CognitoUsername,
    ) -> Result<String, UserPoolError> {
        let client = self.gen_cognito_idp_client();
        let user = client
            .admin_get_user()
            .user_pool_id(self.user_pool_id.clone())
            .username(username.clone())
            .send()
            .await
            .map_err(|err| err.into_service_error())?;
        let user_attributes = user.user_attributes();
        let mut public_key = "";
        for attr in user_attributes {
            if attr.name() == PUBLIC_KEY_ATTRIBUTE {
                public_key = attr.value().ok_or(UserPoolError::GetUserPubkeyError)?;
            }
        }
        if public_key.is_empty() {
            return Err(UserPoolError::GetUserPubkeyError);
        }
        Ok(public_key.to_string())
    }

    #[instrument(err, skip(self, username))]
    async fn sign_out_user(&self, username: &CognitoUsername) -> Result<(), UserPoolError> {
        let client = self.gen_cognito_idp_client();
        client
            .admin_user_global_sign_out()
            .user_pool_id(self.user_pool_id.clone())
            .username(username.clone())
            .send()
            .await
            .map_err(|err| err.into_service_error())?;
        Ok(())
    }

    // We make a call to the Cognito service _as_ the user, using the provided access token
    // to ensure that the user hasn't been signed out at the Cognito level.
    //
    // This is probably too expensive to call in our auth layer for _every_ request, so we should
    // only call it when absolutely necessary.
    //
    // When a user is logged out at the Cognito level (this happens when auth keys are rotated),
    // their existing access token will still be valid, according to our auth layer, up until its
    // natural expiration which is up to 5 minutes later. This generally isn't a concern, because
    // key proofs will fail to verify immediately if the old keys are used, and we generally check
    // key proofs in scenarios where account state is mutated.
    //
    // An example of when we would need to call this function is when a new device token is added,
    // because we do not gate this behind key proofs but we want to prevent inactive apps from adding
    // their own device tokens to the account immediately after they have been logged out.
    //
    // Turns out others had the same idea: https://github.com/aws-amplify/amplify-js/issues/3435#issuecomment-1667013888
    #[instrument(err, skip(self, access_token))]
    async fn is_access_token_revoked(&self, access_token: String) -> Result<bool, UserPoolError> {
        let client = self.gen_cognito_idp_client();
        client
            .get_user()
            .access_token(access_token)
            .send()
            .await
            .map_or_else(
                |err| {
                    let service_error = err.into_service_error();
                    if service_error.is_not_authorized_exception() {
                        Ok(true)
                    } else {
                        Err(service_error)?
                    }
                },
                |_| Ok(false),
            )
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

    #[deprecated]
    pub async fn direct_replace_user_pubkey(
        &self,
        username: &CognitoUsername,
        public_key: PublicKey,
    ) -> Result<(), UserPoolError> {
        self.cognito_client
            .replace_user_pubkey(username, public_key.to_string())
            .await
    }

    async fn create_or_update_user_if_necessary(
        &self,
        username: &CognitoUsername,
        public_key: PublicKey,
    ) -> Result<(), UserPoolError> {
        match self.cognito_client.get_pubkey_for_user(username).await {
            Ok(current_public_key) => {
                // Keys don't match; perform rotation and sign out
                if current_public_key != public_key.to_string() {
                    self.cognito_client
                        .replace_user_pubkey(username, public_key.to_string())
                        .await?;
                    self.cognito_client.sign_out_user(username).await?;
                }
            }
            Err(e) => {
                // User doesn't exist; create and confirm it
                if matches!(&e, UserPoolError::AdminGetUserError(f) if f.is_user_not_found_exception())
                {
                    match self
                        .cognito_client
                        .create_new_user(username, public_key.to_string())
                        .await
                    {
                        Ok(_) => {
                            self.cognito_client.confirm_user(username).await?;
                        }
                        Err(f) => {
                            // Swallow UsernameExistsException in case of race due to consistency on Cognito side;
                            // if we've just created the user, it's possible that the above returns UserNotFoundException
                            // only to have this return UsernameExistsException by the time it's called.
                            if !matches!(&f, UserPoolError::AdminCreateUserError(g) if g.is_username_exists_exception())
                            {
                                return Err(f);
                            }
                        }
                    }
                } else {
                    return Err(e);
                }
            }
        }

        Ok(())
    }

    #[instrument(err, skip(self, app_pubkey, hw_pubkey, recovery_pubkey))]
    pub async fn create_or_update_account_users_if_necessary(
        &self,
        account_id: &AccountId,
        app_pubkey: Option<PublicKey>,
        hw_pubkey: Option<PublicKey>,
        recovery_pubkey: Option<PublicKey>,
    ) -> Result<(), UserPoolError> {
        if let Some(app_pubkey) = app_pubkey {
            self.create_or_update_user_if_necessary(
                &CognitoUser::App(account_id.clone()).into(),
                app_pubkey,
            )
            .await?;
        }

        if let Some(hw_pubkey) = hw_pubkey {
            self.create_or_update_user_if_necessary(
                &CognitoUser::Hardware(account_id.clone()).into(),
                hw_pubkey,
            )
            .await?;
        }

        if let Some(recovery_pubkey) = recovery_pubkey {
            self.create_or_update_user_if_necessary(
                &CognitoUser::Recovery(account_id.clone()).into(),
                recovery_pubkey,
            )
            .await?;
        }

        Ok(())
    }

    #[instrument(err, skip(self, username))]
    pub async fn is_existing_user(
        &self,
        username: &CognitoUsername,
    ) -> Result<bool, UserPoolError> {
        self.cognito_client.is_existing_user(username).await
    }

    #[instrument(err, skip(self))]
    pub async fn initiate_auth_for_user(
        &self,
        user: CognitoUser,
        pubkeys_to_account: &PubkeysToAccount,
    ) -> Result<AuthChallenge, UserPoolError> {
        // TODO: Safe to remove this block after W-5688 is resolved
        self.create_or_update_account_users_if_necessary(
            &pubkeys_to_account.id,
            pubkeys_to_account.application_auth_pubkey,
            pubkeys_to_account.hardware_auth_pubkey,
            pubkeys_to_account.recovery_auth_pubkey,
        )
        .await?;
        self.cognito_client
            .initiate_auth_for_user(&user.into())
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
    pub async fn get_pubkeys_for_account(
        &self,
        account_id: AccountId,
    ) -> Result<(Option<String>, Option<String>, Option<String>), UserPoolError> {
        let app_username: CognitoUsername = CognitoUser::App(account_id.clone()).into();
        let app_pubkey = match self.cognito_client.get_pubkey_for_user(&app_username).await {
            Ok(pubkey) => Some(pubkey),
            Err(e) => {
                if matches!(&e, UserPoolError::AdminGetUserError(f) if f.is_user_not_found_exception())
                {
                    None
                } else {
                    return Err(e);
                }
            }
        };

        let hw_username: CognitoUsername = CognitoUser::Hardware(account_id.clone()).into();
        let hw_pubkey = match self.cognito_client.get_pubkey_for_user(&hw_username).await {
            Ok(pubkey) => Some(pubkey),
            Err(e) => {
                if matches!(&e, UserPoolError::AdminGetUserError(f) if f.is_user_not_found_exception())
                {
                    None
                } else {
                    return Err(e);
                }
            }
        };

        let recovery_username: CognitoUsername = CognitoUser::Recovery(account_id.clone()).into();
        let recovery_pubkey = match self
            .cognito_client
            .get_pubkey_for_user(&recovery_username)
            .await
        {
            Ok(pubkey) => Some(pubkey),
            Err(e) => {
                if matches!(&e, UserPoolError::AdminGetUserError(f) if f.is_user_not_found_exception())
                {
                    None
                } else {
                    return Err(e);
                }
            }
        };

        if matches!(
            (&app_pubkey, &hw_pubkey, &recovery_pubkey),
            (None, None, None)
        ) {
            return Err(UserPoolError::NonExistentUser);
        }

        Ok((app_pubkey, hw_pubkey, recovery_pubkey))
    }

    #[instrument(err, skip(self))]
    pub async fn sign_out_user(&self, username: &CognitoUsername) -> Result<(), UserPoolError> {
        self.cognito_client.sign_out_user(username).await
    }

    #[instrument(err, skip(self))]
    pub async fn is_access_token_revoked(
        &self,
        access_token: String,
    ) -> Result<bool, UserPoolError> {
        self.cognito_client
            .is_access_token_revoked(access_token)
            .await
    }
}

#[derive(Clone)]
pub struct FakeCognitoConnection {
    user_keys: Arc<RwLock<HashMap<String, PublicKey>>>,
    session_challenges: Arc<RwLock<HashMap<String, String>>>,
    access_tokens: Arc<RwLock<HashMap<String, HashSet<String>>>>,
    refresh_tokens: Arc<RwLock<HashMap<String, String>>>,
    revoked_access_tokens: Arc<RwLock<HashSet<String>>>,
}

impl Default for FakeCognitoConnection {
    fn default() -> Self {
        let mut user_keys = HashMap::new();
        // insert test keys from server/src/key_proof/test_utils.rs:12 used in unit tests

        let app_public_key = PublicKey::from_str(
            "02b98a7fb8cc007048625b6446ad49a1b3a722df8c1ca975b87160023e14d19097",
        )
        .expect("could not parse static pubkey");

        let hw_public_key = PublicKey::from_str(
            "0381aaadc8a5e83f4576df823cf22a5b1969cf704a0d5f6f68bd757410c9917aac",
        )
        .expect("could not parse static pubkey");

        let recovery_public_key = PublicKey::from_str(
            "0381aaadc8a5e83f4576df823cf22a5b1969cf704a0d5f6f68bd757410c9917aac",
        )
        .expect("could not parse static pubkey");

        user_keys.insert(
            "urn:wallet-account:000000000000000000000000000-app".to_string(),
            app_public_key,
        );
        user_keys.insert(
            "urn:wallet-account:000000000000000000000000000-hardware".to_string(),
            hw_public_key,
        );
        user_keys.insert(
            "urn:wallet-account:000000000000000000000000000-recovery".to_string(),
            recovery_public_key,
        );

        Self {
            user_keys: Arc::new(RwLock::new(user_keys)),
            session_challenges: Arc::new(RwLock::new(HashMap::new())),
            access_tokens: Arc::new(RwLock::new(HashMap::new())),
            refresh_tokens: Arc::new(RwLock::new(HashMap::new())),
            revoked_access_tokens: Arc::new(RwLock::new(HashSet::new())),
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
    async fn create_new_user(
        &self,
        username: &CognitoUsername,
        public_key: String,
    ) -> Result<String, UserPoolError> {
        let mut user_keys = self.user_keys.write().map_err(|_| {
            UserPoolError::CreateUserError("Could not get lock on in-mem user database".to_string())
        })?;

        let username_str = username.to_string();
        if user_keys.contains_key(&username_str) {
            return Err(UserPoolError::CreateUserError(
                "User already exists".to_string(),
            ));
        }

        let new_key = PublicKey::from_str(&public_key)
            .map_err(|e| UserPoolError::CreateUserError(e.to_string()))?;

        user_keys.insert(username_str.clone(), new_key);
        Ok(username_str)
    }

    async fn is_existing_user(&self, username: &CognitoUsername) -> Result<bool, UserPoolError> {
        let cognito_user =
            CognitoUser::from_str(username.as_ref()).expect("Valid Cognito username");
        let cognito_username = Into::<CognitoUsername>::into(cognito_user.clone()).to_string();
        let user_keys = self.user_keys.read().map_err(|_| {
            UserPoolError::CreateUserError("Could not get lock on in-mem user database".to_string())
        })?;
        Ok(user_keys.contains_key(&cognito_username))
    }

    async fn confirm_user(&self, _username: &CognitoUsername) -> Result<(), UserPoolError> {
        Ok(())
    }

    async fn replace_user_pubkey(
        &self,
        username: &CognitoUsername,
        public_key: String,
    ) -> Result<(), UserPoolError> {
        let mut user_keys = self.user_keys.write().map_err(|_| {
            UserPoolError::CreateUserError("Could not get lock on in-mem user database".to_string())
        })?;
        let new_key = PublicKey::from_str(&public_key)
            .map_err(|e| UserPoolError::CreateUserError(e.to_string()))?;
        user_keys.insert(username.to_string(), new_key);
        Ok(())
    }

    async fn initiate_auth_for_user(
        &self,
        username: &CognitoUsername,
    ) -> Result<AuthChallenge, UserPoolError> {
        let challenge = get_64_random_hex_bytes_for_tests();
        let session = get_64_random_hex_bytes_for_tests();

        let mut session_challenges = self.session_challenges.write().map_err(|_| {
            UserPoolError::InitiateAuthError("Could not write to session store".to_string())
        })?;
        session_challenges.insert(session.to_string(), challenge.to_string());

        Ok(AuthChallenge {
            username: username.clone(),
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
        let cognito_user =
            CognitoUser::from_str(username.as_str()).map_err(|_| UserPoolError::NonExistentUser)?;

        let access_token = get_test_access_token_for_cognito_user(&cognito_user);

        let mut access_token_store = self.access_tokens.write().unwrap();
        match access_token_store.get_mut(username.as_str()) {
            Some(access_tokens) => {
                access_tokens.insert(access_token.clone());
            }
            None => {
                access_token_store.insert(username, HashSet::from([access_token.clone()]));
            }
        }

        Ok(AuthTokens {
            access_token,
            refresh_token,
            expires_in: TEST_EXPIRES_IN_SECONDS,
        })
    }

    async fn respond_to_auth_challenge(
        &self,
        username: &CognitoUsername,
        session: String,
        challenge_response: String,
    ) -> Result<AuthTokens, UserPoolError> {
        let secp: Secp256k1<secp256k1::All> = Secp256k1::new();

        let answer_correct = {
            let keystore = self.user_keys.read().map_err(|_| {
                UserPoolError::InitiateAuthError("Poisoned lock on in-memory userpool".to_string())
            })?;
            let public_key = keystore
                .get(username.as_ref())
                .ok_or(UserPoolError::NonExistentUser)?;
            let session_store = self.session_challenges.read().unwrap();
            let challenge = session_store
                .get(&session)
                .ok_or(UserPoolError::InvalidSession)?;
            let message = Message::from_hashed_data::<sha256::Hash>(challenge.as_bytes());

            if let Ok(signature) = Signature::from_str(&challenge_response) {
                secp.verify_ecdsa(&message, &signature, public_key).is_ok()
            } else {
                false
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

            let access_token = get_test_access_token_for_cognito_user(
                &CognitoUser::from_str(&username_str)
                    .map_err(|_| UserPoolError::NonExistentUser)?,
            );

            let mut access_token_store = self.access_tokens.write().unwrap();
            match access_token_store.get_mut(&username_str) {
                Some(access_tokens) => {
                    access_tokens.insert(access_token.clone());
                }
                None => {
                    access_token_store.insert(username_str, HashSet::from([access_token.clone()]));
                }
            }

            Ok(AuthTokens {
                access_token,
                refresh_token,
                expires_in: TEST_EXPIRES_IN_SECONDS,
            })
        } else {
            Err(UserPoolError::InvalidChallengeResponse)
        }
    }

    async fn get_pubkey_for_user(
        &self,
        username: &CognitoUsername,
    ) -> Result<String, UserPoolError> {
        let user_keys = self
            .user_keys
            .read()
            .map_err(|_| UserPoolError::GetUserPubkeyError)?;
        let public_key =
            user_keys
                .get(username.as_ref())
                .ok_or(UserPoolError::AdminGetUserError(
                    AdminGetUserError::UserNotFoundException(
                        UserNotFoundExceptionBuilder::default().build(),
                    ),
                ))?;
        Ok(public_key.to_string())
    }

    async fn sign_out_user(&self, username: &CognitoUsername) -> Result<(), UserPoolError> {
        let mut access_tokens_store = self.access_tokens.write().unwrap();
        let mut revoked_access_tokens_store = self.revoked_access_tokens.write().unwrap();
        let access_tokens = access_tokens_store
            .remove(username.as_ref())
            .unwrap_or_default();
        revoked_access_tokens_store.extend(access_tokens);
        Ok(())
    }

    async fn is_access_token_revoked(&self, access_token: String) -> Result<bool, UserPoolError> {
        Ok(self
            .revoked_access_tokens
            .read()
            .unwrap()
            .contains(&access_token))
    }
}

fn get_64_random_hex_bytes_for_tests() -> String {
    let mut rng = rand::thread_rng();
    let mut the_bytes: [u8; 64] = [0; 64];
    rng.fill(&mut the_bytes);
    hex::encode(the_bytes)
}
