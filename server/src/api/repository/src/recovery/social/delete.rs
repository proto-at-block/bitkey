use database::aws_sdk_dynamodb::types::AttributeValue;
use database::{
    aws_sdk_dynamodb::{
        error::ProvideErrorMetadata,
        types::{DeleteRequest, WriteRequest},
    },
    ddb::{try_from_items, try_to_attribute_val, DDBService, DatabaseError},
};
use time::format_description::well_known::Rfc3339;
use time::OffsetDateTime;
use tracing::{event, instrument, Level};
use types::{
    account::identifiers::AccountId,
    recovery::social::{challenge::SocialChallenge, relationship::RecoveryRelationship},
};

use super::{
    Repository, SocialRecoveryRow, CUSTOMER_IDX, CUSTOMER_IDX_PARTITION_KEY, PARTITION_KEY,
};

impl Repository {
    #[instrument(skip(self, relationship))]
    pub async fn delete_recovery_relationship(
        &self,
        relationship: &RecoveryRelationship,
    ) -> Result<(), DatabaseError> {
        let database_object = self.get_database_object();

        let condition_value = relationship
            .common_fields()
            .updated_at
            .format(&Rfc3339)
            .map_err(|err| {
                event!(Level::ERROR, "Could not format updated_at: {:?}", err);
                DatabaseError::PersistenceError(database_object)
            })?;

        self.connection
            .client
            .delete_item()
            .table_name(self.get_table_name().await?)
            .key(
                PARTITION_KEY,
                try_to_attribute_val(relationship.common_fields().id.clone(), database_object)?,
            )
            .condition_expression("updated_at = :updated_at")
            .expression_attribute_values(
                ":updated_at",
                try_to_attribute_val(condition_value, database_object)?,
            )
            .send()
            .await
            .map_err(|err| {
                let service_err = err.into_service_error();
                event!(
                    Level::ERROR,
                    "Could not delete recovery relationship: {service_err:?} with message: {:?}",
                    service_err.message()
                );
                DatabaseError::PersistenceError(database_object)
            })?;
        Ok(())
    }

    #[instrument(skip(self))]
    pub async fn delete_challenges_for_customer(
        &self,
        customer_account_id: &AccountId,
    ) -> Result<(), DatabaseError> {
        let table_name = self.get_table_name().await?;
        let database_object = self.get_database_object();

        let mut exclusive_start_key = None;

        loop {
            let item_output = self
                .connection
                .client
                .query()
                .table_name(table_name.clone())
                .index_name(CUSTOMER_IDX)
                .key_condition_expression(
                    format!("{} = :{}", CUSTOMER_IDX_PARTITION_KEY, CUSTOMER_IDX_PARTITION_KEY)
                )
                .expression_attribute_values(format!(":{}", CUSTOMER_IDX_PARTITION_KEY), try_to_attribute_val(customer_account_id, database_object)?)
                .set_exclusive_start_key(exclusive_start_key.clone())
                .limit(25)
                .send()
                .await
                .map_err(|err| {
                    let service_err = err.into_service_error();
                    event!(
                        Level::ERROR,
                        "Could not query social challenges customer index: {service_err:?} with message: {:?}",
                        service_err.message()
                    );
                    DatabaseError::FetchError(database_object)
                })?;

            let challenges = try_from_items::<_, SocialRecoveryRow>(
                item_output.items().to_owned(),
                database_object,
            )?
            .into_iter()
            .filter_map(|r| match r {
                SocialRecoveryRow::Challenge(challenge) => Some(challenge),
                _ => None,
            })
            .collect::<Vec<SocialChallenge>>();

            self.batch_delete_challenges(challenges).await?;

            if let Some(last_evaluated_key) = item_output.last_evaluated_key() {
                exclusive_start_key = Some(last_evaluated_key.to_owned());
            } else {
                break;
            }
        }

        Ok(())
    }

    #[instrument(skip(self))]
    pub async fn delete_row_by_partition_key(
        &self,
        partition_key: &AttributeValue,
        guard_before_date: &OffsetDateTime,
    ) -> Result<(), DatabaseError> {
        let table_name = self.get_table_name().await?;
        let database_object = self.get_database_object();

        let guard_before_date = guard_before_date
            .format(&Rfc3339)
            .map_err(|_| DatabaseError::DatetimeFormatError(database_object))?;

        self.connection
            .client
            .delete_item()
            .table_name(table_name)
            .key(PARTITION_KEY, partition_key.clone())
            .condition_expression("created_at < :date")
            .expression_attribute_values(
                ":date",
                try_to_attribute_val(guard_before_date, database_object)?,
            )
            .send()
            .await
            .map_err(|err| {
                let service_err = err.into_service_error();
                event!(
                    Level::ERROR,
                    "Could not delete row by partition key: {service_err:?} with message: {:?}",
                    service_err.message()
                );
                DatabaseError::PersistenceError(database_object)
            })?;

        Ok(())
    }

    #[instrument(skip(self))]
    async fn batch_delete_challenges(
        &self,
        challenges: Vec<SocialChallenge>,
    ) -> Result<(), DatabaseError> {
        let table_name = self.get_table_name().await?;
        let database_object = self.get_database_object();

        if !challenges.is_empty() {
            self.connection
                .client
                .batch_write_item()
                .request_items(
                    table_name.clone(),
                    challenges
                        .into_iter()
                        .try_fold(Vec::new(), |mut write_requests, c| {
                            write_requests.push(
                                WriteRequest::builder()
                                    .delete_request(
                                        DeleteRequest::builder()
                                            .key(
                                                PARTITION_KEY,
                                                try_to_attribute_val(
                                                    c.id.clone(),
                                                    database_object,
                                                )?,
                                            )
                                            .build()?,
                                    )
                                    .build(),
                            );

                            Ok::<Vec<WriteRequest>, DatabaseError>(write_requests)
                        })?,
                )
                .send()
                .await
                .map_err(|err| {
                    let service_err = err.into_service_error();
                    event!(
                        Level::ERROR,
                        "Could not delete social challenges: {service_err:?} with message: {:?}",
                        service_err.message()
                    );
                    DatabaseError::DeleteItemsError(database_object)
                })?;
        }

        Ok(())
    }
}
