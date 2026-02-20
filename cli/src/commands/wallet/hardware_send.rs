use anyhow::{Context, Result};
use bdk_electrum::{electrum_client::Client as ElectrumClient, BdkElectrumClient};
use bdk_wallet::bitcoin::Address;
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
    blockchain: &BdkElectrumClient<ElectrumClient>,
    recipient: Address,
    amount: u64,
) -> Result<()> {
    let account = Account::from_cache(client, db)?;
    let signers =
        SignerHistory::from_database(db).context("no paired signers found; please `pair` first")?;
    let mut wallet =
        signers
            .active
            .wallet(&account, db, Some(&signers.active.hardware.sign_context()?))?;

    let mut psbt = psbt_from(&mut wallet, recipient, amount)?;

    let finalised = wallet.sign(&mut psbt, Default::default())?;
    assert!(finalised, "transaction wasn't finalised?!");

    let transaction = psbt.extract_tx()?;
    blockchain.transaction_broadcast(&transaction)?;
    println!("{}", transaction.compute_txid());

    Ok(())
}
