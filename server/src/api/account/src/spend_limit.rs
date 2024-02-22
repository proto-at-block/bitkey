use serde::{Deserialize, Serialize};
use time::UtcOffset;
use types::currencies::CurrencyCode;
use types::currencies::CurrencyCode::USD;
use utoipa::ToSchema;

#[derive(Clone, Debug, Serialize, Deserialize, PartialEq, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct SpendingLimit {
    // Since old clients do not POST with this value populated, we will always deserialize to `true`
    // if `active` is not present in the request.
    #[serde(default = "default_true")]
    pub active: bool,
    pub amount: Money,
    pub time_zone_offset: UtcOffset,
}

fn default_true() -> bool {
    true
}

impl Default for SpendingLimit {
    fn default() -> Self {
        SpendingLimit {
            active: false,
            amount: Money {
                amount: 0,
                currency_code: USD,
            },
            time_zone_offset: UtcOffset::UTC,
        }
    }
}

#[derive(Clone, Debug, Serialize, Deserialize, PartialEq, ToSchema)]
#[serde(rename_all = "snake_case")]
pub struct Money {
    pub amount: u64,
    pub currency_code: CurrencyCode,
}
