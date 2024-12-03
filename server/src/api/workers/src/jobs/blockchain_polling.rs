use bdk_utils::bdk::bitcoin::Address;
use itertools::Itertools;
use std::str::FromStr;

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
            event!(Level::ERROR, "Failed to run blockchain polling job: {e}")
        }
        event!(
            Level::INFO,
            "Sleeping for {} seconds",
            sleep_duration_seconds
        );
        tokio::time::sleep(sleep_duration).await;
        event!(Level::INFO, "Done sleeping");
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
        event!(
            Level::INFO,
            "Adding block {:?} to database",
            block.block_hash()
        );
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
    notify_customers_with_addresses(
        state,
        addresses,
        PaymentNotificationType::Confirmed,
        Some(CustomerNotificationFeatureFlag::ConfirmedTransaction),
    )
    .await?;
    event!(Level::INFO, "Ending blockchain polling job");
    Ok(())
}
