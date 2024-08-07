use anyhow::{anyhow, bail, Context, Result};
use rustify::blocking::clients::reqwest::Client;
use sled::Db;
use std::str::FromStr;
use tracing::info;

use crate::cache::FromCache;
use crate::db::transactions::FromDatabase;
use crate::entities::{Account, SignerHistory};
use crate::{commands, AccountType};
use bdk::bitcoin::secp256k1::rand;
use bdk::bitcoin::{Address, Network};
use bdk::blockchain::{Blockchain, ElectrumBlockchain};
use bdk::database::MemoryDatabase;
use bdk::template::Bip84;
use bdk::wallet::AddressIndex;
use bdk::{bitcoin, KeychainKind, SignOptions, Wallet};

use tokio::runtime::Runtime;

pub fn end_to_end(
    client: &Client,
    blockchain: ElectrumBlockchain,
    treasury_root_key: &Option<String>,
) -> Result<()> {
    let db = open_database()?;

    info!("Pairing wallet");
    commands::pair::pair(&db, Network::Signet, true)?;

    info!("Creating wallet");
    commands::account::create(client, &db, &AccountType::Classic)?;

    info!("Authenticating to server");
    commands::account::authenticate_with_app_key(client, &db)?;

    info!("Setting up Mobilepay for 100,000,000 sats");
    commands::wallet::setup_mobile_pay(client, &db, 100_000_000)?;

    let treasury_address = fund_wallet_from_treasury(client, &db, &blockchain, treasury_root_key)?;

    info!("spending coins via server-spend");
    commands::wallet::server_send(client, &db, blockchain, treasury_address, 9500)?;

    Ok(())
}

fn open_database() -> Result<Db> {
    let suffix: String = (0..6)
        .map(|_| std::char::from_digit(rand::random::<u32>() % 36, 36).unwrap())
        .collect();
    let db_filename = format!("test-{suffix}.db");

    info!("db filename: {db_filename} (in case you need to look at it later)");
    sled::open(db_filename).context("opening sled database")
}

fn fund_wallet_from_treasury(
    client: &Client,
    db: &Db,
    blockchain: &ElectrumBlockchain,
    treasury_root_key: &Option<String>,
) -> Result<Address> {
    let key_str = parse_or_fetch_key(treasury_root_key)?;

    let key = bitcoin::bip32::ExtendedPrivKey::from_str(&key_str)?;
    let treasury_wallet = Wallet::new(
        Bip84(key, KeychainKind::External),
        Some(Bip84(key, KeychainKind::Internal)),
        Network::Testnet, // TODO: should this be Signet?
        MemoryDatabase::default(),
    )?;

    treasury_wallet.sync(blockchain, Default::default())?;

    let treasury_address = treasury_wallet
        .get_address(AddressIndex::LastUnused)?
        .address;
    let treasury_balance = treasury_wallet.get_balance()?;
    if treasury_balance.confirmed
        + treasury_balance.trusted_pending
        + treasury_balance.untrusted_pending
        < 10000
    {
        bail!("Not enough sats in treasury wallet. Send coins to {treasury_address}");
    }
    info!("The treasury currently has {treasury_balance}. You can top it up at {treasury_address}");

    let account = Account::from_cache(client, db)?;
    let wallet = SignerHistory::from_database(db)?
        .active
        .wallet(&account, db, None)?;
    let deposit_address = wallet.get_address(AddressIndex::New)?.address;
    info!("Receive address for this wallet: {deposit_address}");

    let mut builder = treasury_wallet.build_tx();
    builder.add_recipient(deposit_address.script_pubkey(), 10000);
    let (mut psbt, _) = builder.finish()?;
    treasury_wallet.sign(&mut psbt, SignOptions::default())?;
    let funding_tx = psbt.extract_tx();
    blockchain.broadcast(&funding_tx)?;
    info!("Funding txid (to this wallet): {}", funding_tx.txid());

    // TODO: replace sleep with a loop that checks for the tx to become pending
    info!("Sleeping for 10 seconds to let the transaction propagate");
    std::thread::sleep(std::time::Duration::from_secs(10));

    info!("Syncing wallet");
    wallet.sync(blockchain, Default::default())?;

    Ok(treasury_address)
}

fn parse_or_fetch_key(treasury_root_key: &Option<String>) -> Result<String> {
    Ok(match treasury_root_key {
        Some(k) => k.to_string(),
        None => {
            // get "e2e_signet_key" from aws secrets manager
            Runtime::new()?
                .block_on(async {
                    let aws_config = aws_config::from_env().load().await;
                    let client = aws_sdk_secretsmanager::Client::new(&aws_config);
                    client
                        .get_secret_value()
                        .secret_id("ops/e2e_signet_key")
                        .send()
                        .await
                })?
                .secret_string()
                .ok_or_else(|| anyhow!("Could not get key from secrets manager"))?
                .to_string()
        }
    })
}
