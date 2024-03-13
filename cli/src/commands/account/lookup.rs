use std::str::FromStr;

use anyhow::Result;
use bdk::bitcoin::secp256k1::PublicKey;
use rustify::blocking::clients::reqwest::Client;

use crate::requests::helper::EndpointExt;
use crate::requests::HardwareAuthenticationRequest;

pub fn lookup(client: &Client, hw_auth_pubkey: String) -> Result<()> {
    let response = HardwareAuthenticationRequest {
        hw_auth_pubkey: PublicKey::from_str(&hw_auth_pubkey)?,
    }
    .exec_unauthenticated(client)?;
    println!("{}", response.account_id);

    Ok(())
}
