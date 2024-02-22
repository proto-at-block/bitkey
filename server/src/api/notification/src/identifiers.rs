use std::{fmt, str::FromStr};

use serde::de::Error;
use serde::{Deserialize, Deserializer, Serialize, Serializer};
use ulid::Ulid;

use crate::{entities::NotificationType, NotificationError};

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub struct NotificationId(NotificationType, Ulid);

impl NotificationId {
    fn gen(t: NotificationType) -> Self {
        Self(t, Ulid::new())
    }

    pub fn gen_scheduled() -> Self {
        Self::gen(NotificationType::Scheduled)
    }

    pub fn gen_customer() -> Self {
        Self::gen(NotificationType::Customer)
    }
}

impl fmt::Display for NotificationId {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        write!(f, "{}-{}", self.0, self.1)
    }
}

impl Serialize for NotificationId {
    fn serialize<S>(&self, serializer: S) -> Result<S::Ok, S::Error>
    where
        S: Serializer,
    {
        serializer.serialize_str(&self.to_string())
    }
}

impl<'de> Deserialize<'de> for NotificationId {
    fn deserialize<D>(deserializer: D) -> Result<Self, D::Error>
    where
        D: Deserializer<'de>,
    {
        let s = String::deserialize(deserializer)?;
        NotificationId::from_str(&s).map_err(D::Error::custom)
    }
}

impl FromStr for NotificationId {
    type Err = NotificationError;

    fn from_str(s: &str) -> Result<Self, Self::Err> {
        let components: Vec<&str> = s.split('-').collect();
        if components.len() != 2 {
            return Err(NotificationError::ParseIdentifier);
        }

        let notification_type = NotificationType::from_str(components[0])?;
        let ulid = Ulid::from_string(components[1])?;
        Ok(NotificationId(notification_type, ulid))
    }
}
