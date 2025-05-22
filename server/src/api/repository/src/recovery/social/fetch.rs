use std::collections::HashMap;

use database::aws_sdk_dynamodb::types::AttributeValue;
use database::{
    aws_sdk_dynamodb::{error::ProvideErrorMetadata, operation::get_item::GetItemOutput},
    ddb::{try_from_item, try_from_items, try_to_attribute_val, DatabaseError, Repository},
};
use serde::Serialize;
use time::format_description::well_known::Rfc3339;
use time::OffsetDateTime;
use tracing::{event, instrument, Level};
use types::recovery::trusted_contacts::TrustedContactRole;
use types::{
    account::identifiers::AccountId,
    recovery::{
        backup::{Backup, ToRecoveryBackupPk},
        social::challenge::{SocialChallenge, SocialChallengeId},
        social::relationship::{RecoveryRelationship, RecoveryRelationshipId},
    },
};

use super::{
    SocialRecoveryRepository, SocialRecoveryRow, CODE_IDX, CODE_IDX_PARTITION_KEY, CUSTOMER_IDX,
    CUSTOMER_IDX_PARTITION_KEY, PARTITION_KEY, TRUSTED_CONTACT_IDX,
    TRUSTED_CONTACT_IDX_PARTITION_KEY, TRUSTED_CONTACT_IDX_SORT_KEY,
};

pub struct RecoveryRelationshipsForAccount {
    pub invitations: Vec<RecoveryRelationship>,
    pub endorsed_trusted_contacts: Vec<RecoveryRelationship>,
    pub unendorsed_trusted_contacts: Vec<RecoveryRelationship>,
    pub customers: Vec<RecoveryRelationship>,
}

impl SocialRecoveryRepository {
    async fn fetch(&self, partition_key: impl Serialize) -> Result<GetItemOutput, DatabaseError> {
        let table_name = self.get_table_name().await?;
        let database_object = self.get_database_object();

        self.get_connection()
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
    pub async fn fetch_recovery_relationship(
        &self,
        id: &RecoveryRelationshipId,
    ) -> Result<RecoveryRelationship, DatabaseError> {
        let database_object = self.get_database_object();

        let item_output = self.fetch(id).await?;

        if let Some(SocialRecoveryRow::Relationship(relationship)) = match item_output.item {
            Some(item) => Some(try_from_item::<_, SocialRecoveryRow>(
                item,
                database_object,
            )?),
            None => None,
        } {
            Ok(relationship)
        } else {
            event!(
                Level::WARN,
                "recovery relationship {id} not found in the database"
            );
            Err(DatabaseError::ObjectNotFound(database_object))
        }
    }

    #[instrument(skip(self))]
    pub async fn fetch_social_challenge(
        &self,
        id: &SocialChallengeId,
    ) -> Result<SocialChallenge, DatabaseError> {
        let database_object = self.get_database_object();

        let item_output = self.fetch(id).await?;

        if let Some(SocialRecoveryRow::Challenge(challenge)) = match item_output.item {
            Some(item) => Some(try_from_item::<_, SocialRecoveryRow>(
                item,
                database_object,
            )?),
            None => None,
        } {
            Ok(challenge)
        } else {
            event!(
                Level::WARN,
                "social challenge {id} not found in the database"
            );
            Err(DatabaseError::ObjectNotFound(database_object))
        }
    }

    #[instrument(skip(self))]
    pub async fn fetch_recovery_relationship_for_code(
        &self,
        code: &str,
    ) -> Result<RecoveryRelationship, DatabaseError> {
        let table_name = self.get_table_name().await?;
        let database_object = self.get_database_object();

        let item_output = self
            .get_connection()
            .client
            .query()
            .table_name(table_name)
            .index_name(CODE_IDX)
            .key_condition_expression(format!(
                "{} = :{}",
                CODE_IDX_PARTITION_KEY, CODE_IDX_PARTITION_KEY
            ))
            .expression_attribute_values(
                format!(":{}", CODE_IDX_PARTITION_KEY),
                try_to_attribute_val(code, database_object)?,
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
            })?;

        let items = item_output.items();
        let relationships =
            try_from_items::<_, SocialRecoveryRow>(items.to_owned(), database_object)?
                .into_iter()
                .filter_map(|r| match r {
                    SocialRecoveryRow::Relationship(relationship) => Some(relationship),
                    _ => None,
                })
                .collect::<Vec<RecoveryRelationship>>();

        if relationships.len() == 1 {
            return Ok(relationships[0].clone());
        } else if items.len() > 1 {
            return Err(DatabaseError::ObjectNotUnique(database_object));
        }

        return Err(DatabaseError::ObjectNotFound(database_object));
    }

    #[instrument(skip(self))]
    pub async fn fetch_optional_recovery_relationship_for_account_ids(
        &self,
        customer_account_id: &AccountId,
        trusted_contact_account_id: &AccountId,
        role: &TrustedContactRole,
    ) -> Result<Option<RecoveryRelationship>, DatabaseError> {
        let table_name = self.get_table_name().await?;
        let database_object = self.get_database_object();

        let item_output = self
            .get_connection()
            .client
            .query()
            .table_name(table_name)
            .index_name(TRUSTED_CONTACT_IDX)
            .key_condition_expression(format!(
                "{} = :{} and {} = :{}",
                TRUSTED_CONTACT_IDX_PARTITION_KEY,
                TRUSTED_CONTACT_IDX_PARTITION_KEY,
                TRUSTED_CONTACT_IDX_SORT_KEY,
                TRUSTED_CONTACT_IDX_SORT_KEY,
            ))
            .filter_expression("contains(trusted_contact_roles, :role)")
            .expression_attribute_values(
                format!(":{}", TRUSTED_CONTACT_IDX_PARTITION_KEY),
                try_to_attribute_val(trusted_contact_account_id, database_object)?,
            )
            .expression_attribute_values(
                format!(":{}", TRUSTED_CONTACT_IDX_SORT_KEY),
                try_to_attribute_val(customer_account_id, database_object)?,
            )
            .expression_attribute_values(":role", try_to_attribute_val(role, database_object)?)
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
            })?;

        let items = item_output.items();
        let relationships =
            try_from_items::<_, SocialRecoveryRow>(items.to_owned(), database_object)?
                .into_iter()
                .filter_map(|r| match r {
                    SocialRecoveryRow::Relationship(relationship) => Some(relationship),
                    _ => None,
                })
                .collect::<Vec<RecoveryRelationship>>();

        if relationships.len() == 1 {
            return Ok(Some(relationships[0].clone()));
        } else if items.len() > 1 {
            return Err(DatabaseError::ObjectNotUnique(database_object));
        }

        Ok(None)
    }

    #[instrument(skip(self))]
    pub async fn fetch_recovery_relationships_for_account(
        &self,
        account_id: &AccountId,
    ) -> Result<RecoveryRelationshipsForAccount, DatabaseError> {
        let table_name = self.get_table_name().await?;
        let database_object = self.get_database_object();

        let (
            mut invitations,
            mut endorsed_trusted_contacts,
            mut unendorsed_trusted_contacts,
            mut customers,
        ) = (Vec::new(), Vec::new(), Vec::new(), Vec::new());

        // Get relationships for which account is the customer / inviter
        let mut exclusive_start_key = None;

        loop {
            let item_output = self
                .get_connection()
                .client
                .query()
                .table_name(table_name.clone())
                .index_name(CUSTOMER_IDX)
                .key_condition_expression(
                    format!("{} = :{}", CUSTOMER_IDX_PARTITION_KEY, CUSTOMER_IDX_PARTITION_KEY)
                )
                .expression_attribute_values(format!(":{}", CUSTOMER_IDX_PARTITION_KEY), try_to_attribute_val(account_id, database_object)?)
                .set_exclusive_start_key(exclusive_start_key.clone())
                .send()
                .await
                .map_err(|err| {
                    let service_err = err.into_service_error();
                    event!(
                        Level::ERROR,
                        "Could not query recovery relationships customer index: {service_err:?} with message: {:?}",
                        service_err.message()
                    );
                    DatabaseError::FetchError(database_object)
                })?;

            let relationships = try_from_items::<_, SocialRecoveryRow>(
                item_output.items().to_owned(),
                database_object,
            )?
            .into_iter()
            .filter_map(|r| match r {
                SocialRecoveryRow::Relationship(relationship) => Some(relationship),
                _ => None,
            })
            .collect::<Vec<RecoveryRelationship>>();

            relationships.into_iter().for_each(|r| match r {
                RecoveryRelationship::Invitation(_) => invitations.push(r),
                RecoveryRelationship::Unendorsed(_) => unendorsed_trusted_contacts.push(r),
                RecoveryRelationship::Endorsed(_) => endorsed_trusted_contacts.push(r),
            });

            if let Some(last_evaluated_key) = item_output.last_evaluated_key() {
                exclusive_start_key = Some(last_evaluated_key.to_owned());
            } else {
                break;
            }
        }

        // Get relationships for which account is the Recovery Contact
        exclusive_start_key = None;

        loop {
            let item_output = self
                .get_connection()
                .client
                .query()
                .table_name(table_name.clone())
                .index_name(TRUSTED_CONTACT_IDX)
                .key_condition_expression(
                    format!("{} = :{}", TRUSTED_CONTACT_IDX_PARTITION_KEY, TRUSTED_CONTACT_IDX_PARTITION_KEY)
                )
                .expression_attribute_values(format!(":{}", TRUSTED_CONTACT_IDX_PARTITION_KEY), try_to_attribute_val(account_id, database_object)?)
                .set_exclusive_start_key(exclusive_start_key.clone())
                .send()
                .await
                .map_err(|err| {
                    let service_err = err.into_service_error();
                    event!(
                        Level::ERROR,
                        "Could not query recovery relationships Recovery Contact index: {service_err:?} with message: {:?}",
                        service_err.message()
                    );
                    DatabaseError::FetchError(database_object)
                })?;

            let relationships = try_from_items::<_, SocialRecoveryRow>(
                item_output.items().to_owned(),
                database_object,
            )?
            .into_iter()
            .filter_map(|r| match r {
                SocialRecoveryRow::Relationship(relationship) => Some(relationship),
                _ => None,
            })
            .collect::<Vec<RecoveryRelationship>>();

            customers.extend(relationships);

            if let Some(last_evaluated_key) = item_output.last_evaluated_key() {
                exclusive_start_key = Some(last_evaluated_key.to_owned());
            } else {
                break;
            }
        }

        Ok(RecoveryRelationshipsForAccount {
            invitations,
            endorsed_trusted_contacts,
            unendorsed_trusted_contacts,
            customers,
        })
    }

    #[instrument(skip(self))]
    pub async fn fetch_recovery_relationships_without_roles(
        &self,
    ) -> Result<Vec<RecoveryRelationship>, DatabaseError> {
        let table_name = self.get_table_name().await?;
        let database_object = self.get_database_object();

        let mut exclusive_start_key = None;
        let mut all_relationships = Vec::new();

        loop {
            let item_output = self
                .get_connection()
                .client
                .scan()
                .table_name(table_name.clone())
                .filter_expression("#type = :type AND attribute_not_exists(trusted_contact_roles)")
                .expression_attribute_values(
                    ":type",
                    try_to_attribute_val("Relationship", database_object)?,
                )
                .expression_attribute_names("#type", "_SocialRecoveryRow_type")
                .set_exclusive_start_key(exclusive_start_key.clone())
                .send()
                .await
                .map_err(|err| {
                    let service_err = err.into_service_error();
                    event!(
                        Level::ERROR,
                        "Could not scan recovery relationships: {service_err:?} with message: {:?}",
                        service_err.message()
                    );
                    DatabaseError::FetchError(database_object)
                })?;

            let relationships = try_from_items::<_, SocialRecoveryRow>(
                item_output.items().to_owned(),
                database_object,
            )?
            .into_iter()
            .filter_map(|r| match r {
                SocialRecoveryRow::Relationship(relationship) => Some(relationship),
                _ => None,
            })
            .collect::<Vec<RecoveryRelationship>>();

            all_relationships.extend(relationships);

            if let Some(last_evaluated_key) = item_output.last_evaluated_key() {
                exclusive_start_key = Some(last_evaluated_key.to_owned());
            } else {
                break;
            }
        }

        Ok(all_relationships)
    }

    #[instrument(skip(self))]
    pub async fn fetch_invalid_relationships_before_date(
        &self,
        date: &OffsetDateTime,
    ) -> Result<Vec<HashMap<String, AttributeValue>>, DatabaseError> {
        let table_name = self.get_table_name().await?;
        let database_object = self.get_database_object();

        let mut exclusive_start_key = None;
        let mut invalid_items = Vec::new();

        let rfc3339_date = date
            .format(&Rfc3339)
            .map_err(|_| DatabaseError::DatetimeFormatError(database_object))?;

        loop {
            let item_output = self
                .get_connection()
                .client
                .scan()
                .table_name(table_name.clone())
                .filter_expression("created_at < :date AND #type = :type")
                .expression_attribute_values(
                    ":date",
                    try_to_attribute_val(&rfc3339_date, database_object)?,
                )
                .expression_attribute_values(
                    ":type",
                    try_to_attribute_val("Relationship", database_object)?,
                )
                .expression_attribute_names("#type", "_SocialRecoveryRow_type")
                .set_exclusive_start_key(exclusive_start_key.clone())
                .send()
                .await
                .map_err(|err| {
                    let service_err = err.into_service_error();
                    event!(
                        Level::ERROR,
                        "Could not scan recovery relationships: {service_err:?} with message: {:?}",
                        service_err.message()
                    );
                    DatabaseError::FetchError(database_object)
                })?;

            let items = item_output.items().to_owned();

            for item in items {
                if try_from_item::<_, SocialRecoveryRow>(item.clone(), database_object).is_err() {
                    invalid_items.push(item);
                }
            }

            if let Some(last_evaluated_key) = item_output.last_evaluated_key() {
                exclusive_start_key = Some(last_evaluated_key.to_owned());
            } else {
                break;
            }
        }

        Ok(invalid_items)
    }

    #[instrument(skip(self))]
    pub async fn count_social_challenges_for_customer(
        &self,
        customer_account_id: &AccountId,
    ) -> Result<usize, DatabaseError> {
        let table_name = self.get_table_name().await?;
        let database_object = self.get_database_object();

        let mut exclusive_start_key = None;
        let mut count = 0;

        loop {
            let item_output = self
                .get_connection()
                .client
                .query()
                .table_name(table_name.clone())
                .index_name(CUSTOMER_IDX)
                .key_condition_expression(
                    format!("{} = :{}", CUSTOMER_IDX_PARTITION_KEY, CUSTOMER_IDX_PARTITION_KEY)
                )
                .expression_attribute_values(format!(":{}", CUSTOMER_IDX_PARTITION_KEY), try_to_attribute_val(customer_account_id, database_object)?)
                .set_exclusive_start_key(exclusive_start_key.clone())
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

            count += try_from_items::<_, SocialRecoveryRow>(
                item_output.items().to_owned(),
                database_object,
            )?
            .into_iter()
            .filter_map(|r| match r {
                SocialRecoveryRow::Challenge(challenge) => Some(challenge),
                _ => None,
            })
            .count();

            if let Some(last_evaluated_key) = item_output.last_evaluated_key() {
                exclusive_start_key = Some(last_evaluated_key.to_owned());
            } else {
                break;
            }
        }

        Ok(count)
    }

    #[instrument(skip(self))]
    pub async fn fetch_recovery_backup(
        &self,
        account_id: &AccountId,
    ) -> Result<Backup, DatabaseError> {
        let database_object = self.get_database_object();
        let id = account_id.to_recovery_backup_pk();

        let item_output = self.fetch(&id).await?;

        let backup = item_output
            .item
            .and_then(|item| try_from_item::<_, SocialRecoveryRow>(item, database_object).ok())
            .and_then(|row| match row {
                SocialRecoveryRow::Backup(backup) => Some(backup),
                _ => None,
            })
            .ok_or_else(|| {
                event!(
                    Level::WARN,
                    "Recovery backup {id} not found in the database"
                );
                DatabaseError::ObjectNotFound(database_object)
            })?;

        Ok(backup)
    }
}
