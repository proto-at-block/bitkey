use errors::ApiError;
use thiserror::Error;
use time::{Duration, OffsetDateTime, Time, UtcOffset};
use types::account::spend_limit::SpendingLimit;

use crate::daily_spend_record::entities::SpendingEntry;

const START_OF_WINDOW_HOUR: u8 = 3; // 3 AM is the start of the quickspend window

#[derive(Error, Debug, Clone)]
pub enum MobilepayDatetimeError {
    #[error("Could not perform datetime arithmetic {0}")]
    DateMathError(String),
}

impl From<MobilepayDatetimeError> for ApiError {
    fn from(value: MobilepayDatetimeError) -> Self {
        match value {
            MobilepayDatetimeError::DateMathError(msg) => {
                ApiError::GenericInternalApplicationError(format!(
                    "Failed at internal datetime conversion: {msg}"
                ))
            }
        }
    }
}

pub(crate) fn total_sats_spent_today(
    spending_entries: &[&SpendingEntry],
    limit: &SpendingLimit,
    now_utc: OffsetDateTime,
) -> Result<u64, String> {
    let timezone_offset = limit.time_zone_offset;
    let current_timezone_dt = now_utc.to_offset(timezone_offset);
    let start_of_window_time = Time::from_hms(START_OF_WINDOW_HOUR, 0, 0)
        .map_err(|_| "Invalid start of window time".to_owned())?;

    let start_of_window_dt = if current_timezone_dt.hour() < START_OF_WINDOW_HOUR {
        (current_timezone_dt - Duration::DAY).replace_time(start_of_window_time)
    } else {
        current_timezone_dt.replace_time(start_of_window_time)
    };

    let start_of_window_utc = start_of_window_dt.to_offset(UtcOffset::UTC);

    Ok(spending_entries
        .iter()
        .filter(|&spend| spend.timestamp >= start_of_window_utc)
        .fold(0, |acc, &spend| acc + spend.outflow_amount))
}
