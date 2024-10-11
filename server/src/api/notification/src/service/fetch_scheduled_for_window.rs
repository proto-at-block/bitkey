use errors::{ApiError, RouteError};
use time::{macros::time, Duration, Time};
use tracing::instrument;

use super::{FetchScheduledForWindowInput, Service};
use crate::{
    entities::{ScheduledNotification, EXECUTION_DATE_FORMAT, EXECUTION_TIME_FORMAT},
    DeliveryStatus,
};

const WORKER_SHARD_IDENTIFIER: &str = "0";
const EXECUTION_WINDOW_END_OF_DAY_TIME: Time = time!(23:59:59);
const EXECUTION_WINDOW_END_OF_DAY_NANOSECONDS: u32 = 999999;
const EXECUTION_WINDOW_START_OF_DAY_TIME: Time = time!(00:00:00);
const EXECUTION_WINDOW_START_OF_DAY_NANOSECONDS: u32 = 0;

impl Service {
    #[instrument(skip(self))]
    pub async fn fetch_scheduled_for_window(
        &self,
        input: FetchScheduledForWindowInput,
    ) -> Result<Vec<ScheduledNotification>, ApiError> {
        assert!(input.duration < Duration::hours(24));
        let execution_time_end_window = input.window_end_time;
        let execution_time_start_window = execution_time_end_window - input.duration;

        // 'Now' and 'Now - Duration' belong to the same day
        if execution_time_end_window.date() == execution_time_start_window.date() {
            let execution_time_lower = execution_time_start_window
                .format(&EXECUTION_TIME_FORMAT)
                .map_err(|_| RouteError::DatetimeFormatError)?;
            let execution_time_upper = execution_time_end_window
                .format(&EXECUTION_TIME_FORMAT)
                .map_err(|_| RouteError::DatetimeFormatError)?;
            let sharded_execution_date = format!(
                "{}:{}",
                execution_time_end_window
                    .format(&EXECUTION_DATE_FORMAT)
                    .map_err(|_| RouteError::DatetimeFormatError)?,
                WORKER_SHARD_IDENTIFIER
            );
            let notifications = self
                .notification_repo
                .fetch_scheduled_in_execution_window(
                    sharded_execution_date,
                    execution_time_lower,
                    execution_time_upper,
                    DeliveryStatus::New,
                )
                .await?;
            return Ok(notifications);
        }
        // This occurs when you have an execution window that interjects between midnight.
        // We need to fetch the notifications separately as they take place on separate days
        let execution_time_lower = execution_time_start_window
            .format(&EXECUTION_TIME_FORMAT)
            .map_err(|_| RouteError::DatetimeFormatError)?;
        let execution_time_upper = execution_time_start_window
            .replace_time(EXECUTION_WINDOW_END_OF_DAY_TIME)
            .replace_nanosecond(EXECUTION_WINDOW_END_OF_DAY_NANOSECONDS)
            .map_err(|_| RouteError::MutateDatetimeError)?
            .format(&EXECUTION_TIME_FORMAT)
            .map_err(|_| RouteError::DatetimeFormatError)?;
        let sharded_execution_date = format!(
            "{}:{}",
            execution_time_start_window
                .format(&EXECUTION_DATE_FORMAT)
                .map_err(|_| RouteError::DatetimeFormatError)?,
            WORKER_SHARD_IDENTIFIER
        );
        let mut notifications = self
            .notification_repo
            .fetch_scheduled_in_execution_window(
                sharded_execution_date,
                execution_time_lower,
                execution_time_upper,
                DeliveryStatus::New,
            )
            .await?;

        let sharded_execution_date = format!(
            "{}:{}",
            execution_time_end_window
                .format(&EXECUTION_DATE_FORMAT)
                .map_err(|_| RouteError::DatetimeFormatError)?,
            WORKER_SHARD_IDENTIFIER
        );
        let execution_time_lower = execution_time_end_window
            .replace_time(EXECUTION_WINDOW_START_OF_DAY_TIME)
            .replace_nanosecond(EXECUTION_WINDOW_START_OF_DAY_NANOSECONDS)
            .map_err(|_| RouteError::MutateDatetimeError)?
            .format(&EXECUTION_TIME_FORMAT)
            .map_err(|_| RouteError::DatetimeFormatError)?;
        let execution_time_upper = execution_time_end_window
            .format(&EXECUTION_TIME_FORMAT)
            .map_err(|_| RouteError::DatetimeFormatError)?;
        let mut current_day_notifications = self
            .notification_repo
            .fetch_scheduled_in_execution_window(
                sharded_execution_date,
                execution_time_lower,
                execution_time_upper,
                DeliveryStatus::New,
            )
            .await?;
        notifications.append(&mut current_day_notifications);
        Ok(notifications)
    }
}
