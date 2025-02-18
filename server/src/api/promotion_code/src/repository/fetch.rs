use tracing::{event, instrument, Level};

use database::aws_sdk_dynamodb::error::ProvideErrorMetadata;
use database::ddb::{try_from_item, try_to_attribute_val, DatabaseError, Repository};

use super::{PromotionCodeRepository, CODE_IDX, CODE_IDX_PARTITION_KEY, PARTITION_KEY, SORT_KEY};
use crate::entities::{Code, CodeKey};

impl PromotionCodeRepository {
    #[instrument(skip(self))]
    pub(crate) async fn fetch(&self, key: &CodeKey) -> Result<Code, DatabaseError> {
        let table_name = self.get_table_name().await?;
        let database_object = self.get_database_object();

        self.connection
            .client
            .get_item()
            .table_name(table_name)
            .key(
                PARTITION_KEY,
                try_to_attribute_val(&key.unique_by, database_object)?,
            )
            .key(
                SORT_KEY,
                try_to_attribute_val(key.code_type, database_object)?,
            )
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
                event!(Level::WARN, "Promotion code not found for key: {:?}", key);
                DatabaseError::ObjectNotFound(database_object)
            })
            .and_then(|item| try_from_item(item, database_object))
    }

    #[instrument(skip(self))]
    pub(crate) async fn fetch_by_promotion_code(
        &self,
        code: &str,
    ) -> Result<Option<Code>, DatabaseError> {
        let table_name = self.get_table_name().await?;
        let database_object = self.get_database_object();

        self.connection
            .client
            .query()
            .table_name(table_name)
            .index_name(CODE_IDX)
            .key_condition_expression(format!("{CODE_IDX_PARTITION_KEY} = :code"))
            .expression_attribute_values(":code", try_to_attribute_val(code, database_object)?)
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
            .items()
            .first()
            .map(|item| try_from_item(item.to_owned(), database_object))
            .transpose()
    }
}
