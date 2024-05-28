use bdk_utils::bdk::bitcoin::Address;
use futures::stream::{FuturesUnordered, StreamExt};
use itertools::Itertools;
use notification::payloads::payment::PendingPaymentPayload;
use notification::service::SendNotificationInput;
use notification::{
    payloads::payment::PaymentPayload, service::Service as NotificationService,
    NotificationPayloadBuilder, NotificationPayloadType,
};
use types::account::identifiers::AccountId;

use bdk_utils::bdk::bitcoin::address::NetworkUnchecked;
use tracing::{event, Level};

use crate::{error::WorkerError, jobs::WorkerState};

#[derive(Debug, Clone, Copy)]
pub(crate) enum PaymentNotificationType {
    Pending,
    Confirmed,
}

impl From<PaymentNotificationType> for NotificationPayloadType {
    fn from(value: PaymentNotificationType) -> Self {
        match value {
            PaymentNotificationType::Pending => NotificationPayloadType::PendingPaymentNotification,
            PaymentNotificationType::Confirmed => {
                NotificationPayloadType::ConfirmedPaymentNotification
            }
        }
    }
}

pub async fn notify_customers_with_addresses(
    state: &WorkerState,
    addresses: Vec<Address<NetworkUnchecked>>,
    notification_type: PaymentNotificationType,
) -> Result<(), WorkerError> {
    let account_ids: Vec<AccountId> = state
        .address_repo
        .get(&addresses)
        .await?
        .values()
        .cloned()
        .unique()
        .collect();
    event!(
        Level::INFO,
        "{} accounts found with payments",
        account_ids.len()
    );
    let mut futures = account_ids
        .iter()
        .map(|account_id| {
            send_new_tx_notifications(account_id, &state.notification_service, notification_type)
        })
        .collect::<FuturesUnordered<_>>();
    while let Some(result) = futures.next().await {
        if let Err(e) = result {
            event!(Level::ERROR, "Unable to send push notification {e}");
        }
    }
    Ok(())
}

async fn send_new_tx_notifications(
    account_id: &AccountId,
    service: &NotificationService,
    notification_type: PaymentNotificationType,
) -> Result<(), WorkerError> {
    event!(Level::INFO, "Sending notification for account {account_id}");
    let mut builder = NotificationPayloadBuilder::default();
    let payload = match notification_type {
        PaymentNotificationType::Pending => {
            builder.pending_payment_payload(Some(PendingPaymentPayload {
                account_id: account_id.clone(),
            }))
        }
        PaymentNotificationType::Confirmed => {
            builder.confirmed_payment_payload(Some(PaymentPayload {
                account_id: account_id.clone(),
            }))
        }
    }
    .build()?;
    service
        .send_notification(SendNotificationInput {
            account_id,
            payload_type: notification_type.into(),
            payload: &payload,
            only_touchpoints: None,
        })
        .await?;

    Ok(())
}
