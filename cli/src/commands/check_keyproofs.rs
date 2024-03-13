use std::str::FromStr;

use anyhow::Result;
use aws_sdk_dynamodb::types::AttributeValue;
use bdk::bitcoin::hashes::sha256;
use bdk::bitcoin::secp256k1::ecdsa::Signature;
use bdk::bitcoin::secp256k1::{Message, PublicKey, Secp256k1};

pub fn check_keyproofs(
    account_table: String,
    account_id: String,
    access_token: String,
    app_signature: String,
    hardware_signature: String,
) -> Result<()> {
    let rt = tokio::runtime::Runtime::new()?;

    let item = rt.block_on(async {
        let ddb_client = aws_sdk_dynamodb::Client::new(&aws_config::load_from_env().await);
        ddb_client
            .get_item()
            .table_name(account_table)
            .key("partition_key", AttributeValue::S(account_id.clone()))
            .send()
            .await
    })?;

    let active_auth_keys_id = item
        .item()
        .unwrap()
        .get("active_auth_keys_id")
        .unwrap()
        .as_s()
        .unwrap();
    println!("******************************************************");
    println!("Active auth key id: {}", active_auth_keys_id);
    println!("******************************************************");

    let auth_keys = item
        .item()
        .unwrap()
        .get("auth_keys")
        .unwrap()
        .as_m()
        .unwrap();
    println!("AUTH KEYS");

    let keys = auth_keys.get(active_auth_keys_id).unwrap().as_m().unwrap();
    let app_pubkey = keys.get("app_pubkey").unwrap().as_s().unwrap().to_owned();
    let hardware_pubkey = keys
        .get("hardware_pubkey")
        .unwrap()
        .as_s()
        .unwrap()
        .to_owned();
    println!("app public key: {app_pubkey}");
    println!("hardware public key: {hardware_pubkey}");
    println!("******************************************************");
    println!("KEYPROOF SIGNATURE CHECK");
    println!(
        "Valid signature with app key: {}",
        verify_signature(&app_signature, access_token.clone(), app_pubkey)
    );
    println!(
        "Valid signature with hardware key: {}",
        verify_signature(&hardware_signature, access_token, hardware_pubkey)
    );
    println!("******************************************************");
    Ok(())
}

fn verify_signature(signature: &str, message: String, pubkey: String) -> bool {
    let secp = Secp256k1::verification_only();
    let message = Message::from_hashed_data::<sha256::Hash>(message.as_bytes());
    let Ok(signature) = Signature::from_str(signature) else {
        return false;
    };
    let Ok(pubkey) = PublicKey::from_str(&pubkey) else {
        return false;
    };
    secp.verify_ecdsa(&message, &signature, &pubkey).is_ok()
}
