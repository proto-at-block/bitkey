use std::{collections::HashSet, env};

use account::service::Service as AccountService;
use authn_authz::key_claims::KeyClaims;
use queue::sqs::SqsQueue;
use repository::account::AccountRepository;
use repository::consent::ConsentRepository;
use serde::Deserialize;
use time::{Duration, OffsetDateTime};
use types::{account::identifiers::AccountId, notification::NotificationsPreferences};

use crate::{
    clients::iterable::{IterableClient, IterableMode},
    entities::{
        CustomerNotification, NotificationCompositeKey, NotificationSchedule,
        NotificationTouchpoint, ScheduledNotification,
    },
    repository::NotificationRepository,
    schedule::ScheduleNotificationType,
    DeliveryStatus, NotificationPayload, NotificationPayloadType, EMAIL_QUEUE_ENV_VAR,
    PUSH_QUEUE_ENV_VAR, SMS_QUEUE_ENV_VAR,
};

mod fetch_for_account;
mod fetch_scheduled_for_window;
pub mod migrations;
pub mod notifications_preferences;
mod persist_notifications;
mod schedule_notifications;
mod send_notification;
pub mod tests;
mod update_delivery_status;

#[derive(Deserialize)]
pub struct Config {
    pub iterable: IterableMode,
}

#[derive(Clone)]
pub struct Service {
    notification_repo: NotificationRepository,
    account_repo: AccountRepository,
    account_service: AccountService,
    sqs: SqsQueue,
    push_queue_url: String,
    sms_queue_url: String,
    email_queue_url: String,
    iterable_client: IterableClient,
    consent_repo: ConsentRepository,
}

impl Service {
    pub async fn new(
        notification_repo: NotificationRepository,
        account_repo: AccountRepository,
        account_service: AccountService,
        sqs: SqsQueue,
        iterable_client: IterableClient,
        consent_repo: ConsentRepository,
    ) -> Self {
        Self {
            notification_repo,
            account_repo,
            account_service,
            sqs,
            push_queue_url: env::var(PUSH_QUEUE_ENV_VAR).unwrap_or_default(),
            sms_queue_url: env::var(SMS_QUEUE_ENV_VAR).unwrap_or_default(),
            email_queue_url: env::var(EMAIL_QUEUE_ENV_VAR).unwrap_or_default(),
            iterable_client,
            consent_repo,
        }
    }
}

// General Inputs

#[derive(Debug)]
pub struct FetchForAccountInput {
    pub account_id: AccountId,
}

#[derive(Debug)]
pub struct FetchForCompositeKeyInput {
    pub composite_key: NotificationCompositeKey,
}

#[derive(Debug)]
pub struct FetchForCompositeKeysInput {
    pub composite_keys: Vec<NotificationCompositeKey>,
}

// Scheduled Notifications
#[derive(Debug)]
pub struct ScheduleNotificationsInput {
    pub account_id: AccountId,
    pub notification_type: ScheduleNotificationType,
    pub payload: NotificationPayload,
}

#[derive(Debug)]
pub struct SendNotificationInput<'a> {
    pub account_id: &'a AccountId,
    pub payload_type: NotificationPayloadType,
    pub payload: &'a NotificationPayload,
    pub only_touchpoints: Option<HashSet<NotificationTouchpoint>>,
}

#[derive(Debug)]
pub struct RescheduleNotificationInput<'a> {
    pub account_id: &'a AccountId,
    pub payload_type: NotificationPayloadType,
    pub payload: &'a NotificationPayload,
    pub schedule: Option<NotificationSchedule>,
}

#[derive(Debug)]
pub struct PersistScheduledNotificationsInput {
    pub account_id: AccountId,
    pub notifications: Vec<ScheduledNotification>,
}

#[derive(Debug)]
pub struct FetchScheduledForWindowInput {
    pub window_end_time: OffsetDateTime,
    pub duration: Duration,
}

#[derive(Debug)]
pub struct UpdateDeliveryStatusInput {
    pub composite_key: NotificationCompositeKey,
    pub status: DeliveryStatus,
}

// Customer Touchpoint Notifications

#[derive(Debug)]
pub struct PersistCustomerNotificationsInput {
    pub account_id: AccountId,
    pub notifications: Vec<CustomerNotification>,
}

// Notifications Preferences
#[derive(Debug)]
pub struct FetchNotificationsPreferencesInput<'a> {
    pub account_id: &'a AccountId,
}

#[derive(Debug)]
pub struct UpdateNotificationsPreferencesInput<'a> {
    pub account_id: &'a AccountId,
    pub notifications_preferences: &'a NotificationsPreferences,
    pub key_proof: Option<KeyClaims>,
}
