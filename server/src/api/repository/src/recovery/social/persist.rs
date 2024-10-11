use std::collections::HashMap;

use database::{
    aws_sdk_dynamodb::{
        error::ProvideErrorMetadata,
        types::{AttributeValue, ReturnValue},
    },
    ddb::{
        try_from_item, try_to_attribute_val, try_to_item, DatabaseError, Repository, Upsert as _,
    },
    serde_dynamo::Item,
};
use time::{format_description::well_known::Rfc3339, OffsetDateTime};
use tracing::{event, instrument, Level};
use types::recovery::{
    backup::Backup,
    social::{challenge::SocialChallenge, relationship::RecoveryRelationship},
};

use super::{SocialRecoveryRepository, SocialRecoveryRow};

impl SocialRecoveryRepository {
    async fn persist(
        &self,
        item: HashMap<String, AttributeValue>,
        updated_at: OffsetDateTime,
    ) -> Result<(), DatabaseError> {
        let database_object = self.get_database_object();

        let condition_value = updated_at.format(&Rfc3339).map_err(|err| {
            event!(Level::ERROR, "Could not format updated_at: {:?}", err);
            DatabaseError::PersistenceError(database_object)
        })?;

        self.connection
            .client
            .put_item()
            .table_name(self.get_table_name().await?)
            .set_item(Some(item))
            .condition_expression("attribute_not_exists(updated_at) OR updated_at = :updated_at")
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
                    "Could not persist to database: {service_err:?} with message: {:?}",
                    service_err.message()
                );
                DatabaseError::PersistenceError(database_object)
            })?;

        Ok(())
    }

    #[instrument(skip(self, relationship))]
    pub async fn persist_recovery_relationship(
        &self,
        relationship: &RecoveryRelationship,
    ) -> Result<RecoveryRelationship, DatabaseError> {
        let updated_common_fields = relationship
            .common_fields()
            .with_updated_at(&OffsetDateTime::now_utc());

        let updated_relationship = relationship.with_common_fields(&updated_common_fields);

        let database_object = self.get_database_object();

        let item = try_to_item(
            SocialRecoveryRow::Relationship(updated_relationship.clone()),
            database_object,
        )?;

        self.persist(item, relationship.common_fields().updated_at)
            .await?;

        Ok(updated_relationship)
    }

    #[instrument(skip(self, challenge))]
    pub async fn persist_social_challenge(
        &self,
        challenge: &SocialChallenge,
    ) -> Result<SocialChallenge, DatabaseError> {
        let updated_challenge = challenge.with_updated_at(OffsetDateTime::now_utc());

        let database_object = self.get_database_object();

        let item = try_to_item(
            SocialRecoveryRow::Challenge(updated_challenge.clone()),
            database_object,
        )?;

        self.persist(item, challenge.updated_at).await?;

        Ok(updated_challenge)
    }

    pub async fn persist_recovery_backup(&self, backup: &Backup) -> Result<Backup, DatabaseError> {
        let database_object = self.get_database_object();
        let table_name = self.get_table_name().await?;

        let upsert_output = self
            .connection
            .client
            .try_upsert(SocialRecoveryRow::Backup(backup.clone()), database_object)?
            .set_return_values(Some(ReturnValue::AllNew))
            .table_name(&table_name)
            .send()
            .await
            .map_err(|err| {
                let service_err = err.into_service_error();
                event!(
                    Level::ERROR,
                    "Could not persist to database: {service_err:?} with message: {:?}",
                    service_err.message()
                );

                DatabaseError::PersistenceError(database_object)
            })?;

        let item_attrs = upsert_output
            .attributes()
            .ok_or(DatabaseError::PersistenceError(database_object))?;

        try_from_item(Item::from(item_attrs.clone()), database_object)
    }
}
