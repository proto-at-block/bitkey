use anyhow::Result;
use rustify::blocking::clients::reqwest::Client;
use sled::Db;

use crate::{
    db::transactions::FromDatabase,
    entities::{Account, SignerHistory},
    requests::{helper::EndpointExt, CompleteDelayNotifyRequest},
};

pub(crate) fn complete_delay_notify(client: &Client, db: &Db) -> Result<()> {
    let account_id = Account::from_database(db)?.id;
    let signers = SignerHistory::from_database(db)?;
    let context = signers.active.hardware.sign_context()?;

    CompleteDelayNotifyRequest::new(
        account_id,
        &signers.active.application,
        &signers.active.hardware,
        &context,
    )?
    .exec_unauthenticated(client)?;

    Ok(())
}
