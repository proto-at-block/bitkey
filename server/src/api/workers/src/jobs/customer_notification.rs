use std::env;

use account::service::FetchAccountInput;
use account::service::Service as AccountService;
use errors::ApiError;
use notification::entities::NotificationTouchpoint;
use notification::service::FetchForCompositeKeyInput;
use notification::{
    service::{Service as NotificationService, UpdateDeliveryStatusInput},
    DeliveryStatus, NotificationMessage,
};
use notification::{EMAIL_QUEUE_ENV_VAR, PUSH_QUEUE_ENV_VAR, SMS_QUEUE_ENV_VAR};
use tracing::{event, instrument, Level};
use types::account::entities::Touchpoint;
use types::notification::NotificationChannel;

use super::WorkerState;
use crate::sms::SendSMS;
use crate::sqs::sqs_job_handler;
use crate::{email::SendEmail, error::WorkerError, sns::SendPushNotification};

#[instrument(skip(state))]
pub async fn handler(
    state: &WorkerState,
    notification_channel: NotificationChannel,
) -> Result<(), WorkerError> {
    let queue_url = match notification_channel {
        NotificationChannel::Push => env::var(PUSH_QUEUE_ENV_VAR).unwrap_or_default(),
        NotificationChannel::Email => env::var(EMAIL_QUEUE_ENV_VAR).unwrap_or_default(),
        NotificationChannel::Sms => env::var(SMS_QUEUE_ENV_VAR).unwrap_or_default(),
    };
    let email_client = SendEmail::new(
        state.config.ses.to_owned(),
        state.config.iterable.to_owned(),
    )
    .await;
    let push_client = SendPushNotification::new(state.config.sns.to_owned()).await;
    let sms_client = SendSMS::new(state.config.twilio.to_owned()).await;
    let email_client_ref = &email_client;
    let push_client_ref = &push_client;
    let sms_client_ref = &sms_client;

    sqs_job_handler(state, queue_url, |serialized_messages| async move {
        let mut failed_messages: Vec<NotificationMessage> = Vec::new();
        for serialized_notification in serialized_messages {
            let notification: NotificationMessage = serde_json::from_str(&serialized_notification)
                .map_err(WorkerError::SerdeSerialization)?;
            if let Err(e) = notification_handler(
                &state.account_service,
                &state.notification_service,
                push_client_ref,
                email_client_ref,
                sms_client_ref,
                &notification,
            )
            .await
            {
                let composite_key = notification.clone().composite_key;
                failed_messages.push(notification);
                event!(
                    Level::ERROR,
                    "Failed to update notification with id: {:?} due to error: {e}",
                    composite_key
                );
            } else {
                event!(
                    Level::INFO,
                    "Successfully handled notification with key: {:?}",
                    notification.composite_key
                );
            }
        }
        Ok(())
    })
    .await
}

//TODO: Make into closure once for<'a> is supported
// https://github.com/rust-lang/rust/issues/97362
async fn notification_handler(
    account_service: &AccountService,
    notification_service: &NotificationService,
    push_client: &SendPushNotification,
    email_client: &SendEmail,
    sms_client: &SendSMS,
    message: &NotificationMessage,
) -> Result<(), ApiError> {
    let Ok(account) = account_service
        .fetch_account(FetchAccountInput {
            account_id: &message.account_id,
        })
        .await
    else {
        return Err(WorkerError::AccountNotFound)?;
    };
    let composite_key = message.clone().composite_key;
    let Some(notification) = notification_service
        .fetch_pending(FetchForCompositeKeyInput {
            composite_key: composite_key.clone(),
        })
        .await?
    else {
        event!(
            Level::INFO,
            "Notification not found for message: {:?}",
            message.composite_key.clone()
        );
        return Ok(());
    };
    notification_service
        .update_delivery_status(UpdateDeliveryStatusInput {
            composite_key: message.clone().composite_key,
            status: DeliveryStatus::Completed,
        })
        .await?;
    event!(
        Level::INFO,
        "Updated Delivery Status for message: {:?}",
        message.composite_key.clone()
    );

    let email_payload = message.email_payload.as_ref();
    let push_payload = message.push_payload.as_ref();
    let sms_payload = message.sms_payload.as_ref();

    let touchpoint = match notification.touchpoint {
        NotificationTouchpoint::Email { touchpoint_id }
        | NotificationTouchpoint::Phone { touchpoint_id } => {
            account.get_touchpoint_by_id(touchpoint_id)
        }
        NotificationTouchpoint::Push {
            platform,
            device_token,
        } => account.get_push_touchpoint_by_platform_and_device_token(platform, device_token),
        _ => None,
    };
    let Some(t) = touchpoint else {
        return Ok(());
    };

    match t.to_owned() {
        Touchpoint::Email { .. } => {
            if let Some(payload) = email_payload {
                email_client.send(account.get_id(), t, payload).await?;
            }
        }
        Touchpoint::Push {
            platform: _,
            arn,
            device_token: _,
        } => {
            if let Some(payload) = push_payload {
                push_client.send(&arn, payload).await?;
            }
        }
        Touchpoint::Phone { country_code, .. } => {
            if let Some(payload) = sms_payload {
                if !sms_client.is_supported_country_code(country_code) {
                    event!(
                        Level::INFO,
                        "Filtering SMS: client does not support the country_code {}",
                        country_code,
                    );
                    return Ok(());
                }

                if let Some(unsupported_country_codes) = payload.unsupported_country_codes.as_ref()
                {
                    if unsupported_country_codes.contains(&country_code) {
                        event!(
                            Level::INFO,
                            "Filtering SMS: payload does not support the country_code {}",
                            country_code,
                        );
                        return Ok(());
                    }
                }

                sms_client.send(t, payload).await?;
            }
        }
    }
    Ok(())
}
