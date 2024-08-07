use serde::{Deserialize, Serialize};
use time::OffsetDateTime;

use crate::account::identifiers::AccountId;

use super::shared::{PrivilegedActionInstanceId, PrivilegedActionType};

#[derive(Serialize, Deserialize, Debug, Clone)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum DelayAndNotifyStatus {
    Pending,
    Canceled,
    CanceledInContest,
    Completed,
}

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct DelayAndNotifyRecord {
    pub status: DelayAndNotifyStatus,
    pub cancellation_token: String,
    pub completion_token: String,
    pub delay_end_time: OffsetDateTime,
}

#[derive(Serialize, Deserialize, Debug, Clone)]
#[serde(tag = "authorization_strategy_type")]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum AuthorizationStrategyRecord {
    HardwareProofOfPossession,
    DelayAndNotify(DelayAndNotifyRecord),
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
    pub created_at: OffsetDateTime,
    pub updated_at: OffsetDateTime,
}
