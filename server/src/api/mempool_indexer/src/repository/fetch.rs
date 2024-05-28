use std::collections::HashSet;

use bdk_utils::bdk::bitcoin::{Network, Txid};
use database::{
    aws_sdk_dynamodb::types::{AttributeValue, Select::SpecificAttributes},
    ddb::{try_from_items, try_to_attribute_val, DDBService, DatabaseError},
};
use serde::Deserialize;
use tracing::{event, instrument, Level};

use super::Repository;
use crate::repository::{NETWORK_TX_IDS_IDX, PARTITION_KEY};

#[derive(Debug, Deserialize)]
struct FetchTransactionId {
    tx_id: Txid,
}

impl Repository {
    #[instrument(skip(self))]
    pub(crate) async fn fetch_recorded_transaction_ids(
        &self,
        network: Network,
    ) -> Result<HashSet<Txid>, DatabaseError> {
        let table_name = self.get_table_name().await?;
        let database_object = self.get_database_object();

        let network_attr: AttributeValue =
            try_to_attribute_val(network.to_string(), self.get_database_object())?;

        let mut exclusive_start_key = None;
        let mut result = HashSet::new();

        loop {
            let item_output = self
                .connection
                .client
                .query()
                .index_name(NETWORK_TX_IDS_IDX)
                .table_name(table_name.clone())
                .key_condition_expression("network = :network")
                .expression_attribute_values(":network", network_attr.clone())
                .select(SpecificAttributes)
                .projection_expression(PARTITION_KEY)
                .set_exclusive_start_key(exclusive_start_key.clone())
                .send()
                .await
                .map_err(|err| {
                    let service_err = err.into_service_error();
                    event!(
                        Level::ERROR,
                        "Could not fetch seen tx ids with err: {service_err:?} and message: {:?}",
                        service_err,
                    );
                    DatabaseError::FetchError(database_object)
                })?;

            let tx_ids_wrapper: Vec<FetchTransactionId> =
                try_from_items(item_output.items().to_owned(), database_object)?;
            result.extend(tx_ids_wrapper.into_iter().map(|tx| tx.tx_id));

            if let Some(last_evaluated_key) = item_output.last_evaluated_key() {
                exclusive_start_key = Some(last_evaluated_key.to_owned());
            } else {
                break;
            }
        }
        Ok(result)
    }
}
