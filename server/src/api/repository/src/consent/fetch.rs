use database::{
    aws_sdk_dynamodb::{error::ProvideErrorMetadata, types::AttributeValue},
    ddb::{try_from_items, try_to_attribute_val, DatabaseError, Repository},
};
use tracing::{event, instrument, Level};
use types::{account::identifiers::AccountId, consent::Consent};

use super::ConsentRepository;

impl ConsentRepository {
    #[instrument(skip(self))]
    pub async fn fetch_for_account_id(
        &self,
        account_id: &AccountId,
    ) -> Result<Vec<Consent>, DatabaseError> {
        let table_name = self.get_table_name().await?;
        let database_object = self.get_database_object();

        let account_id_attr: AttributeValue =
            try_to_attribute_val(account_id.to_string(), self.get_database_object())?;

        let mut exclusive_start_key = None;
        let mut result = Vec::new();

        loop {
            let item_output = self.get_connection().client
                .scan()
                .table_name(table_name.clone())
                .filter_expression("partition_key = :partition_key")
                .expression_attribute_values(":partition_key", account_id_attr.clone())
                .set_exclusive_start_key(exclusive_start_key.clone())
                .send()
                .await
                .map_err(|err| {
                    let service_err = err.into_service_error();
                    event!(
                        Level::ERROR,
                        "Could not fetch consents for account id: {account_id} with err: {service_err:?} and message: {:?}",
                        service_err.message(),
                    );
                    DatabaseError::FetchError(database_object)
                })?;

            let items = item_output.items();
            let mut consents: Vec<Consent> = try_from_items(items.to_owned(), database_object)?;
            result.append(&mut consents);

            if let Some(last_evaluated_key) = item_output.last_evaluated_key() {
                exclusive_start_key = Some(last_evaluated_key.to_owned());
            } else {
                break;
            }
        }

        Ok(result)
    }
}
