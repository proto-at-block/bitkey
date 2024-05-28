use bdk_utils::bdk::{
    bitcoin::{Network, Txid},
    FeeRate,
};
use serde::{Deserialize, Serialize};
use time::{serde::rfc3339, Duration, OffsetDateTime};
use types::serde::{deserialize_ts, serialize_ts};

const RETENTION_DAYS: i64 = 2;

#[derive(Deserialize, Serialize, Debug, Clone, PartialEq, Eq, Hash)]
pub struct TransactionVout {
    #[serde(default)]
    pub scriptpubkey_address: Option<String>,
    pub value: u64,
}

#[derive(Deserialize, Serialize, Debug, Clone)]
pub(crate) struct TransactionResponse {
    txid: Txid,
    vout: Vec<TransactionVout>,
    size: usize,
    weight: u64,
    fee: u64,
}

#[derive(Deserialize, Serialize, Debug, Clone)]
pub struct TransactionRecord {
    #[serde(rename = "tx_id")]
    pub txid: Txid, // Partition Key
    pub network: Network,
    pub received: Vec<TransactionVout>,
    pub fee_rate: f32,
    #[serde(with = "rfc3339")]
    pub first_seen: OffsetDateTime,
    /// The unix epoch time in seconds at which this record will be deleted from the database
    #[serde(serialize_with = "serialize_ts", deserialize_with = "deserialize_ts")]
    pub expiring_at: OffsetDateTime,
}

impl TransactionRecord {
    pub(crate) fn from_mempool_tx(tx: &TransactionResponse, network: Network) -> Self {
        let now: OffsetDateTime = OffsetDateTime::now_utc();
        TransactionRecord {
            txid: tx.txid,
            network,
            received: tx.vout.clone(),
            fee_rate: FeeRate::from_vb(tx.fee, tx.size).as_sat_per_vb(),
            first_seen: now,
            expiring_at: now + Duration::days(RETENTION_DAYS),
        }
    }
}
