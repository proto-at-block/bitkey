use anyhow::Result;
use bdk_electrum::{electrum_client::Client as ElectrumClient, BdkElectrumClient};
use bdk_wallet::bitcoin::Amount;
use bdk_wallet::chain::ChainPosition;
use rustify::blocking::clients::reqwest::Client;
use time::OffsetDateTime;

use crate::{
    cache::FromCache,
    db::transactions::FromDatabase,
    entities::{Account, SignerHistory},
};

pub fn transactions(
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

    for details in wallet.transactions() {
        match details.chain_position {
            ChainPosition::Confirmed { anchor, .. } => {
                let timestamp = anchor.confirmation_time;
                print!(
                    "[{}]",
                    OffsetDateTime::from_unix_timestamp(timestamp.try_into()?)?
                );
            }
            ChainPosition::Unconfirmed { .. } => print!("[UNCONFIRMED]"),
        }

        let (sent, received) = wallet.sent_and_received(&details.tx_node.tx);

        if received > Amount::from_sat(0) {
            print!(" received: {received}");
        }

        if sent > Amount::from_sat(0) {
            print!(" sent: {sent}");
        }

        print!(" [{}]", details.tx_node.txid);

        println!();
    }

    Ok(())
}
