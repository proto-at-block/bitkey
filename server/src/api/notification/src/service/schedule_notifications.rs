use rand::Rng;
use time::{Duration, OffsetDateTime};
use tracing::instrument;

use super::{
    RescheduleNotificationInput, ScheduleNotificationsInput, SendNotificationInput, Service,
};
use crate::{
    entities::{
        NotificationSchedule, ScheduledNotification, EXECUTION_DATE_FORMAT, EXECUTION_TIME_FORMAT,
    },
    identifiers::NotificationId,
    DeliveryStatus, NotificationError,
};

impl Service {
    #[instrument(skip(self))]
    pub async fn schedule_notifications(
        &self,
        input: ScheduleNotificationsInput,
    ) -> Result<(), NotificationError> {
        let schedule = input.notification_type.gen_schedule();
        let now = OffsetDateTime::now_utc();
        let (immediate, future) = schedule.into_iter().try_fold(
            (vec![], vec![]),
            |(mut i, mut f), s| {
                let (payload_type, execution_date_time, schedule) = s;
                let execution_time = execution_date_time.format(&EXECUTION_TIME_FORMAT).unwrap();
                let sharded_execution_date = format!(
                    "{}:{}",
                    execution_date_time.format(&EXECUTION_DATE_FORMAT).unwrap(),
                    0
                );
                let payload = payload_type.filter_payload(&input.payload)?;
                let notification = ScheduledNotification {
                    account_id: input.account_id.clone(),
                    unique_id: NotificationId::gen_scheduled(),
                    sharded_execution_date,
                    execution_time,
                    execution_date_time,
                    payload_type,
                    payload,
                    delivery_status: DeliveryStatus::New,
                    created_at: now,
                    updated_at: now,
                    schedule,
                };

                if execution_date_time <= now {
                    i.push(notification);
                } else {
                    f.push(notification);
                }

                Ok::<(Vec<ScheduledNotification>, Vec<ScheduledNotification>), NotificationError>((
                    i, f,
                ))
            },
        )?;

        for notification in immediate.into_iter() {
            self.send_notification(SendNotificationInput {
                account_id: &input.account_id,
                payload_type: notification.payload_type,
                payload: &notification.payload,
                only_touchpoints: None,
            })
            .await?;

            // Reschedule
            self.reschedule_notification(RescheduleNotificationInput {
                account_id: &input.account_id,
                payload_type: notification.payload_type,
                payload: &notification.payload,
                schedule: notification.schedule,
            })
            .await?;
        }

        self.notification_repo
            .persist_notifications(future.into_iter().map(|n| n.into()).collect())
            .await?;

        Ok(())
    }

    #[instrument(skip(self))]
    pub async fn reschedule_notification(
        &self,
        input: RescheduleNotificationInput<'_>,
    ) -> Result<(), NotificationError> {
        let Some(schedule) = input.schedule.as_ref() else {
            return Ok(());
        };

        let now: OffsetDateTime = OffsetDateTime::now_utc();

        let (jitter, execution_date_time) = if let Some(previous_jitter) = schedule.jitter {
            let new_jitter = gen_jitter(&schedule.interval);

            (
                Some(new_jitter),
                // Account for previous jitter to prevent schedule drift
                (now - previous_jitter) + schedule.interval + new_jitter,
            )
        } else {
            (None, now + schedule.interval)
        };

        if let Some(end_date_time) = schedule.end_date_time {
            if execution_date_time > end_date_time {
                return Ok(());
            }
        }

        let execution_time = execution_date_time.format(&EXECUTION_TIME_FORMAT).unwrap();
        let sharded_execution_date = format!(
            "{}:{}",
            execution_date_time.format(&EXECUTION_DATE_FORMAT).unwrap(),
            0
        );

        let rescheduled_notification = ScheduledNotification {
            account_id: input.account_id.to_owned(),
            unique_id: NotificationId::gen_scheduled(),
            sharded_execution_date,
            execution_time,
            execution_date_time,
            payload_type: input.payload_type,
            payload: input.payload.to_owned(),
            delivery_status: DeliveryStatus::New,
            created_at: now,
            updated_at: now,
            schedule: Some(NotificationSchedule {
                jitter,
                ..schedule.to_owned()
            }),
        };
        self.notification_repo
            .persist_notifications(vec![rescheduled_notification.into()])
            .await?;

        Ok(())
    }
}

// gen_jitter generates a random duration (positive or negative) that is less than
// 1/8th of the schedule's interval (this equates to ~3 hours per day of interval).
// This jitter is used to prevent all notifications for a given schedule from being
// sent at the exact same time of day.
fn gen_jitter(interval: &Duration) -> Duration {
    let total_secs = interval.whole_seconds();
    let max_jitter = total_secs / 8;

    let mut rng = rand::thread_rng();
    let jitter_secs = rng.gen_range(0..=max_jitter);

    let signed_jitter_secs = if jitter_secs % 2 == 0 {
        -jitter_secs
    } else {
        jitter_secs
    };

    Duration::seconds(signed_jitter_secs)
}

#[test]
fn test_gen_jitter() {
    let interval = Duration::days(2);
    let jitter = gen_jitter(&interval);
    assert!(
        jitter <= Duration::hours(6) && jitter >= Duration::hours(-6),
        "this should _never_ fail"
    );
}
