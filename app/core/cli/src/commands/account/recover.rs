use anyhow::{Context, Result};
use rustify::blocking::clients::reqwest::Client;
use sled::Db;
use tokio::runtime::Runtime;

use crate::{
    cache::FromCache,
    commands::account::authenticate::authenticate_with_signer,
    db::transactions::{FromDatabase, ToDatabase},
    entities::{Account, SignerHistory},
    requests::{helper::EndpointExt, HardwareAuthenticationRequest},
    signers::Authentication,
};

pub(crate) fn recover(client: &Client, db: &Db, auth_client_id: &str) -> Result<()> {
    let signer = SignerHistory::from_database(db)
        .context("no paired signers found; please `pair` first")?
        .active
        .hardware;
    let context = signer.sign_context()?;

    let response = HardwareAuthenticationRequest {
        hw_auth_pubkey: signer.public_key(),
    }
    .exec_unauthenticated(client)?;

    let account_id = response.account_id;
    println!("{}", account_id);

    Runtime::new()?
        .block_on(authenticate_with_signer(
            auth_client_id,
            &account_id,
            &signer,
            &context,
        ))?
        .to_database(db)?;

    Account::from_database(db)
        .unwrap_or(Account {
            id: account_id,
            keysets: vec![],
        })
        .to_database(db)?;
    Account::from_cache(client, db)?;

    Ok(())
}
