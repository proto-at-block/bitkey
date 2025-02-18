use serde::{Deserialize, Serialize};
use time::{Duration, OffsetDateTime};

use bdk_utils::bdk::bitcoin::psbt::Psbt;
use bdk_utils::bdk::bitcoin::Txid;
use types::serde::{deserialize_ts, serialize_ts};

use crate::util::MobilepayDatetimeError;

const RETENTION_HOURS: i64 = 24; // 1 day

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct CachedPsbtTxid {
    pub txid: Txid,
    /// The unix epoch time in seconds at which this record will be deleted from the database
    #[serde(serialize_with = "serialize_ts", deserialize_with = "deserialize_ts")]
    pub expiring_at: OffsetDateTime,
}

impl CachedPsbtTxid {
    pub fn try_new(psbt: Psbt) -> Result<Self, MobilepayDatetimeError> {
        Ok(Self {
            txid: psbt.unsigned_tx.txid(),
            expiring_at: OffsetDateTime::now_utc()
                .checked_add(Duration::hours(RETENTION_HOURS))
                .ok_or_else(|| {
                    MobilepayDatetimeError::DateMathError(format!(
                        "{} + {RETENTION_HOURS} hours",
                        OffsetDateTime::now_utc()
                    ))
                })?,
        })
    }
}
