use anyhow::Result;
use bdk::{
    blockchain::{log_progress, ElectrumBlockchain},
    KeychainKind,
};
use rustify::blocking::clients::reqwest::Client;
use sled::Db;

use crate::{
    cache::FromCache,
    db::transactions::FromDatabase,
    entities::{Account, SignerHistory},
};

pub fn utxos(client: &Client, db: &Db, blockchain: ElectrumBlockchain) -> Result<()> {
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

    let utxos = wallet.list_unspent()?;
    let val_width = utxos
        .iter()
        .max_by_key(|u| u.txout.value)
        .map(|u| u.txout.value as f64)
        .map(|f| f.log10() as usize)
        .map(|u| u + 1)
        .unwrap_or_default();
    for utxo in utxos {
        let keychain = match utxo.keychain {
            KeychainKind::External => "spending",
            KeychainKind::Internal => "change",
        };
        println!(
            "UTXO with value {:>val_width$} from tx {} index {} in the {} keychain",
            utxo.txout.value, utxo.outpoint.txid, utxo.outpoint.vout, keychain
        );
    }

    Ok(())
}
