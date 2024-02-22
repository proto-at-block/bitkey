use account::service::FetchAccountInput;
use bdk_utils::bdk::bitcoin::Address;
use futures::stream::{FuturesUnordered, StreamExt};
use itertools::Itertools;
use notification::service::SendNotificationInput;
use notification::{
    payloads::payment::PaymentPayload, service::Service as NotificationService,
    NotificationPayloadBuilder, NotificationPayloadType,
};
use std::str::FromStr;
use types::account::identifiers::AccountId;

use bdk_utils::bdk::bitcoin::address::NetworkUnchecked;
use tracing::{event, instrument, Level};

use super::WorkerState;
use crate::error::WorkerError;

#[instrument(skip(state))]
pub async fn handler(state: &WorkerState, sleep_duration_seconds: u64) -> Result<(), WorkerError> {
    // TODO: We should schedule events to trigger the job rather than using an infinite poll-loop
    // in a http handler: W-3245/scheduled-workers-refactor
    let sleep_duration = std::time::Duration::from_secs(sleep_duration_seconds);

    loop {
        let result = run_once(state).await;
        if let Err(e) = result {
            event!(Level::ERROR, "Failed to run blockchain polling job: {e}")
        }
        tokio::time::sleep(sleep_duration).await;
    }
}

pub async fn run_once(state: &WorkerState) -> Result<(), WorkerError> {
    event!(Level::INFO, "Starting blockchain polling job");

    let blocks = state.chain_indexer_service.get_new_blocks().await?;
    if blocks.is_empty() {
        event!(Level::INFO, "No new blocks detected");
        return Ok(());
    }
    event!(Level::INFO, "{} blocks found", blocks.len());
    // We update state here to avoid sending duplicate notifications if the job crashes,
    // however, this could result in missed notifications in that case. We plan on updating
    // this job with a cursor so that it can resume where it left off.
    for block in &blocks {
        state.chain_indexer_service.add_block(block).await?;
    }
    event!(Level::INFO, "{} blocks added", blocks.len());

    let addresses: Vec<Address<NetworkUnchecked>> = blocks
        .into_iter()
        .flat_map(|block| block.txdata)
        .flat_map(|transaction| transaction.output)
        .unique()
        .filter_map(|output| {
            if output.script_pubkey.is_op_return() {
                None
            } else {
                match Address::from_script(
                    &output.script_pubkey,
                    state.chain_indexer_service.network(),
                ) {
                    Ok(address) => {
                        // `from_script` returns an Address with `NetworkChecked`. Here, we want one
                        // with `NetworkUnchecked`.
                        // [W-5648]: Use `as_unchecked` once it's available in BDK.
                        let addr_string = address.to_string();
                        let address = Address::from_str(&addr_string).unwrap();
                        Some(address)
                    }
                    Err(_) => {
                        event!(
                            Level::ERROR,
                            "Unable to parse address from script for output: {:?}",
                            output
                        );
                        None
                    }
                }
            }
        })
        .unique()
        .collect();
    event!(Level::INFO, "{} addresses found in blocks", addresses.len());

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
    let mut futures = FuturesUnordered::new();
    for account_id in account_ids {
        futures.push(process_account_id(account_id, state));
    }
    while let Some(result) = futures.next().await {
        if let Err(e) = result {
            event!(Level::ERROR, "Unable to send push notification {e}");
        }
    }

    event!(Level::INFO, "Ending blockchain polling job");
    Ok(())
}

async fn process_account_id(account_id: AccountId, state: &WorkerState) -> Result<(), WorkerError> {
    let account = state
        .account_service
        .fetch_account(FetchAccountInput {
            account_id: &account_id,
        })
        .await
        .map_err(|e| WorkerError::AccountErrorWithId(account_id.clone(), e))?;

    // Dont send a push notification if one isn't registered
    account
        .get_push_touchpoint()
        .ok_or_else(|| WorkerError::TouchpointNotFound(account_id.clone()))?;

    send_new_tx_notifications(&account_id, &state.notification_service).await
}

async fn send_new_tx_notifications(
    account_id: &AccountId,
    service: &NotificationService,
) -> Result<(), WorkerError> {
    event!(Level::INFO, "Sending notification for account {account_id}");

    let payload = NotificationPayloadBuilder::default()
        .payment_payload(Some(PaymentPayload {
            account_id: account_id.clone(),
        }))
        .build()?;

    service
        .send_notification(SendNotificationInput {
            account_id,
            payload_type: NotificationPayloadType::PaymentNotification,
            payload: &payload,
            only_touchpoints: None,
        })
        .await?;

    Ok(())
}
