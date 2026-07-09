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
    /// Terminal state for out-of-band records whose user-supplied confirmation input
    /// failed to verify too many times (attempts >= max_attempts on the OOB record).
    Failed,
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
    /// Count of failed user-confirmation attempts (e.g. wrong serial typed).
    /// Defaults to 0 for records persisted before this field existed. The
    /// ceiling is the [`DEFAULT_OOB_MAX_ATTEMPTS`] const, not a persisted
    /// field — the limit is policy, never varied per instance.
    #[serde(default)]
    pub attempts: u32,
    /// Timestamp of the most recent verification-email send (initial or
    /// resend), used to enforce the resend cooldown. `None` for records
    /// persisted before this field existed (cooldown not enforced on their
    /// first resend).
    #[serde(default, with = "rfc3339::option")]
    pub last_sent_at: Option<OffsetDateTime>,
    /// Count of resends performed (0 at creation; the initial send is not a
    /// resend). Defaults to 0 for records persisted before this field
    /// existed. Bounded by the [`DEFAULT_OOB_MAX_RESENDS`] const.
    #[serde(default)]
    pub resend_count: u32,
}

/// Maximum failed user-confirmation attempts before an OOB record transitions
/// to `Failed`.
pub const DEFAULT_OOB_MAX_ATTEMPTS: u32 = 5;

/// Maximum verification-email resends allowed per OOB instance.
pub const DEFAULT_OOB_MAX_RESENDS: u32 = 5;

#[derive(Serialize, Deserialize, Debug, Clone)]
#[serde(
    tag = "authorization_strategy_type",
    rename_all = "SCREAMING_SNAKE_CASE"
)]
pub enum AuthorizationStrategyRecord {
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
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    /// Records persisted before the resend fields existed — and records still
    /// carrying the dropped `max_attempts` field — must continue to
    /// deserialize. New fields receive their `#[serde(default)]` values; the
    /// unknown `max_attempts` is ignored.
    #[test]
    fn out_of_band_record_deserializes_legacy_shape_with_defaults() {
        let legacy_json = serde_json::json!({
            "status": "PENDING",
            "web_auth_token": "legacy-token",
            "expiry_time": "2024-01-01T00:00:00Z",
            // Persisted by prior server versions; now an ignored unknown field.
            "max_attempts": 7,
        });

        let record: OutOfBandRecord =
            serde_json::from_value(legacy_json).expect("legacy DDB shape must deserialize");

        assert_eq!(record.attempts, 0);
        assert_eq!(record.last_sent_at, None);
        assert_eq!(record.resend_count, 0);
        assert_eq!(record.status, RecordStatus::Pending);
        assert_eq!(record.web_auth_token, "legacy-token");
    }

    #[test]
    fn out_of_band_record_round_trip_preserves_new_fields() {
        let record = OutOfBandRecord {
            status: RecordStatus::Pending,
            web_auth_token: "tok".to_string(),
            expiry_time: OffsetDateTime::from_unix_timestamp(1_700_000_000).unwrap(),
            attempts: 3,
            last_sent_at: Some(OffsetDateTime::from_unix_timestamp(1_700_000_500).unwrap()),
            resend_count: 2,
        };

        let value = serde_json::to_value(&record).expect("serialize");
        let parsed: OutOfBandRecord = serde_json::from_value(value).expect("deserialize");
        assert_eq!(parsed.attempts, 3);
        assert_eq!(parsed.last_sent_at, record.last_sent_at);
        assert_eq!(parsed.resend_count, 2);
    }
}
