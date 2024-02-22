use errors::ApiError;
use notification::{
    entities::ScheduledNotification,
    service::{
        FetchScheduledForWindowInput, RescheduleNotificationInput, SendNotificationInput,
        UpdateDeliveryStatusInput,
    },
    DeliveryStatus,
};
use notification_validation::{to_validator, NotificationValidationState};
use time::{Duration, OffsetDateTime};
use tracing::{event, instrument, Level};

use super::WorkerState;
use crate::error::WorkerError;

#[instrument(skip(state))]
pub async fn handler(state: WorkerState, sleep_duration_seconds: u64) -> Result<(), WorkerError> {
    let sleep_duration = std::time::Duration::from_secs(sleep_duration_seconds);

    loop {
        let result = run_once(state.clone()).await;
        if let Err(e) = result {
            event!(
                Level::ERROR,
                "Failed to process scheduled notifications: {e}"
            )
        }
        tokio::time::sleep(sleep_duration).await;
    }
}

pub async fn run_once(state: WorkerState) -> Result<(), WorkerError> {
    let cur_time = OffsetDateTime::now_utc();

    let notifications = state
        .notification_service
        .fetch_scheduled_for_window(FetchScheduledForWindowInput {
            window_end_time: cur_time,
            duration: Duration::minutes(10),
        })
        .await
        .map_err(|_| WorkerError::FetchNotifications)?; // TODO: Refactor NotificationService to return NotificationError and update error to take in Notification Error

    let notification_validation_state: NotificationValidationState =
        Into::<NotificationValidationState>::into(state.clone());

    for n in notifications {
        let composite_key = n.clone().composite_key();
        if let Err(e) =
            handle_scheduled_notification(&state, n, &notification_validation_state).await
        {
            event!(
                Level::ERROR,
                "Failed to update enqueued and updated notification with id: {composite_key:?} due to error: {e}"
            );
        } else {
            event!(
                Level::INFO,
                "Successfully enqueued and updated notification with id: {composite_key:?}"
            );
        }
    }
    Ok(())
}

async fn handle_scheduled_notification(
    state: &WorkerState,
    n: ScheduledNotification,
    notification_validation_state: &NotificationValidationState,
) -> Result<(), ApiError> {
    let v = to_validator(n.payload_type, &n.payload)?;
    if !v
        .validate_delivery(notification_validation_state, &n.composite_key())
        .await
    {
        return Ok(());
    }

    state
        .notification_service
        .send_notification(SendNotificationInput {
            account_id: &n.account_id,
            payload_type: n.payload_type,
            payload: &n.payload,
            only_touchpoints: None,
        })
        .await?;

    state
        .notification_service
        .update_delivery_status(UpdateDeliveryStatusInput {
            composite_key: n.composite_key(),
            status: DeliveryStatus::Completed,
        })
        .await?;

    // TODO: we should be able to get rid of self-rescheduling notifications once we use
    //   workflows/step functions for recovery and/or notifications
    state
        .notification_service
        .reschedule_notification(RescheduleNotificationInput {
            account_id: &n.account_id,
            payload_type: n.payload_type,
            payload: &n.payload,
            schedule: n.schedule,
        })
        .await?;
    Ok(())
}
