use std::str::FromStr;

use bdk_utils::bdk::bitcoin::Address;
use futures::pin_mut;
use futures::stream::StreamExt;
use itertools::Itertools;

use bdk_utils::bdk::bitcoin::address::NetworkUnchecked;
use tracing::{event, instrument, Level};

use super::WorkerState;
use crate::error::WorkerError;
use crate::jobs::helpers::customer_address::{
    notify_customers_with_addresses, CustomerNotificationFeatureFlag, PaymentNotificationType,
};

#[instrument(skip(state))]
pub async fn handler(state: &WorkerState, sleep_duration_seconds: u64) -> Result<(), WorkerError> {
    // TODO: We should schedule events to trigger the job rather than using an infinite poll-loop
    // in a http handler: W-3245/scheduled-workers-refactor
    let sleep_duration = std::time::Duration::from_secs(sleep_duration_seconds);
    loop {
        let result = run_once(state).await;
        if let Err(e) = result {
            event!(Level::ERROR, "Failed to run mempool polling job: {e}")
        }
        tokio::time::sleep(sleep_duration).await;
    }
}

pub async fn run_once(state: &WorkerState) -> Result<(), WorkerError> {
    event!(Level::INFO, "Starting mempool polling job");

    let txs_stream = state.mempool_indexer_service.get_new_mempool_txs();
    pin_mut!(txs_stream);

    while let Some(txs) = txs_stream.next().await {
        let txs_batch = match txs {
            Ok(txs) => {
                event!(Level::INFO, "Yielded {} new txs from mempool", txs.len());
                txs
            }
            Err(e) => {
                event!(Level::ERROR, "Failed to get new mempool txs: {e}");
                continue;
            }
        };
        let addresses: Vec<Address<NetworkUnchecked>> = txs_batch
            .into_iter()
            .flat_map(|r| r.received)
            .unique()
            .filter_map(|output| {
                let Some(address) = &output.scriptpubkey_address else {
                    return None;
                };
                Address::<NetworkUnchecked>::from_str(address).ok()
            })
            .unique()
            .collect();
        event!(
            Level::INFO,
            "{} addresses found in unrecorded mempool txs",
            addresses.len()
        );
        notify_customers_with_addresses(
            state,
            addresses,
            PaymentNotificationType::Pending,
            Some(CustomerNotificationFeatureFlag::UnconfirmedMempoolTransaction),
        )
        .await?;
    }

    event!(
        Level::INFO,
        "Stream completed for new mempool txs, taking a look at expiring txs now"
    );
    let stale_txs_stream = state.mempool_indexer_service.get_stale_txs();
    pin_mut!(stale_txs_stream);

    // We can inform customers here if their transactions are in the mempool for longer than the expiry period.
    while let Some(records) = stale_txs_stream.next().await {
        match records {
            Ok(txs) => {
                event!(
                    Level::INFO,
                    "Yielded {} expiring txs that are still present in mempool",
                    txs.len()
                );
                txs
            }
            Err(e) => {
                event!(Level::ERROR, "Failed to get expiring txs from ddb: {e}");
                continue;
            }
        };
    }

    event!(Level::INFO, "Stream completed mempool polling job");
    Ok(())
}
