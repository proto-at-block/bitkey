use anyhow::{Context, Result};
use bdk::blockchain::ElectrumBlockchain;
use bdk::{bitcoin::Address, blockchain::Blockchain};
use rustify::blocking::clients::reqwest::Client;
use sled::Db;

use crate::cache::FromCache;
use crate::db::transactions::FromDatabase;
use crate::entities::{Account, AuthenticationToken, SignerHistory};
use crate::requests::helper::EndpointExt;
use crate::{commands::wallet::psbt_from, requests::SignTransactionRequest};

pub fn server_send(
    client: &Client,
    db: &Db,
    blockchain: ElectrumBlockchain,
    recipient: Address,
    amount: u64,
) -> Result<()> {
    let account = Account::from_cache(client, db)?;
    let signers = SignerHistory::from_database(db)?;

    let wallet = signers.active.wallet(&account, db, None)?;
    let mut psbt = psbt_from(&wallet, recipient, amount)?;
    let finalised = wallet.sign(&mut psbt, Default::default())?;
    assert!(!finalised, "transaction was finalised?!");

    let response = SignTransactionRequest {
        account_id: account.id,
        psbt: psbt.clone(),
        settings: Default::default(),
    }
    .exec_authenticated(client, &AuthenticationToken::from_database(db)?)?;

    // Don't trust the server too much!
    psbt.combine(response.tx).context("psbt combine error")?;

    let transaction = psbt.extract_tx();
    blockchain.broadcast(&transaction)?;
    println!("{}", transaction.txid());

    Ok(())
}
