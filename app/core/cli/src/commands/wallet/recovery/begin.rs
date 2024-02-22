use anyhow::Result;
use rustify::blocking::clients::reqwest::Client;

use crate::{
    db::transactions::FromDatabase,
    entities::{Account, AuthenticationToken, SignerHistory},
    requests::{helper::EndpointExt, AuthKeypairRequest, CreateAccountDelayNotifyRequest, Factor},
    signers::{seed::SeedSigner, Authentication},
};

pub fn lost_app(client: &Client, db: &sled::Db) -> Result<()> {
    let account_id = Account::from_database(db)?.id;
    let signers = SignerHistory::from_database(db)?;
    let transactor = signers.active.hardware.sign_context()?;

    let response = CreateAccountDelayNotifyRequest {
        account_id,
        delay_period_num_sec: Some(60), // TODO: make a plan for testing in production
        lost_factor: Factor::App,
        auth: AuthKeypairRequest {
            app: signers.active.application.public_key(),
            hardware: signers.active.hardware.public_key(),
        },
        verification_code: None,
    }
    .exec_keyproofed(
        client,
        &AuthenticationToken::from_database(db)?,
        None::<&SeedSigner>,
        Some(&signers.active.hardware),
        &transactor,
    )?;

    println!("{response}");

    Ok(())
}
