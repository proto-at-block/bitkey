use account::service::FetchAccountInput;
use time::OffsetDateTime;
use tracing::instrument;
use types::notification::{NotificationCategory, NotificationChannel};

use crate::{
    entities::{CustomerNotification, Notification, NotificationTouchpoint},
    identifiers::NotificationId,
    DeliveryStatus, NotificationError, NotificationMessage,
};

use super::{SendNotificationInput, Service};

impl Service {
    #[instrument(skip(self))]
    pub async fn send_notification(
        &self,
        input: SendNotificationInput<'_>,
    ) -> Result<(), NotificationError> {
        let payload = input.payload_type.filter_payload(input.payload)?;

        let account = self
            .account_service
            .fetch_account(FetchAccountInput {
                account_id: input.account_id,
            })
            .await?;

        let (customer_notifications, serialized_messages) = account
            .get_common_fields()
            .to_owned()
            .touchpoints
            .into_iter()
            .filter(|t| {
                let Some(only_touchpoints) = input.only_touchpoints.as_ref() else {
                    let notification_category = NotificationCategory::from(input.payload_type);
                    let notification_channel = NotificationChannel::from(t);

                    return t.is_active()
                        && account
                            .get_common_fields()
                            .notifications_preferences
                            .is_enabled(notification_category, notification_channel);
                };

                // If the notification is targeting a specific touchpoint(s), we skip the standard checks of whether or
                // not the touchpoint is active or whether or not the account has opted in to this notification category
                // for this touchpoint type. This option is used only when the system needs to send a notification to
                // a specific touchpoint(s) regardless of subscription preferences or active status. (Currently only for OTPs
                // which get sent before the user can set their preferences.)
                only_touchpoints.contains(&NotificationTouchpoint::from(t.to_owned()))
            })
            .try_fold((vec![], vec![]), |(mut c, mut s), t| {
                let customer_notification = CustomerNotification {
                    account_id: input.account_id.to_owned(),
                    unique_id: NotificationId::gen_customer(),
                    touchpoint: NotificationTouchpoint::from(t.clone()),
                    payload_type: input.payload_type,
                    delivery_status: DeliveryStatus::Enqueued,
                    created_at: OffsetDateTime::now_utc(),
                    updated_at: OffsetDateTime::now_utc(),
                };

                let notification_message = NotificationMessage::try_from((
                    customer_notification.composite_key(),
                    input.payload_type,
                    payload.clone(),
                ))?;

                if !notification_message
                    .channels()
                    .contains(&NotificationChannel::from(&t))
                {
                    return Ok::<_, NotificationError>((c, s));
                }

                let serialized_message = serde_json::to_string(&notification_message)?;

                c.push(customer_notification);
                s.push((NotificationChannel::from(&t), serialized_message));

                Ok((c, s))
            })?;

        if !customer_notifications.is_empty() {
            self.notification_repo
                .persist_notifications(
                    customer_notifications
                        .into_iter()
                        .map(Notification::from)
                        .collect(),
                )
                .await?;

            for (channel, message) in serialized_messages {
                let queue_url = match channel {
                    NotificationChannel::Push => self.push_queue_url.as_str(),
                    NotificationChannel::Email => self.email_queue_url.as_str(),
                    NotificationChannel::Sms => self.sms_queue_url.as_str(),
                };
                self.sqs.enqueue(queue_url, &message).await?;
            }
        }

        Ok(())
    }
}
