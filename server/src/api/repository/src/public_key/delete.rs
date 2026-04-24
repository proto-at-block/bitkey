use database::ddb::{try_to_attribute_val, DatabaseError, Repository};
use tracing::{event, instrument, Level};
use types::account::identifiers::AccountId;

use super::{PublicKeyRepository, PARTITION_KEY};

impl PublicKeyRepository {
    /// Delete a public key record if it is still owned by the expected account.
    ///
    /// Returns `Ok(true)` when the row was deleted or already absent.
    /// Returns `Ok(false)` when the row exists for a different account.
    #[instrument(skip(self))]
    pub async fn delete_public_key_if_owned_by_account(
        &self,
        public_key: &str,
        account_id: &AccountId,
    ) -> Result<bool, DatabaseError> {
        let database_object = self.get_database_object();

        let result = self
            .get_connection()
            .client
            .delete_item()
            .table_name(self.get_table_name().await?)
            .key(
                PARTITION_KEY,
                try_to_attribute_val(public_key, database_object)?,
            )
            .condition_expression(
                format!("attribute_not_exists({PARTITION_KEY}) OR account_id = :account_id"),
            )
            .expression_attribute_values(
                ":account_id",
                try_to_attribute_val(account_id, database_object)?,
            )
            .send()
            .await;

        match result {
            Ok(_) => Ok(true),
            Err(err) => {
                let service_err = err.into_service_error();
                if service_err.is_conditional_check_failed_exception() {
                    Ok(false)
                } else {
                    event!(
                        Level::ERROR,
                        "Failed to delete public key entry: {service_err:?}"
                    );
                    Err(DatabaseError::PersistenceError(database_object))
                }
            }
        }
    }
}
