use account::entities::{Touchpoint, TouchpointPlatform};
use serde::{Deserialize, Serialize};
use strum_macros::{Display, EnumString};
use time::macros::format_description;
use time::serde::rfc3339;
use time::OffsetDateTime;
use time::{format_description::FormatItem, Duration};
use types::account::identifiers::{AccountId, TouchpointId};

use crate::{
    identifiers::NotificationId, DeliveryStatus, NotificationError, NotificationMessage,
    NotificationPayload, NotificationPayloadType,
};

pub type NotificationCompositeKey = (AccountId, NotificationId);

pub const EXECUTION_DATE_FORMAT: &[FormatItem<'_>] = format_description!("[year]-[month]-[day]");
pub const EXECUTION_TIME_FORMAT: &[FormatItem<'_>] =
    format_description!("[hour]:[minute]:[second]:[subsecond]");

#[derive(Debug, Clone, Copy, EnumString, Display, PartialEq, Eq, Hash)]
#[strum(serialize_all = "lowercase")]
pub enum NotificationType {
    Scheduled,
    Customer,
}

#[derive(Debug)]
pub enum Notification {
    Scheduled(ScheduledNotification),
    Customer(CustomerNotification),
}

#[derive(Serialize, Deserialize, Clone, Debug)]
pub struct NotificationSchedule {
    pub interval: Duration,
    pub end_date_time: Option<OffsetDateTime>,
    #[serde(default)]
    pub jitter: Option<Duration>, // Deviation from the scheduled execution due to intentional jitter
}

#[derive(Serialize, Deserialize, Clone, Debug)]
pub struct ScheduledNotification {
    #[serde(rename = "partition_key")]
    pub account_id: AccountId, // Used to identify which account is receiving the notification
    #[serde(rename = "sort_key")]
    pub unique_id: NotificationId,
    pub sharded_execution_date: String, // Partition Key
    pub execution_time: String,         // Sort Key
    #[serde(with = "rfc3339")]
    pub execution_date_time: OffsetDateTime, // Combination of the above two fields
    pub payload_type: NotificationPayloadType, // This is used to figure out how to deserialize the payload type
    pub payload: NotificationPayload,          // JSON blob for the notification
    pub delivery_status: DeliveryStatus,       // Status when propogating through our systems
    #[serde(with = "rfc3339")]
    pub created_at: OffsetDateTime,
    #[serde(with = "rfc3339")]
    pub updated_at: OffsetDateTime,
    #[serde(default)]
    pub schedule: Option<NotificationSchedule>,
}

impl From<ScheduledNotification> for Notification {
    fn from(n: ScheduledNotification) -> Self {
        Notification::Scheduled(n)
    }
}

impl ScheduledNotification {
    pub fn composite_key(&self) -> NotificationCompositeKey {
        (self.account_id.clone(), self.unique_id)
    }
}

impl TryFrom<ScheduledNotification> for NotificationMessage {
    type Error = NotificationError;

    fn try_from(n: ScheduledNotification) -> Result<Self, Self::Error> {
        NotificationMessage::try_from((n.composite_key(), n.payload_type, n.payload))
    }
}

#[derive(Deserialize, Serialize, Clone, Debug, PartialEq, Eq, Hash)]
#[serde(tag = "type")]
pub enum NotificationTouchpoint {
    Email {
        touchpoint_id: TouchpointId,
    },
    Phone {
        touchpoint_id: TouchpointId,
    },
    Push {
        platform: TouchpointPlatform,
        device_token: String,
    },
    Fake,
}

impl From<Touchpoint> for NotificationTouchpoint {
    fn from(t: Touchpoint) -> Self {
        match t {
            Touchpoint::Email { id, .. } => NotificationTouchpoint::Email { touchpoint_id: id },
            Touchpoint::Phone { id, .. } => NotificationTouchpoint::Phone { touchpoint_id: id },
            Touchpoint::Push {
                platform,
                device_token,
                arn: _,
            } => NotificationTouchpoint::Push {
                platform,
                device_token,
            },
        }
    }
}

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct CustomerNotification {
    #[serde(rename = "partition_key")]
    pub account_id: AccountId, // Partition Key
    #[serde(rename = "sort_key")]
    pub unique_id: NotificationId, // Sort Key
    pub touchpoint: NotificationTouchpoint,
    pub payload_type: NotificationPayloadType,
    pub delivery_status: DeliveryStatus, // Status when propogating through our systems
    #[serde(with = "rfc3339")]
    pub created_at: OffsetDateTime,
    #[serde(with = "rfc3339")]
    pub updated_at: OffsetDateTime,
}

impl CustomerNotification {
    pub fn composite_key(&self) -> NotificationCompositeKey {
        (self.account_id.clone(), self.unique_id)
    }
}

impl From<CustomerNotification> for Notification {
    fn from(n: CustomerNotification) -> Self {
        Notification::Customer(n)
    }
}

#[cfg(test)]
mod tests {
    use std::str::FromStr;

    use time::{Duration, OffsetDateTime};
    use types::account::identifiers::AccountId;

    use crate::entities::ScheduledNotification;
    use crate::identifiers::NotificationId;
    use crate::{DeliveryStatus, NotificationPayload, NotificationPayloadType};

    #[test]
    fn test_notification_composite_key() {
        let t = OffsetDateTime::UNIX_EPOCH + Duration::microseconds(10);

        let account_id = AccountId::from_str("urn:wallet-account:00000000000000000000000000")
            .expect("Valid AccountId");
        let unique_id = NotificationId::from_str("scheduled-01H4661RMJDM407YDJ63BHAJ2R")
            .expect("Valid NotificationId");
        let n = ScheduledNotification {
            account_id: account_id.clone(),
            unique_id,
            sharded_execution_date: "1970-1-1:0".to_owned(),
            execution_time: "0:0:0:00001".to_owned(),
            execution_date_time: t,
            payload_type: NotificationPayloadType::TestPushNotification,
            payload: NotificationPayload::default(),
            delivery_status: DeliveryStatus::New,
            created_at: t,
            updated_at: t,
            schedule: None,
        };

        let (expected_account_id, expected_unique_id) = n.composite_key();
        assert_eq!(account_id, expected_account_id);
        assert_eq!(unique_id, expected_unique_id);
    }
}
