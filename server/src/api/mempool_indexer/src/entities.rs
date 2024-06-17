use std::hash::{Hash, Hasher};

use bdk_utils::bdk::{
    bitcoin::{Network, Txid, Weight},
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
pub struct TransactionRecordStatus {
    confirmed: bool,
}

#[derive(Deserialize, Serialize, Debug, Clone)]
pub(crate) struct TransactionResponse {
    txid: Txid,
    vout: Vec<TransactionVout>,
    size: usize,
    weight: Weight,
    fee: u64,
    status: TransactionRecordStatus,
}

impl TransactionResponse {
    pub(crate) fn is_confirmed(&self) -> bool {
        self.status.confirmed
    }
}

#[derive(Deserialize, Serialize, Debug, Clone, PartialEq)]
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
    pub(crate) fn update_expiry(mut self) -> Self {
        self.expiring_at = OffsetDateTime::now_utc() + Duration::days(RETENTION_DAYS);
        self
    }
}

impl Eq for TransactionRecord {}

impl Hash for TransactionRecord {
    fn hash<H: Hasher>(&self, state: &mut H) {
        self.txid.hash(state);
        self.network.hash(state);
    }
}

impl TransactionRecord {
    pub(crate) fn from_mempool_tx(tx: &TransactionResponse, network: Network) -> Self {
        let now: OffsetDateTime = OffsetDateTime::now_utc();
        TransactionRecord {
            txid: tx.txid,
            network,
            received: tx.vout.clone(),
            fee_rate: FeeRate::from_wu(tx.fee, tx.weight).as_sat_per_vb(),
            first_seen: now,
            expiring_at: now + Duration::days(RETENTION_DAYS),
        }
    }
}
