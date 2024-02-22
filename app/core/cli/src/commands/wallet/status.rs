use anyhow::Result;
use bdk::{
    miniscript::{Descriptor, DescriptorPublicKey},
    KeychainKind, Wallet,
};
use rustify::blocking::clients::reqwest::Client;

use crate::{
    cache::FromCache,
    db::transactions::FromDatabase,
    entities::{Account, AuthenticationToken, SignerHistory},
};

pub fn status(client: &Client, db: &sled::Db) -> Result<()> {
    let signers = SignerHistory::from_database(db)?;
    println!("Active:");
    println!("{}", indent(&signers.active.to_string()));

    for (index, signer) in signers.inactive.iter().enumerate() {
        println!("Inactive ({index}):");
        println!("{}", indent(&signer.to_string()));
    }

    if let Ok(token) = AuthenticationToken::from_database(db) {
        println!("Authentication Token: {}", token.0);
    }

    if let Ok(account) = Account::from_cache(client, db) {
        println!("{account}");

        let wallet = signers.active.wallet(&account, db, None)?;
        println!("Wallet:");
        println!("{}", indent(&pretty_print_wallet(wallet)));
    }

    Ok(())
}

fn pretty_print_wallet(wallet: Wallet<sled::Tree>) -> String {
    let spending = wallet
        .public_descriptor(KeychainKind::External)
        .expect("no public descriptor")
        .expect("corrupt change descriptor");
    let change = wallet
        .public_descriptor(KeychainKind::Internal)
        .expect("no public descriptor")
        .expect("corrupt change descriptor");

    format!(
        "Spending:\n{}\nChange:\n{}",
        indent(&pretty_print_descriptor(spending)),
        indent(&pretty_print_descriptor(change))
    )
}

fn pretty_print_descriptor(descriptor: Descriptor<DescriptorPublicKey>) -> String {
    descriptor
        .to_string()
        .split(',')
        .collect::<Vec<_>>()
        .join(",\n")
}

fn indent(text: &str) -> String {
    let joiner = "\n\t".to_string();
    let body = text.split('\n').collect::<Vec<_>>().join(&joiner);
    format!("\t{}", body)
}
