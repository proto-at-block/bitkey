use std::collections::HashSet;

use bdk_utils::bdk::bitcoin::{Network, Txid};
use database::{
    aws_sdk_dynamodb::types::{AttributeValue, Select::SpecificAttributes},
    ddb::{try_from_items, try_to_attribute_val, DatabaseError, Repository},
};
use serde::Deserialize;
use time::{Duration, OffsetDateTime};
use tracing::{event, instrument, Level};

use super::{MempoolIndexerRepository, NETWORK_EXPIRING_IDX, PARTITION_KEY};
use crate::entities::TransactionRecord;

const EXPIRY_UPDATE_WINDOW_MINS: i64 = 20;

#[derive(Debug, Deserialize)]
struct FetchTransactionId {
    tx_id: Txid,
}

impl MempoolIndexerRepository {
    #[instrument(skip(self))]
    pub(crate) async fn fetch_recorded_transaction_ids(
        &self,
        network: Network,
    ) -> Result<HashSet<Txid>, DatabaseError> {
        let table_name = self.get_table_name().await?;
        let database_object = self.get_database_object();

        let network_attr: AttributeValue =
            try_to_attribute_val(network.to_string(), self.get_database_object())?;

        let now = OffsetDateTime::now_utc();
        let now_attr: AttributeValue =
            try_to_attribute_val(now.unix_timestamp(), self.get_database_object())?;

        let mut exclusive_start_key = None;
        let mut result = HashSet::new();

        loop {
            let item_output = self
                .connection
                .client
                .query()
                .index_name(NETWORK_EXPIRING_IDX)
                .table_name(table_name.clone())
                .key_condition_expression("network = :network AND expiring_at >= :now")
                .expression_attribute_values(":network", network_attr.clone())
                .expression_attribute_values(":now", now_attr.clone())
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

    #[instrument(skip(self))]
    pub async fn fetch_expiring_transactions(
        &self,
        network: Network,
        expiring_after: OffsetDateTime,
    ) -> Result<HashSet<TransactionRecord>, DatabaseError> {
        let table_name = self.get_table_name().await?;
        let database_object = self.get_database_object();

        let expiring_before =
            OffsetDateTime::now_utc() + Duration::minutes(EXPIRY_UPDATE_WINDOW_MINS);
        let network_attr: AttributeValue =
            try_to_attribute_val(network.to_string(), self.get_database_object())?;
        let expiring_after_attr: AttributeValue =
            try_to_attribute_val(expiring_after.unix_timestamp(), self.get_database_object())?;
        let expiring_before_attr: AttributeValue =
            try_to_attribute_val(expiring_before.unix_timestamp(), self.get_database_object())?;

        let mut exclusive_start_key = None;
        let mut result = HashSet::new();

        event!(
            Level::INFO,
            "Fetched expiring txs between {} and {}",
            expiring_after.unix_timestamp(),
            expiring_before.unix_timestamp()
        );
        // This should never happen
        if expiring_after >= expiring_before {
            return Ok(result);
        }

        loop {
            let item_output = self
                .connection
                .client
                .query()
                .index_name(NETWORK_EXPIRING_IDX)
                .table_name(table_name.clone())
                .key_condition_expression("network = :network AND expiring_at BETWEEN :expiring_after AND :expiring_before")
                .expression_attribute_values(":network", network_attr.clone())
                .expression_attribute_values(":expiring_after", expiring_after_attr.clone())
                .expression_attribute_values(":expiring_before", expiring_before_attr.clone())
                .set_exclusive_start_key(exclusive_start_key.clone())
                .send()
                .await
                .map_err(|err| {
                    let service_err = err.into_service_error();
                    event!(
                        Level::ERROR,
                        "Could not fetch expiring tx ids with err: {service_err:?} and message: {:?}",
                        service_err,
                    );
                    DatabaseError::FetchError(database_object)
                })?;

            result.extend(try_from_items(
                item_output.items().to_owned(),
                database_object,
            )?);
            if let Some(last_evaluated_key) = item_output.last_evaluated_key() {
                exclusive_start_key = Some(last_evaluated_key.to_owned());
            } else {
                break;
            }
        }
        Ok(result)
    }
}
