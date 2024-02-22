use time::{Duration, OffsetDateTime};

use crate::entities::NotificationSchedule;

use super::NotificationPayloadType;

pub type ScheduledNotificationEvent = (
    NotificationPayloadType,
    OffsetDateTime,
    Option<NotificationSchedule>,
);

#[derive(Debug)]
pub enum ScheduleNotificationType {
    TestPushNotification,
    RecoveryPendingDelayNotify(OffsetDateTime),
}

impl ScheduleNotificationType {
    pub(crate) fn gen_schedule(&self) -> Vec<ScheduledNotificationEvent> {
        let now = OffsetDateTime::now_utc();
        match self {
            ScheduleNotificationType::TestPushNotification => {
                vec![(
                    NotificationPayloadType::TestPushNotification,
                    now,
                    Some(NotificationSchedule {
                        interval: Duration::seconds(0),
                        end_date_time: None,
                        jitter: None,
                    }),
                )]
            }
            ScheduleNotificationType::RecoveryPendingDelayNotify(delay_end_time) => {
                vec![
                    (
                        // Starts now
                        // Sends every 2 days
                        // Ends at delay end
                        // =
                        // DAYS 0, 2, 4, 6 (standard 7-day window)
                        NotificationPayloadType::RecoveryPendingDelayPeriod,
                        now,
                        Some(NotificationSchedule {
                            interval: Duration::days(2),
                            end_date_time: Some(*delay_end_time),
                            jitter: Some(Duration::ZERO),
                        }),
                    ),
                    (
                        // Starts at delay end
                        // Sends every 3 days
                        // Ends at delay end + 7 days
                        // =
                        // DAYS 7, 10, 13 (standard 7-day window)
                        NotificationPayloadType::RecoveryCompletedDelayPeriod,
                        *delay_end_time,
                        Some(NotificationSchedule {
                            interval: Duration::days(3),
                            end_date_time: Some(*delay_end_time + Duration::days(7)),
                            jitter: None,
                        }),
                    ),
                    (
                        // Starts at delay end + 9 days
                        // Sends every 7 days
                        // Ends at delay end + 24 days
                        // =
                        // DAYS 16, 23, 30 (standard 7-day window)
                        NotificationPayloadType::RecoveryCompletedDelayPeriod,
                        *delay_end_time + Duration::days(9),
                        Some(NotificationSchedule {
                            interval: Duration::days(7),
                            end_date_time: Some(*delay_end_time + Duration::days(24)),
                            jitter: None,
                        }),
                    ),
                ]
            }
        }
    }
}
