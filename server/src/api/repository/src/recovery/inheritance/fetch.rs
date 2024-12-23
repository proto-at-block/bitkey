use std::collections::HashMap;

use database::{
    aws_sdk_dynamodb::{
        error::ProvideErrorMetadata,
        operation::get_item::GetItemOutput,
        types::{AttributeValue, KeysAndAttributes},
    },
    ddb::{try_from_item, try_from_items, try_to_attribute_val, DatabaseError, Repository},
};
use serde::Serialize;
use tracing::{event, instrument, Level};
use types::recovery::{
    inheritance::{
        claim::{InheritanceClaim, InheritanceClaimId},
        package::{Package, ToPackagePk},
    },
    social::relationship::RecoveryRelationshipId,
};

use super::{
    InheritanceRepository, InheritanceRow, PARTITION_KEY, RECOVERY_RELATIONSHIP_ID_IDX,
    RECOVERY_RELATIONSHIP_ID_IDX_PARTITION_KEY,
};

const INHERITANCE_CLAIM_PENDING_STATUS: &str = "PENDING";

impl InheritanceRepository {
    async fn fetch(&self, partition_key: impl Serialize) -> Result<GetItemOutput, DatabaseError> {
        let table_name = self.get_table_name().await?;
        let database_object = self.get_database_object();

        self.connection
            .client
            .get_item()
            .table_name(table_name)
            .key(
                PARTITION_KEY,
                try_to_attribute_val(partition_key, database_object)?,
            )
            .consistent_read(true)
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
            })
    }

    #[instrument(skip(self))]
    pub async fn fetch_inheritance_claim(
        &self,
        id: &InheritanceClaimId,
    ) -> Result<InheritanceClaim, DatabaseError> {
        let database_object = self.get_database_object();

        let item_output = self.fetch(id).await?;

        if let Some(InheritanceRow::Claim(claim)) = match item_output.item {
            Some(item) => Some(try_from_item::<_, InheritanceRow>(item, database_object)?),
            None => None,
        } {
            Ok(claim)
        } else {
            event!(Level::WARN, "claim {id} not found in the database");
            Err(DatabaseError::ObjectNotFound(database_object))
        }
    }

    #[instrument(skip(self))]
    pub async fn fetch_pending_claims_for_recovery_relationship_ids(
        &self,
        recovery_relationship_ids: Vec<RecoveryRelationshipId>,
    ) -> Result<Vec<InheritanceClaim>, DatabaseError> {
        let table_name = self.get_table_name().await?;
        let database_object = self.get_database_object();
        let base_query = self
            .connection
            .client
            .query()
            .table_name(table_name)
            .index_name(RECOVERY_RELATIONSHIP_ID_IDX)
            .key_condition_expression(format!(
                "{} = :{}",
                RECOVERY_RELATIONSHIP_ID_IDX_PARTITION_KEY,
                RECOVERY_RELATIONSHIP_ID_IDX_PARTITION_KEY
            ))
            .filter_expression("#status = :status")
            .expression_attribute_names("#status", "status")
            .expression_attribute_values(
                ":status",
                AttributeValue::S(INHERITANCE_CLAIM_PENDING_STATUS.to_string()),
            );

        let mut claims = Vec::new();
        for recovery_relationship_id in recovery_relationship_ids {
            let item_output = base_query.clone()
                .expression_attribute_values(format!(":{}", RECOVERY_RELATIONSHIP_ID_IDX_PARTITION_KEY), try_to_attribute_val(recovery_relationship_id, database_object)?)
                .send()
                .await
                .map_err(|err| {
                    let service_err = err.into_service_error();
                    event!(
                        Level::ERROR,
                        "Could not query inheritance claim with recovery relationship id index: {service_err:?} with message: {:?}",
                        service_err.message()
                    );
                    DatabaseError::FetchError(database_object)
                })?;
            let Some(inheritance_item) = item_output.items().first() else {
                continue;
            };
            let inheritance_claim: InheritanceClaim =
                try_from_item(inheritance_item.clone(), database_object)?;
            claims.push(inheritance_claim);
        }
        Ok(claims)
    }

    #[instrument(skip(self))]
    pub async fn fetch_claims_for_recovery_relationship_id(
        &self,
        recovery_relationship_id: &RecoveryRelationshipId,
    ) -> Result<Vec<InheritanceClaim>, DatabaseError> {
        let table_name = self.get_table_name().await?;
        let database_object = self.get_database_object();

        let mut exclusive_start_key = None;
        let mut claims = Vec::new();

        loop {
            let item_output = self
                .connection
                .client
                .query()
                .table_name(table_name.clone())
                .index_name(RECOVERY_RELATIONSHIP_ID_IDX)
                .key_condition_expression(
                    format!("{} = :{}", RECOVERY_RELATIONSHIP_ID_IDX_PARTITION_KEY, RECOVERY_RELATIONSHIP_ID_IDX_PARTITION_KEY)
                )
                .expression_attribute_values(format!(":{}", RECOVERY_RELATIONSHIP_ID_IDX_PARTITION_KEY), try_to_attribute_val(recovery_relationship_id, database_object)?)
                .set_exclusive_start_key(exclusive_start_key.clone())
                .send()
                .await
                .map_err(|err| {
                    let service_err = err.into_service_error();
                    event!(
                        Level::ERROR,
                        "Could not query inheritance claim recovery relationship id index: {service_err:?} with message: {:?}",
                        service_err.message()
                    );
                    DatabaseError::FetchError(database_object)
                })?;

            claims.extend(
                try_from_items::<_, InheritanceRow>(
                    item_output.items().to_owned(),
                    database_object,
                )?
                .into_iter()
                .filter_map(|r| match r {
                    InheritanceRow::Claim(c) => Some(c),
                    _ => None,
                }),
            );

            if let Some(last_evaluated_key) = item_output.last_evaluated_key() {
                exclusive_start_key = Some(last_evaluated_key.to_owned());
            } else {
                break;
            }
        }

        Ok(claims)
    }

    #[instrument(skip(self))]
    pub async fn count_claims_for_recovery_relationship_id(
        &self,
        recovery_relationship_id: &RecoveryRelationshipId,
    ) -> Result<usize, DatabaseError> {
        Ok(self
            .fetch_claims_for_recovery_relationship_id(recovery_relationship_id)
            .await?
            .len())
    }

    #[instrument(skip(self))]
    pub async fn fetch_packages_by_relationship_id(
        &self,
        recovery_relationship_ids: &Vec<RecoveryRelationshipId>,
    ) -> Result<Vec<Package>, DatabaseError> {
        let table_name = self.get_table_name().await?;
        let database_object = self.get_database_object();
        let mut payload_rows = Vec::new();

        let mut query_builder = KeysAndAttributes::builder();
        for id in recovery_relationship_ids {
            query_builder = query_builder.keys(HashMap::from([(
                PARTITION_KEY.to_string(),
                try_to_attribute_val(id.to_package_pk(), database_object)?,
            )]));
        }

        let item_output = self
            .connection
            .client
            .batch_get_item()
            .request_items(
                table_name.clone(),
                query_builder.build()?,
            )
            .send()
            .await
            .map_err(|err| {
                let service_err = err.into_service_error();
                event!(
                    Level::ERROR,
                    "Could not query beneficiary package recovery relationship id index: {service_err:?} with message: {:?}",
                    service_err.message()
                );
                DatabaseError::FetchError(database_object)
            })?;

        if let Some(items) = item_output.responses() {
            items
                .clone()
                .entry(table_name.clone())
                .or_default()
                .iter()
                .for_each(|item| {
                    payload_rows.extend(
                        try_from_item::<_, InheritanceRow>(item.to_owned(), database_object)
                            .into_iter()
                            .filter_map(|r| match r {
                                InheritanceRow::Package(b) => Some(b),
                                _ => None,
                            }),
                    );
                });
        }

        Ok(payload_rows)
    }
}
