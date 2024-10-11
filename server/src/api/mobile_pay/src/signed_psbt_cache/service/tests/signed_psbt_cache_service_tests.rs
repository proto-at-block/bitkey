use std::str::FromStr;

use bdk_utils::bdk::bitcoin::absolute::LockTime;
use bdk_utils::bdk::bitcoin::psbt::Psbt;
use bdk_utils::bdk::bitcoin::{Address, Transaction, TxOut};
use database::ddb;
use database::ddb::{Connection, Repository};
use http_server::config;

use crate::signed_psbt_cache::repository::SignedPsbtCacheRepository;
use crate::signed_psbt_cache::service::Service;

#[tokio::test]
async fn test_fetch_base64_serialized_psbt() {
    // arrange
    let conn = connection().await;
    let repo = SignedPsbtCacheRepository::new(conn.clone());
    let service = Service::new(repo.clone());
    let psbt = construct_psbt();

    service.put(psbt.clone()).await.unwrap();

    // act
    let cached_psbt = service.get(psbt.unsigned_tx.txid()).await.unwrap().unwrap();

    // assert
    assert_eq!(cached_psbt.txid, psbt.unsigned_tx.txid());
    assert_eq!(cached_psbt.psbt, psbt);
}

async fn connection() -> Connection {
    config::extract::<ddb::Config>(Some("test"))
        .unwrap()
        .to_connection()
        .await
}

fn construct_psbt() -> Psbt {
    let payee_script_pubkey = Address::from_str("bc1qvh30c5k24q4z2h6e88tvsv7x3xyj7m4g37e498")
        .unwrap()
        .assume_checked()
        .script_pubkey();
    let payee_amount_sats: u64 = 100_000;
    Psbt::from_unsigned_tx(Transaction {
        version: 0,
        lock_time: LockTime::ZERO,
        input: Vec::new(),
        output: vec![TxOut {
            value: payee_amount_sats,
            script_pubkey: payee_script_pubkey,
        }],
    })
    .unwrap()
}
