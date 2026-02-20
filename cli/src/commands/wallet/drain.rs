use anyhow::Result;
use bdk_electrum::{electrum_client::Client as ElectrumClient, BdkElectrumClient};
use bdk_wallet::bitcoin::{Address, FeeRate};
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
    blockchain: &BdkElectrumClient<ElectrumClient>,
    recipient: Address,
) -> Result<()> {
    let account = Account::from_cache(client, db)?;
    let signers = SignerHistory::from_database(db)?;
    let mut wallet =
        signers
            .active
            .wallet(&account, db, Some(&signers.active.hardware.sign_context()?))?;

    let mut builder = wallet.build_tx();
    builder
        .drain_wallet()
        .drain_to(recipient.script_pubkey())
        .fee_rate(FeeRate::BROADCAST_MIN);
    let mut psbt = builder.finish()?;

    let finalised = wallet.sign(&mut psbt, Default::default())?;
    assert!(finalised, "transaction wasn't finalised?!");

    let transaction = psbt.extract_tx()?;
    blockchain.transaction_broadcast(&transaction)?;
    println!("{}", transaction.compute_txid());

    Ok(())
}
