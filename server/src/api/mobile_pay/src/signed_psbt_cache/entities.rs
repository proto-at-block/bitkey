use serde::{Deserialize, Serialize};
use serde_with::{serde_as, DisplayFromStr};
use time::{Duration, OffsetDateTime};

use bdk_utils::bdk::bitcoin::psbt::Psbt;
use bdk_utils::bdk::bitcoin::Txid;
use types::serde::{deserialize_ts, serialize_ts};

use crate::util::MobilepayDatetimeError;

const RETENTION_HOURS: i64 = 24; // 1 day

#[serde_as]
#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct CachedPsbt {
    pub txid: Txid,
    #[serde_as(as = "DisplayFromStr")]
    pub psbt: Psbt,
    /// The unix epoch time in seconds at which this record will be deleted from the database
    #[serde(serialize_with = "serialize_ts", deserialize_with = "deserialize_ts")]
    pub expiring_at: OffsetDateTime,
}

impl CachedPsbt {
    pub fn try_new(psbt: Psbt) -> Result<Self, MobilepayDatetimeError> {
        Ok(Self {
            txid: psbt.unsigned_tx.txid(),
            psbt,
            expiring_at: OffsetDateTime::now_utc()
                .checked_add(Duration::hours(RETENTION_HOURS))
                .ok_or_else(|| {
                    MobilepayDatetimeError::DateMathError(format!(
                        "{} + {RETENTION_HOURS} hours",
                        OffsetDateTime::now_utc()
                    ))
                })
                .expect("Adding {RETENTION_HOURS} to now should always work"),
        })
    }
}

impl From<NonBase64CachedPsbt> for CachedPsbt {
    fn from(non_base64_cached_psbt: NonBase64CachedPsbt) -> Self {
        Self {
            txid: non_base64_cached_psbt.txid,
            psbt: non_base64_cached_psbt.psbt,
            expiring_at: non_base64_cached_psbt.expiring_at,
        }
    }
}

/// For backwards compatibility, we need to support non-base64 encoded psbts
#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct NonBase64CachedPsbt {
    pub txid: Txid,
    pub psbt: Psbt,
    #[serde(serialize_with = "serialize_ts", deserialize_with = "deserialize_ts")]
    pub expiring_at: OffsetDateTime,
}
