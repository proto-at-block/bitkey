use anyhow::Result;
use rustify::blocking::clients::reqwest::Client;

use crate::{
    db::transactions::FromDatabase,
    entities::{Account, AuthenticationToken},
    requests::{helper::EndpointExt, GetWalletStatusRequest},
};

pub fn server_status(client: &Client, db: &sled::Db) -> Result<()> {
    let account_id = Account::from_database(db)?.id;
    let response = GetWalletStatusRequest {
        account_id: account_id.clone(),
    }
    .exec_authenticated(client, &AuthenticationToken::from_database(db)?)?;

    println!("Account ID: {}", account_id);

    let keyset = response.active_keyset;
    println!(
        "Active Keyset: {} {:?}",
        keyset.keyset_id, keyset.spending.network
    );
    println!("\tApplication: {}", keyset.spending.app_dpub);
    println!("\tHardware: {}", keyset.spending.hardware_dpub);
    println!("\tServer: {}", keyset.spending.server_dpub);

    print!("{}", response.recovery_status);

    Ok(())
}
