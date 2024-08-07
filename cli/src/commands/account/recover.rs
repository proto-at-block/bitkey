use anyhow::{Context, Result};
use rustify::blocking::clients::reqwest::Client;
use sled::Db;

use crate::{
    cache::FromCache,
    commands::account::authenticate::authenticate_with_signer,
    db::transactions::{FromDatabase, ToDatabase},
    entities::{Account, KeyMaterial, SignerHistory},
    requests::{helper::EndpointExt, HardwareAuthenticationRequest},
    signers::Authentication,
};

pub(crate) fn recover(client: &Client, db: &Db) -> Result<()> {
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

    authenticate_with_signer(client, &signer, &context)?.to_database(db)?;

    Account::from_database(db)
        .unwrap_or(Account {
            id: account_id,
            key_material: KeyMaterial::Keyset(vec![]),
        })
        .to_database(db)?;
    Account::from_cache(client, db)?;

    Ok(())
}
