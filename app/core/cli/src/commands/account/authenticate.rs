use anyhow::{Context, Result};
use aws_config::meta::region::RegionProviderChain;
use aws_sdk_cognitoidentityprovider::operation::initiate_auth::InitiateAuthOutput;
use aws_sdk_cognitoidentityprovider::operation::respond_to_auth_challenge::RespondToAuthChallengeOutput;
use aws_sdk_cognitoidentityprovider::types::{AuthFlowType, ChallengeNameType};
use aws_sdk_cognitoidentityprovider::Client;
use aws_types::SdkConfig;
use bdk::bitcoin::secp256k1::ecdsa::Signature;
use tokio::runtime::Runtime;
use wca::pcsc::{NullTransactor, Transactor};

use crate::db::transactions::{FromDatabase, ToDatabase};
use crate::entities::{Account, AuthenticationToken, SignerHistory};
use crate::serde_helpers::AccountId;
use crate::signers::Authentication;

pub(crate) fn authenticate_with_app_key(db: &sled::Db, auth_client_id: &str) -> Result<()> {
    Runtime::new()?.block_on(authenticate(db, auth_client_id))
}

async fn authenticate(db: &sled::Db, auth_client_id: &str) -> Result<()> {
    authenticate_with_signer(
        auth_client_id,
        &Account::from_database(db)?.id,
        &SignerHistory::from_database(db)?.active.application,
        &NullTransactor,
    )
    .await?
    .to_database(db)?;

    Ok(())
}

pub(crate) async fn authenticate_with_signer(
    auth_client_id: &str,
    account_id: &AccountId,
    signer: &impl Authentication,
    context: &impl Transactor,
) -> Result<AuthenticationToken> {
    let client = Client::new(&(get_aws_config().await));
    let initial_auth_response = initate_auth(&client, auth_client_id, account_id).await?;
    let initial_auth_session = initial_auth_response
        .session()
        .context("no session from cognito")?;
    let challenge = initial_auth_response
        .challenge_parameters()
        .context("no challenge parameters from cognito")?
        .get("challenge")
        .context("no auth challenge from cognito")?;
    let signed = signer.sign(challenge.as_bytes(), context)?;
    let respond_to_auth_response = respond_to_auth_challenge(
        client,
        auth_client_id,
        initial_auth_session,
        signed,
        account_id,
    )
    .await?;
    let access_token = respond_to_auth_response
        .authentication_result()
        .context("no authentication result from cognito")?
        .access_token()
        .context("did not get access token back from cognito")?;

    Ok(AuthenticationToken(access_token.to_string()))
}

async fn get_aws_config() -> SdkConfig {
    aws_config::from_env()
        .region(RegionProviderChain::default_provider().or_else("us-west-2"))
        .load()
        .await
}

async fn initate_auth(
    client: &Client,
    auth_client_id: &str,
    account_id: &AccountId,
) -> Result<InitiateAuthOutput> {
    let response = client
        .initiate_auth()
        .client_id(auth_client_id)
        .auth_flow(AuthFlowType::CustomAuth)
        .auth_parameters("USERNAME", account_id.to_string())
        .send()
        .await?;
    Ok(response)
}

async fn respond_to_auth_challenge(
    client: Client,
    auth_client_id: &str,
    initial_auth_session: &str,
    signed: Signature,
    account_id: &AccountId,
) -> Result<RespondToAuthChallengeOutput> {
    let response = client
        .respond_to_auth_challenge()
        .client_id(auth_client_id)
        .challenge_name(ChallengeNameType::CustomChallenge)
        .session(initial_auth_session)
        .challenge_responses("ANSWER", signed.to_string())
        .challenge_responses("USERNAME", account_id.to_string())
        .send()
        .await?;
    Ok(response)
}
