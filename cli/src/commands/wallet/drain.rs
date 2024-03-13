use anyhow::Result;
use bdk::{
    bitcoin::Address,
    blockchain::{Blockchain, ElectrumBlockchain},
    FeeRate,
};
use rustify::blocking::clients::reqwest::Client;
use sled::Db;

use crate::{
    cache::FromCache,
    db::transactions::FromDatabase,
    entities::{Account, SignerHistory},
};

pub fn drain(
    client: &Client,
    db: &Db,
    blockchain: ElectrumBlockchain,
    recipient: Address,
) -> Result<()> {
    let account = Account::from_cache(client, db)?;
    let signers = SignerHistory::from_database(db)?;
    let wallet =
        signers
            .active
            .wallet(&account, db, Some(&signers.active.hardware.sign_context()?))?;

    let mut builder = wallet.build_tx();
    builder
        .drain_wallet()
        .drain_to(recipient.script_pubkey())
        .fee_rate(FeeRate::default_min_relay_fee())
        .enable_rbf();
    let (mut psbt, _) = builder.finish()?;

    let finalised = wallet.sign(&mut psbt, Default::default())?;
    assert!(finalised, "transaction wasn't finalised?!");

    let transaction = psbt.extract_tx();
    blockchain.broadcast(&transaction)?;
    println!("{}", transaction.txid());

    Ok(())
}
