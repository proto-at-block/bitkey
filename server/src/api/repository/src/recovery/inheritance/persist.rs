use std::collections::HashMap;
use std::convert::TryInto;

use database::{
    aws_sdk_dynamodb::{
        error::ProvideErrorMetadata,
        types::{builders::UpdateBuilder, AttributeValue, ConditionCheck, TransactWriteItem},
    },
    ddb::{
        try_to_attribute_val, try_to_item, DatabaseError, DatabaseObject, Repository, UpdateItemOp,
        Upsert as _,
    },
};
use time::{format_description::well_known::Rfc3339, OffsetDateTime};
use tracing::{event, instrument, Level};
use types::{
    account::identifiers::AccountId,
    recovery::{
        inheritance::{claim::InheritanceClaim, package::Package},
        trusted_contacts::TrustedContactRole,
    },
};

use super::{InheritanceRepository, InheritanceRow};
use crate::recovery::social::PARTITION_KEY as RELATIONSHIP_PARTITION_KEY;

const ENDORSED_VALUE: &str = "Endorsed";
const RELATIONSHIP_TYPE_NAME: &str = "_RecoveryRelationship_type";
const BENEFACTOR_ACCOUNT_ID_NAME: &str = "customer_account_id";

impl InheritanceRepository {
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

        self.get_connection()
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

    #[instrument(skip(self, claim))]
    pub async fn persist_inheritance_claim(
        &self,
        claim: &InheritanceClaim,
    ) -> Result<InheritanceClaim, DatabaseError> {
        let updated_common_fields = claim
            .common_fields()
            .with_updated_at(&OffsetDateTime::now_utc());

        let updated_claim = claim.with_common_fields(&updated_common_fields);

        let database_object = self.get_database_object();

        let item = try_to_item(
            InheritanceRow::Claim(updated_claim.clone()),
            database_object,
        )?;

        self.persist(item, claim.common_fields().updated_at).await?;

        Ok(updated_claim)
    }

    #[instrument(skip(self, packages))]
    pub async fn persist_packages(
        &self,
        benefactor_account_id: &AccountId,
        packages: Vec<Package>,
    ) -> Result<Vec<Package>, DatabaseError> {
        let database_object = self.get_database_object();
        let recovery_table_name = self
            .get_connection()
            .get_table_name(DatabaseObject::SocialRecovery)?;
        let table_name = self.get_table_name().await?;

        let mut transact_items: Vec<TransactWriteItem> = Vec::new();

        for package in packages.clone().iter() {
            let update_builder: UpdateBuilder = UpdateItemOp::new(
                self.get_connection()
                    .client
                    .try_upsert(InheritanceRow::Package(package.clone()), database_object)?,
            )
            .try_into()?;

            transact_items.push(
                TransactWriteItem::builder()
                    .update(
                        update_builder
                            .set_table_name(Some(table_name.clone()))
                            .build()?,
                    )
                    .build(),
            );

            transact_items.push(
                TransactWriteItem::builder()
                    .condition_check(
                        ConditionCheck::builder()
                            .table_name(recovery_table_name.clone())
                            .key(
                                RELATIONSHIP_PARTITION_KEY.to_string(),
                                try_to_attribute_val(package.recovery_relationship_id.to_string(), DatabaseObject::SocialRecovery)?,
                            )
                            .condition_expression("attribute_exists(partition_key) AND contains(trusted_contact_roles, :role) AND #recovery_relationship_type = :recovery_relationship_type AND #benefactor_account_id = :benefactor_account_id")
                            .expression_attribute_names(
                                "#recovery_relationship_type",
                                RELATIONSHIP_TYPE_NAME,
                            )
                            .expression_attribute_names(
                                "#benefactor_account_id",
                                BENEFACTOR_ACCOUNT_ID_NAME,
                            )
                            .expression_attribute_values(
                                ":role",
                                try_to_attribute_val(TrustedContactRole::Beneficiary, database_object)?,
                            )
                            .expression_attribute_values(
                                ":recovery_relationship_type",
                                try_to_attribute_val(ENDORSED_VALUE, database_object)?,
                            )
                            .expression_attribute_values(
                                ":benefactor_account_id",
                                try_to_attribute_val(benefactor_account_id, database_object)?,
                            )
                            .build()?)
                    .build());
        }

        if transact_items.is_empty() {
            return Ok(vec![]);
        }

        self.get_connection()
            .client
            .transact_write_items()
            .set_transact_items(Some(transact_items))
            .send()
            .await
            .map_err(|err| {
                let service_err = err.into_service_error();
                if service_err.is_transaction_canceled_exception() {
                    if let Some(message) = service_err.message() {
                        if message.contains("ConditionalCheckFailed") {
                            return DatabaseError::DependantObjectNotFound(database_object);
                        }
                    }
                }

                event!(
                    Level::ERROR,
                    "Could not persist to database: {service_err:?} with message: {:?}",
                    service_err.message()
                );

                DatabaseError::PersistenceError(database_object)
            })?;

        Ok(packages)
    }
}
