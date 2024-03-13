use anyhow::Result;
use bdk::blockchain::{log_progress, ElectrumBlockchain};
use rustify::blocking::clients::reqwest::Client;

use crate::{
    cache::FromCache,
    db::transactions::FromDatabase,
    entities::{Account, SignerHistory},
};

pub fn balance(client: &Client, db: &sled::Db, blockchain: ElectrumBlockchain) -> Result<()> {
    let account = Account::from_cache(client, db)?;
    let wallet = SignerHistory::from_database(db)?
        .active
        .wallet(&account, db, None)?;

    wallet.sync(
        &blockchain,
        bdk::SyncOptions {
            progress: Some(Box::new(log_progress())),
        },
    )?;
    println!("balance: {}", wallet.get_balance()?);

    Ok(())
}
