use std::collections::HashMap;
use std::fmt::Display;

use bdk_utils::bdk::bitcoin::Address;
use feature_flags::flag::{evaluate_flag_value, ContextKey};
use futures::stream::{FuturesUnordered, StreamExt};
use itertools::Itertools;
use notification::address_repo::AccountIdAndKeysetId;
use notification::payloads::payment::PendingPaymentPayload;
use notification::service::SendNotificationInput;
use notification::{
    payloads::payment::ConfirmedPaymentPayload, service::Service as NotificationService,
    NotificationPayloadBuilder, NotificationPayloadType,
};
use types::account::identifiers::{AccountId, KeysetId};

use bdk_utils::bdk::bitcoin::address::NetworkUnchecked;
use tracing::{event, Level};

use crate::{error::WorkerError, jobs::WorkerState};

#[derive(Debug, Clone, Copy)]
pub(crate) enum PaymentNotificationType {
    Pending,
    Confirmed,
}

impl Display for PaymentNotificationType {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            PaymentNotificationType::Pending => write!(f, "pending"),
            PaymentNotificationType::Confirmed => write!(f, "confirmed"),
        }
    }
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

pub enum CustomerNotificationFeatureFlag {
    UnconfirmedMempoolTransaction,
}

impl Display for CustomerNotificationFeatureFlag {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            CustomerNotificationFeatureFlag::UnconfirmedMempoolTransaction => {
                write!(f, "f8e-mempool-unconfirmed-tx-push-notification")
            }
        }
    }
}

async fn fetch_unique_accounts_by_address(
    state: &WorkerState,
    addresses: Vec<Address<NetworkUnchecked>>,
) -> Result<Vec<AccountIdAndKeysetId>, WorkerError> {
    Ok(state
        .address_repo
        .get(&addresses)
        .await?
        .values()
        .cloned()
        .unique_by(|v| v.account_id.clone())
        .collect())
}

fn filter_accounts_by_flag(
    state: &WorkerState,
    accounts: Vec<AccountIdAndKeysetId>,
    flag_key: Option<String>,
) -> Vec<AccountIdAndKeysetId> {
    accounts
        .into_iter()
        .filter(|v| {
            if let Some(flag_key) = flag_key.as_ref() {
                if let Ok(boolean) = evaluate_flag_value(
                    &state.feature_flags_service,
                    flag_key.clone(),
                    ContextKey::Account(v.account_id.to_string(), HashMap::new()),
                ) {
                    return boolean;
                }
                return false;
            }
            true
        })
        .collect()
}

async fn fetch_active_keysets(
    state: &WorkerState,
    accounts: &[AccountIdAndKeysetId],
    notification_type: PaymentNotificationType,
) -> Result<HashMap<AccountId, KeysetId>, WorkerError> {
    if matches!(notification_type, PaymentNotificationType::Confirmed) {
        let account_ids: Vec<_> = accounts.iter().map(|v| v.account_id.clone()).collect();
        Ok(state
            .account_service
            .fetch_full_accounts_by_account_ids(account_ids)
            .await?
            .into_iter()
            .map(|(account_id, account)| (account_id, account.active_keyset_id))
            .collect())
    } else {
        Ok(HashMap::new())
    }
}

fn pair_accounts_with_keyset_info(
    accounts: Vec<AccountIdAndKeysetId>,
    active_keysets: HashMap<AccountId, KeysetId>,
    notification_type: PaymentNotificationType,
) -> Vec<(AccountIdAndKeysetId, bool)> {
    accounts
        .into_iter()
        .filter_map(|v| {
            let account_id = &v.account_id;
            let is_addressed_to_inactive_keyset =
                if matches!(notification_type, PaymentNotificationType::Confirmed) {
                    if let Some(active_keyset_id) = active_keysets.get(account_id) {
                        v.spending_keyset_id != *active_keyset_id
                    } else {
                        return None;
                    }
                } else {
                    false
                };
            Some((v, is_addressed_to_inactive_keyset))
        })
        .collect()
}

// The main function with cleaned-up logic
pub async fn notify_customers_with_addresses(
    state: &WorkerState,
    addresses: Vec<Address<NetworkUnchecked>>,
    notification_type: PaymentNotificationType,
    flag: Option<CustomerNotificationFeatureFlag>,
) -> Result<(), WorkerError> {
    // Fetch unique accounts by address
    let accounts_to_notify = fetch_unique_accounts_by_address(state, addresses).await?;
    let num_accounts = accounts_to_notify.len();
    let flag_key = flag.map(|f| f.to_string());

    // Filter accounts by feature flag
    let accounts_to_notify = filter_accounts_by_flag(state, accounts_to_notify, flag_key);
    event!(
        Level::INFO,
        "{} accounts found with payments and {} accounts passing checks",
        num_accounts,
        accounts_to_notify.len()
    );

    if accounts_to_notify.is_empty() {
        return Ok(());
    }

    // Fetch active keysets for accounts
    let active_keyset_id_by_account =
        fetch_active_keysets(state, &accounts_to_notify, notification_type).await?;

    // Pair accounts with keyset information
    let accounts_to_notify_with_keyset_info = pair_accounts_with_keyset_info(
        accounts_to_notify,
        active_keyset_id_by_account,
        notification_type,
    );

    let num_notifications_to_inactive_keysets = accounts_to_notify_with_keyset_info
        .iter()
        .filter(|(_, is_addressed_to_inactive_keyset)| *is_addressed_to_inactive_keyset)
        .count();
    event!(
        Level::INFO,
        "Sending {} tx notifications to {} accounts with {} addressed to inactive keysets",
        notification_type,
        accounts_to_notify_with_keyset_info.len(),
        num_notifications_to_inactive_keysets
    );

    // Send notifications
    let mut futures = accounts_to_notify_with_keyset_info
        .iter()
        .map(|(v, is_addressed_to_inactive_keyset)| {
            send_new_tx_notifications(
                &v.account_id,
                &state.notification_service,
                notification_type,
                *is_addressed_to_inactive_keyset,
            )
        })
        .collect::<FuturesUnordered<_>>();

    // Handle results from sending notifications
    while let Some(result) = futures.next().await {
        if let Err(e) = result {
            event!(Level::ERROR, "Unable to send push notification: {e}");
        }
    }
    Ok(())
}

async fn send_new_tx_notifications(
    account_id: &AccountId,
    service: &NotificationService,
    notification_type: PaymentNotificationType,
    is_addressed_to_inactive_keyset: bool,
) -> Result<(), WorkerError> {
    event!(Level::INFO, "Sending notification for account {account_id}");
    let mut builder = NotificationPayloadBuilder::default();
    let payload = match notification_type {
        PaymentNotificationType::Pending => {
            builder.pending_payment_payload(Some(PendingPaymentPayload {
                account_id: account_id.clone(),
                is_addressed_to_inactive_keyset,
            }))
        }
        PaymentNotificationType::Confirmed => {
            builder.confirmed_payment_payload(Some(ConfirmedPaymentPayload {
                account_id: account_id.clone(),
                is_addressed_to_inactive_keyset,
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
