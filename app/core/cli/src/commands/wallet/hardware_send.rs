use anyhow::{Context, Result};
use bdk::{
    bitcoin::Address,
    blockchain::{Blockchain, ElectrumBlockchain},
};
use rustify::blocking::clients::reqwest::Client;
use sled::Db;

use crate::{
    cache::FromCache,
    commands::wallet::psbt_from,
    db::transactions::FromDatabase,
    entities::{Account, SignerHistory},
};

pub fn hardware_send(
    client: &Client,
    db: &Db,
    blockchain: ElectrumBlockchain,
    recipient: Address,
    amount: u64,
) -> Result<()> {
    let account = Account::from_cache(client, db)?;
    let signers =
        SignerHistory::from_database(db).context("no paired signers found; please `pair` first")?;
    let wallet =
        signers
            .active
            .wallet(&account, db, Some(&signers.active.hardware.sign_context()?))?;

    let mut psbt = psbt_from(&wallet, recipient, amount)?;

    let finalised = wallet.sign(&mut psbt, Default::default())?;
    assert!(finalised, "transaction wasn't finalised?!");

    let transaction = psbt.extract_tx();
    blockchain.broadcast(&transaction)?;
    println!("{}", transaction.txid());

    Ok(())
}
