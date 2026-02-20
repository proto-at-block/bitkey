use anyhow::Result;
use bdk_electrum::{electrum_client::Client as ElectrumClient, BdkElectrumClient};
use bdk_wallet::KeychainKind;
use rustify::blocking::clients::reqwest::Client;
use sled::Db;

use crate::{
    cache::FromCache,
    db::transactions::FromDatabase,
    entities::{Account, SignerHistory},
};

pub fn utxos(
    client: &Client,
    db: &Db,
    blockchain: &BdkElectrumClient<ElectrumClient>,
) -> Result<()> {
    let account = Account::from_cache(client, db)?;
    let mut wallet = SignerHistory::from_database(db)?
        .active
        .wallet(&account, db, None)?;

    let request = wallet.start_full_scan();
    let update = blockchain.full_scan(request, 100, 10, true)?;
    wallet.apply_update(update)?;

    let utxos: Vec<_> = wallet.list_unspent().collect();
    let val_width = utxos
        .iter()
        .map(|u| u.txout.value.to_string().len())
        .max()
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
