use serde::{Deserialize, Serialize};
use time::serde::rfc3339;
use time::OffsetDateTime;

use crate::account::identifiers::AccountId;

use super::shared::{PrivilegedActionInstanceId, PrivilegedActionType};

#[derive(Serialize, Deserialize, Debug, Clone, Copy, PartialEq)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum RecordStatus {
    Pending,
    Canceled,
    Completed,
}

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct DelayAndNotifyRecord {
    pub status: RecordStatus,
    pub cancellation_token: String,
    pub completion_token: String,
    #[serde(with = "rfc3339")]
    pub delay_end_time: OffsetDateTime,
}

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct OutOfBandRecord {
    pub status: RecordStatus,
    pub web_auth_token: String,
    #[serde(with = "rfc3339")]
    pub expiry_time: OffsetDateTime,
}

#[derive(Serialize, Deserialize, Debug, Clone)]
#[serde(
    tag = "authorization_strategy_type",
    rename_all = "SCREAMING_SNAKE_CASE"
)]
pub enum AuthorizationStrategyRecord {
    HardwareProofOfPossession,
    DelayAndNotify(DelayAndNotifyRecord),
    OutOfBand(OutOfBandRecord),
}

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct PrivilegedActionInstanceRecord<T> {
    #[serde(rename = "partition_key")]
    pub id: PrivilegedActionInstanceId,
    pub account_id: AccountId,
    pub privileged_action_type: PrivilegedActionType,
    #[serde(flatten)]
    pub authorization_strategy: AuthorizationStrategyRecord,
    pub request: T,
    #[serde(with = "rfc3339")]
    pub created_at: OffsetDateTime,
    #[serde(with = "rfc3339")]
    pub updated_at: OffsetDateTime,
}

impl<T> PrivilegedActionInstanceRecord<T> {
    #[must_use]
    pub fn new(
        account_id: AccountId,
        privileged_action_type: PrivilegedActionType,
        authorization_strategy: AuthorizationStrategyRecord,
        request: T,
    ) -> Result<Self, external_identifier::Error> {
        let now = OffsetDateTime::now_utc();

        Ok(Self {
            id: PrivilegedActionInstanceId::gen()?,
            account_id,
            privileged_action_type,
            authorization_strategy,
            request,
            created_at: now,
            updated_at: now,
        })
    }

    pub fn get_record_status(&self) -> RecordStatus {
        match &self.authorization_strategy {
            AuthorizationStrategyRecord::DelayAndNotify(dn) => dn.status,
            AuthorizationStrategyRecord::OutOfBand(out_of_band) => out_of_band.status,
            AuthorizationStrategyRecord::HardwareProofOfPossession => RecordStatus::Completed,
        }
    }
}
