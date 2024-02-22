use anyhow::Result;
use bdk::wallet::AddressIndex;
use qrcode::{render::unicode, QrCode};
use rustify::blocking::clients::reqwest::Client;
use sled::Db;

use crate::{
    cache::FromCache,
    db::transactions::FromDatabase,
    entities::{Account, SignerHistory},
};

pub fn receive(client: &Client, db: &Db) -> Result<()> {
    let account = Account::from_cache(client, db)?;
    let wallet = SignerHistory::from_database(db)?
        .active
        .wallet(&account, db, None)?;

    let address = wallet.get_address(AddressIndex::New)?;
    println!("{address}");

    let image = QrCode::new(address.to_qr_uri())?
        .render::<unicode::Dense1x2>()
        .dark_color(unicode::Dense1x2::Light)
        .light_color(unicode::Dense1x2::Dark)
        .build();
    println!("{image}");

    Ok(())
}
