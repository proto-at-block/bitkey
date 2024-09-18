use std::{
    fmt::{self, Display, Formatter},
    str::FromStr,
};

use external_identifier::ExternalIdentifier;
use serde::{Deserialize, Serialize};
use strum_macros::{Display, EnumIter};
use ulid::Ulid;
use urn::Urn;
use utoipa::ToSchema;

#[derive(Serialize, Deserialize, Debug, Clone, PartialEq, Eq, Hash)]
pub struct PrivilegedActionInstanceId(urn::Urn);

impl FromStr for PrivilegedActionInstanceId {
    type Err = urn::Error;

    fn from_str(s: &str) -> Result<Self, Self::Err> {
        Ok(Urn::from_str(s)?.into())
    }
}

impl From<urn::Urn> for PrivilegedActionInstanceId {
    fn from(urn: urn::Urn) -> Self {
        Self(urn)
    }
}

impl ExternalIdentifier<Ulid> for PrivilegedActionInstanceId {
    fn namespace() -> &'static str {
        "privileged-action-inst"
    }
}

impl PrivilegedActionInstanceId {
    pub fn gen() -> Result<Self, external_identifier::Error> {
        Self::new(Ulid::new())
    }
}

impl Display for PrivilegedActionInstanceId {
    fn fmt(&self, f: &mut Formatter) -> fmt::Result {
        write!(f, "{}", self.0)
    }
}

#[derive(
    Serialize, Deserialize, Debug, Clone, ToSchema, PartialEq, Eq, Hash, Display, EnumIter,
)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum PrivilegedActionType {
    ConfigurePrivilegedActionDelays,
    ActivateTouchpoint,
}

#[derive(Serialize, Deserialize, Debug, Clone, ToSchema, PartialEq, Eq)]
pub struct PrivilegedActionDelayDuration {
    pub privileged_action_type: PrivilegedActionType,
    pub delay_duration_secs: usize,
}
