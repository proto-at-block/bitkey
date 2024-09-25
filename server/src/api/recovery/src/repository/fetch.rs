use std::collections::HashMap;

use database::{
    aws_sdk_dynamodb::{error::ProvideErrorMetadata, types::AttributeValue},
    ddb::{try_from_item, try_to_attribute_val, DatabaseError, Repository},
};
use time::{format_description::well_known::Rfc3339, OffsetDateTime};
use tracing::{event, instrument, Level};
use types::account::identifiers::AccountId;

use crate::{
    entities::{RecoveryStatus, RecoveryType, WalletRecovery},
    repository::{PARTITION_KEY, SORT_KEY},
};
use bdk_utils::bdk::bitcoin::secp256k1::PublicKey;

use super::{
    RecoveryRepository, APP_IDX_PARTITION_KEY, APP_TO_RECOVERY_IDX, HW_IDX_PARTITION_KEY,
    HW_TO_RECOVERY_IDX, RECOVERY_AUTHKEY_IDX_PARTITION_KEY, RECOVERY_AUTHKEY_TO_RECOVERY_IDX,
};

impl RecoveryRepository {
    #[instrument(skip(self))]
    async fn fetch_optional(
        &self,
        account_id: &AccountId,
        recovery_type: RecoveryType,
        recovery_status: RecoveryStatus,
    ) -> Result<Option<HashMap<String, AttributeValue>>, DatabaseError> {
        let table_name = self.get_table_name().await?;
        let database_object = self.get_database_object();

        let account_id_attr = try_to_attribute_val(account_id, database_object)?;
        let recovery_type_attr = try_to_attribute_val(recovery_type, database_object)?;
        let recovery_status_attr = try_to_attribute_val(recovery_status, database_object)?;

        let item_output = self
            .connection
            .client
            .query()
            .table_name(table_name)
            .key_condition_expression("account_id = :account_id")
            .filter_expression("recovery_type = :recovery_type AND recovery_status = :rs")
            .expression_attribute_values(":account_id", account_id_attr)
            .expression_attribute_values(":recovery_type", recovery_type_attr)
            .expression_attribute_values(":rs", recovery_status_attr)
            .scan_index_forward(false)
            .consistent_read(true)
            .send()
            .await
            .map_err(|err| {
                let service_err = err.into_service_error();
                event!(
                    Level::ERROR,
                    "Could not fetch recovery: {service_err:?} with message: {:?}",
                    service_err.message()
                );
                DatabaseError::FetchError(database_object)
            })?;

        let outputs = item_output
            .items
            .ok_or(DatabaseError::ObjectNotFound(database_object))?;

        Ok(outputs.into_iter().next())
    }

    pub async fn has_pending_recovery(
        &self,
        account_id: &AccountId,
        recovery_type: RecoveryType,
    ) -> Result<bool, DatabaseError> {
        let item = self
            .fetch_optional(account_id, recovery_type, RecoveryStatus::Pending)
            .await?;
        Ok(item.is_some())
    }

    pub async fn fetch_pending(
        &self,
        account_id: &AccountId,
        recovery_type: RecoveryType,
    ) -> Result<Option<WalletRecovery>, DatabaseError> {
        let item = self
            .fetch_optional(account_id, recovery_type, RecoveryStatus::Pending)
            .await?;
        if let Some(i) = item {
            let recovery = try_from_item(i, self.get_database_object())?;
            Ok(Some(recovery))
        } else {
            Ok(None)
        }
    }

    pub async fn fetch_by_status_since(
        &self,
        account_id: &AccountId,
        recovery_type: RecoveryType,
        recovery_status: RecoveryStatus,
        since: OffsetDateTime,
    ) -> Result<Option<WalletRecovery>, DatabaseError> {
        let item = self
            .fetch_optional(account_id, recovery_type, recovery_status)
            .await?;
        if let Some(i) = item {
            let recovery: WalletRecovery = try_from_item(i, self.get_database_object())?;
            if since < recovery.updated_at {
                return Ok(Some(recovery));
            }
        }
        Ok(None)
    }

    pub async fn fetch(
        &self,
        account_id: &AccountId,
        initiation_time: OffsetDateTime,
    ) -> Result<WalletRecovery, DatabaseError> {
        let table_name = self.get_table_name().await?;
        let database_object = self.get_database_object();

        let account_id_attr = try_to_attribute_val(account_id, database_object)?;
        let item_output = self
            .connection
            .client
            .get_item()
            .table_name(table_name)
            .key(PARTITION_KEY, account_id_attr)
            .key(
                SORT_KEY,
                AttributeValue::S(initiation_time.format(&Rfc3339).unwrap()),
            )
            .consistent_read(true)
            .send()
            .await
            .map_err(|err| {
                let service_err = err.into_service_error();
                event!(
                    Level::ERROR,
                    "Could not fetch recovery: {service_err:?} with message: {:?}",
                    service_err.message()
                );
                DatabaseError::FetchError(database_object)
            })?;

        let item = item_output
            .item
            .ok_or(DatabaseError::ObjectNotFound(database_object))?;
        let recovery = try_from_item(item, database_object)?;
        event!(Level::INFO, "Fetched recovery");
        Ok(recovery)
    }

    pub async fn fetch_optional_recovery_by_hardware_auth_pubkey(
        &self,
        hardware_auth_pubkey: PublicKey,
        recovery_status: RecoveryStatus,
    ) -> Result<Option<WalletRecovery>, DatabaseError> {
        let table_name = self.get_table_name().await?;
        let database_object = self.get_database_object();

        let pubkey_attr = try_to_attribute_val(hardware_auth_pubkey.to_string(), database_object)?;
        let recovery_status_attr = try_to_attribute_val(recovery_status, database_object)?;

        let item_output = self
            .connection
            .client
            .query()
            .table_name(table_name)
            .index_name(HW_TO_RECOVERY_IDX)
            .key_condition_expression("#P = :pubkey")
            .filter_expression("recovery_status = :recovery_status")
            .expression_attribute_names("#P", HW_IDX_PARTITION_KEY)
            .expression_attribute_values(":pubkey", pubkey_attr)
            .expression_attribute_values(":recovery_status", recovery_status_attr)
            .send()
            .await
            .map_err(|err| {
                let service_err = err.into_service_error();
                event!(
                    Level::ERROR,
                    "Could not fetch account: {service_err:?} with message: {:?}",
                    service_err.message()
                );
                DatabaseError::FetchError(database_object)
            })?;

        let item = item_output.items().first();
        if let Some(account_item) = item {
            try_from_item(account_item.clone(), database_object)
        } else {
            Ok(None)
        }
    }

    pub async fn fetch_optional_recovery_by_app_auth_pubkey(
        &self,
        app_auth_pubkey: PublicKey,
        recovery_status: RecoveryStatus,
    ) -> Result<Option<WalletRecovery>, DatabaseError> {
        let table_name = self.get_table_name().await?;
        let database_object = self.get_database_object();

        let pubkey_attr = try_to_attribute_val(app_auth_pubkey.to_string(), database_object)?;
        let recovery_status_attr = try_to_attribute_val(recovery_status, database_object)?;

        let item_output = self
            .connection
            .client
            .query()
            .table_name(table_name)
            .index_name(APP_TO_RECOVERY_IDX)
            .key_condition_expression("#P = :pubkey")
            .filter_expression("recovery_status = :recovery_status")
            .expression_attribute_names("#P", APP_IDX_PARTITION_KEY)
            .expression_attribute_values(":pubkey", pubkey_attr)
            .expression_attribute_values(":recovery_status", recovery_status_attr)
            .send()
            .await
            .map_err(|err| {
                let service_err = err.into_service_error();
                event!(
                    Level::ERROR,
                    "Could not fetch account: {service_err:?} with message: {:?}",
                    service_err.message()
                );
                DatabaseError::FetchError(database_object)
            })?;

        let item = item_output.items().first();
        if let Some(account_item) = item {
            try_from_item(account_item.clone(), database_object)
        } else {
            Ok(None)
        }
    }

    pub async fn fetch_optional_recovery_by_recovery_auth_pubkey(
        &self,
        recovery_auth_pubkey: PublicKey,
        recovery_status: RecoveryStatus,
    ) -> Result<Option<WalletRecovery>, DatabaseError> {
        let table_name = self.get_table_name().await?;
        let database_object = self.get_database_object();

        let pubkey_attr = try_to_attribute_val(recovery_auth_pubkey.to_string(), database_object)?;
        let recovery_status_attr = try_to_attribute_val(recovery_status, database_object)?;

        let item_output = self
            .connection
            .client
            .query()
            .table_name(table_name)
            .index_name(RECOVERY_AUTHKEY_TO_RECOVERY_IDX)
            .key_condition_expression("#P = :pubkey")
            .filter_expression("recovery_status = :recovery_status")
            .expression_attribute_names("#P", RECOVERY_AUTHKEY_IDX_PARTITION_KEY)
            .expression_attribute_values(":pubkey", pubkey_attr)
            .expression_attribute_values(":recovery_status", recovery_status_attr)
            .send()
            .await
            .map_err(|err| {
                let service_err = err.into_service_error();
                event!(
                    Level::ERROR,
                    "Could not fetch account: {service_err:?} with message: {:?}",
                    service_err.message()
                );
                DatabaseError::FetchError(database_object)
            })?;

        let item = item_output.items().first();
        if let Some(account_item) = item {
            try_from_item(account_item.clone(), database_object)
        } else {
            Ok(None)
        }
    }
}
