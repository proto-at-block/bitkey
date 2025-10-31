use serde::{Deserialize, Serialize};
use utoipa::ToSchema;

use crate::currencies::CurrencyCode;

#[derive(Clone, Copy, Debug, Serialize, Deserialize, PartialEq, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct Money {
    pub amount: u64,
    pub currency_code: CurrencyCode,
}
