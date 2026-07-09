//! Types for the `VerifyHardwareSerial` privileged action. The keyset
//! under verification is implicit (= `account.active_spending_keyset()`
//! at confirm time), so there is no stored request type — only the
//! user-submitted [`VerifyHardwareSerialSubmission`].

use serde::{Deserialize, Serialize};
use utoipa::ToSchema;

#[derive(Serialize, Deserialize, Debug, Clone, ToSchema, PartialEq, Eq)]
pub struct VerifyHardwareSerialSubmission {
    pub serial: String,
}
