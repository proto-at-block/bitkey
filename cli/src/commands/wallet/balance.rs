use anyhow::Result;
use bdk_electrum::{electrum_client::Client as ElectrumClient, BdkElectrumClient};
use rustify::blocking::clients::reqwest::Client;

use crate::{
    cache::FromCache,
    db::transactions::FromDatabase,
    entities::{Account, SignerHistory},
};

pub fn balance(
    client: &Client,
    db: &sled::Db,
    blockchain: &BdkElectrumClient<ElectrumClient>,
) -> Result<()> {
    let account = Account::from_cache(client, db)?;
    let mut wallet = SignerHistory::from_database(db)?
        .active
        .wallet(&account, db, None)?;

    let request = wallet.start_full_scan();
    let update = blockchain.full_scan(request, 100, 10, true)?;
    wallet.apply_update(update)?;

    println!("balance: {}", wallet.balance());

    Ok(())
}
