use anyhow::Result;
use rustify::blocking::client::Client;

use crate::requests::helper::EndpointExt;
use crate::{
    db::transactions::FromDatabase,
    entities::{Account, AuthenticationToken},
    requests::RecoveryStatusRequest,
};

pub fn status_delay_notify(client: &impl Client, db: &sled::Db) -> Result<()> {
    let account_id = Account::from_database(db)?.id;

    let response = RecoveryStatusRequest { account_id }
        .exec_authenticated(client, &AuthenticationToken::from_database(db)?)?;

    println!("{response}");

    Ok(())
}
