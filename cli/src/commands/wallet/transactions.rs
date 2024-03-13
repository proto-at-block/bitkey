use anyhow::Result;
use bdk::blockchain::{log_progress, ElectrumBlockchain};
use rustify::blocking::clients::reqwest::Client;
use time::OffsetDateTime;

use crate::{
    cache::FromCache,
    db::transactions::FromDatabase,
    entities::{Account, SignerHistory},
};

pub fn transactions(client: &Client, db: &sled::Db, blockchain: ElectrumBlockchain) -> Result<()> {
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

    for details in wallet.list_transactions(false)? {
        if let Some(time) = details.confirmation_time {
            // TODO: investigate why the BlockTime.timestamp is u64
            print!(
                "[{}]",
                OffsetDateTime::from_unix_timestamp(time.timestamp.try_into()?)?
            );
        } else {
            print!("[UNCONFIRMED]")
        }

        if details.received > 0 {
            print!(" received: {}", details.received)
        }

        if details.sent > 0 {
            print!(" sent: {}", details.sent)
        }

        if let Some(fee) = details.fee {
            if fee > 0 {
                print!(" fee: {fee}")
            }
        }

        print!(" [{}]", details.txid);

        println!();
    }

    Ok(())
}
