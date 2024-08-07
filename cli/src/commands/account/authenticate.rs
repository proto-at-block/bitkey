use anyhow::Result;
use rustify::blocking::clients::reqwest::Client;
use wca::pcsc::{NullTransactor, Transactor};

use crate::db::transactions::{FromDatabase, ToDatabase};
use crate::entities::{AuthenticationToken, SignerHistory};
use crate::requests::helper::EndpointExt;
use crate::requests::{
    AuthRequestKey, ChallengeResponseParameters, GetTokensRequest, InitiateAuthRequest,
};
use crate::signers::Authentication;

pub(crate) fn authenticate_with_app_key(client: &Client, db: &sled::Db) -> Result<()> {
    authenticate(client, db)
}

fn authenticate(client: &Client, db: &sled::Db) -> Result<()> {
    let auth_token = authenticate_with_signer(
        client,
        &SignerHistory::from_database(db)?.active.application,
        &NullTransactor,
    );

    auth_token?.to_database(db)?;

    Ok(())
}

pub(crate) fn authenticate_with_signer(
    client: &Client,
    signer: &impl Authentication,
    context: &impl Transactor,
) -> Result<AuthenticationToken> {
    let initiate_auth_response = InitiateAuthRequest {
        auth_request_key: AuthRequestKey::AppPubkey(signer.public_key()),
    }
    .exec_unauthenticated(client)?;

    let challenge_response = signer.sign(initiate_auth_response.challenge.as_bytes(), context)?;

    let get_tokens_response = GetTokensRequest {
        challenge: Some(ChallengeResponseParameters {
            username: initiate_auth_response.username,
            session: initiate_auth_response.session,
            challenge_response: challenge_response.to_string(),
        }),
        refresh_token: None,
    }
    .exec_unauthenticated(client)?;

    Ok(AuthenticationToken(
        get_tokens_response.access_token.to_string(),
    ))
}
