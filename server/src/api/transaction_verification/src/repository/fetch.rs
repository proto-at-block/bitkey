use tracing::{event, instrument, Level};

use bdk_utils::bdk::bitcoin::Txid;
use database::aws_sdk_dynamodb::error::ProvideErrorMetadata;
use database::ddb::{try_from_item, try_to_attribute_val, DatabaseError, Repository};
use types::transaction_verification::entities::TransactionVerification;
use types::transaction_verification::TransactionVerificationId;

use super::{
    TransactionVerificationRepository, PARTITION_KEY, TXID_IDX, TXID_IDX_PARTITION_KEY,
    WEB_AUTH_TOKEN_IDX, WEB_AUTH_TOKEN_IDX_PARTITION_KEY,
};

impl TransactionVerificationRepository {
    #[instrument(skip(self))]
    pub(crate) async fn fetch(
        &self,
        key: &TransactionVerificationId,
    ) -> Result<TransactionVerification, DatabaseError> {
        let table_name = self.get_table_name().await?;
        let database_object = self.get_database_object();

        self.connection
            .client
            .get_item()
            .table_name(table_name)
            .key(PARTITION_KEY, try_to_attribute_val(key, database_object)?)
            .send()
            .await
            .map_err(|err| {
                let service_err = err.into_service_error();
                event!(
                    Level::ERROR,
                    "Could not query database: {service_err:?} with message: {:?}",
                    service_err.message()
                );
                DatabaseError::FetchError(database_object)
            })?
            .item
            .ok_or_else(|| {
                event!(
                    Level::WARN,
                    "Transaction verification not found for key: {:?}",
                    key
                );
                DatabaseError::ObjectNotFound(database_object)
            })
            .and_then(|item| try_from_item(item, database_object))
    }

    #[instrument(skip(self))]
    pub(crate) async fn fetch_by_web_auth_token(
        &self,
        token: String,
    ) -> Result<TransactionVerification, DatabaseError> {
        let table_name = self.get_table_name().await?;
        let database_object = self.get_database_object();

        self.connection
            .client
            .query()
            .table_name(table_name)
            .index_name(WEB_AUTH_TOKEN_IDX)
            .key_condition_expression(format!("{WEB_AUTH_TOKEN_IDX_PARTITION_KEY} = :val"))
            .expression_attribute_values(":val", try_to_attribute_val(&token, database_object)?)
            .limit(1)
            .send()
            .await
            .map_err(|err| {
                let service_err = err.into_service_error();
                event!(
                    Level::ERROR,
                    "Could not fetch transaction verification request: {service_err:?} with message: {:?}",
                    service_err.message()
                );
                DatabaseError::FetchError(database_object)
            })?
            .items
            .and_then(|items| items.into_iter().next())
            .ok_or(DatabaseError::ObjectNotFound(database_object))
            .and_then(|item| try_from_item(item, database_object))
    }

    #[instrument(skip(self))]
    pub(crate) async fn fetch_pending_by_txid(
        &self,
        txid: &Txid,
    ) -> Result<Option<TransactionVerification>, DatabaseError> {
        let table_name = self.get_table_name().await?;
        let database_object = self.get_database_object();
        let verification_status = try_to_attribute_val("PENDING", database_object)?;

        self.connection
            .client
            .query()
            .table_name(table_name)
            .index_name(TXID_IDX)
            .key_condition_expression(format!("{TXID_IDX_PARTITION_KEY} = :val"))
            .expression_attribute_values(":val", try_to_attribute_val(txid.to_string(), database_object)?)
            .filter_expression("verification_status = :verification_status".to_string())
            .expression_attribute_values(":verification_status", verification_status)
            .limit(1)
            .send()
            .await
            .map_err(|err| {
                let service_err = err.into_service_error();
                event!(
                    Level::ERROR,
                    "Could not fetch transaction verification request: {service_err:?} with message: {:?}",
                    service_err.message()
                );
                DatabaseError::FetchError(database_object)
            })?
            .items
            .and_then(|items| items.into_iter().next())
            .map(|item| try_from_item(item, database_object))
            .transpose()
    }
}
