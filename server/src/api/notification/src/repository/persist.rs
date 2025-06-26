use std::collections::HashMap;

use database::{
    aws_sdk_dynamodb::{
        error::ProvideErrorMetadata,
        types::{AttributeValue, PutRequest, WriteRequest},
    },
    ddb::{try_to_item, DatabaseError, DatabaseObject, Repository},
};
use time::{Duration, OffsetDateTime};
use tracing::{event, instrument, Level};

use crate::entities::Notification;

use super::NotificationRepository;

const TTL: Duration = Duration::days(31);

impl NotificationRepository {
    /// Persists notification agnostic of type
    ///
    /// ### Arguments
    ///
    /// * `notification` - A wrapper type around either the Customer or Scheduled Notification we're persisting
    ///
    #[instrument(skip(self))]
    pub async fn persist_notification(&self, n: &Notification) -> Result<(), DatabaseError> {
        let table_name = self.get_table_name().await?;
        let database_object = self.get_database_object();
        let item = try_to_item_with_ttl(n, database_object)?;
        self.connection
            .client
            .put_item()
            .table_name(table_name)
            .set_item(Some(item))
            .send()
            .await
            .map_err(|err| {
                let service_err = err.into_service_error();
                event!(
                    Level::ERROR,
                    "Could not persist notification {:?}: {service_err:?} with message: {:?}",
                    n,
                    service_err.message()
                );
                DatabaseError::PersistenceError(self.get_database_object())
            })?;
        Ok(())
    }

    /// Persists a set of notifications agnostic of type
    ///
    /// ### Arguments
    ///
    /// * `notifications` - A vector of notifications, the Notification wrapper type is around either Customer or Scheduled Notification we're persisting
    ///
    #[instrument(skip(self))]
    pub async fn persist_notifications(
        &self,
        notifications: Vec<Notification>,
    ) -> Result<(), DatabaseError> {
        if notifications.is_empty() {
            return Ok(());
        }

        let table_name = self.get_table_name().await?;
        let database_object = self.get_database_object();
        let items = notifications
            .iter()
            .map(|n| try_to_item_with_ttl(n, database_object))
            .collect::<Result<Vec<HashMap<String, AttributeValue>>, DatabaseError>>()?;

        let request_items = items
            .into_iter()
            .map(|item| {
                let put_request = PutRequest::builder().set_item(Some(item)).build();
                match put_request {
                    Ok(put_request) => Ok(WriteRequest::builder()
                        .set_put_request(Some(put_request))
                        .build()),
                    Err(err) => Err(err.into()),
                }
            })
            .collect::<Result<Vec<WriteRequest>, DatabaseError>>()?;
        self.connection
            .client
            .batch_write_item()
            .request_items(table_name, request_items)
            .send()
            .await
            .map_err(|err| {
                let service_err = err.into_service_error();
                event!(
                    Level::ERROR,
                    "Could not persist notifications: {service_err:?} with message: {:?}",
                    service_err.message()
                );
                DatabaseError::PersistenceError(database_object)
            })?;
        Ok(())
    }
}

fn try_to_item_with_ttl(
    notification: &Notification,
    database_object: DatabaseObject,
) -> Result<HashMap<String, AttributeValue>, DatabaseError> {
    let (mut item, last_relevant): (HashMap<String, AttributeValue>, OffsetDateTime) =
        match notification {
            Notification::Customer(n) => (try_to_item(n, database_object)?, n.updated_at),
            Notification::Scheduled(n) => (try_to_item(n, database_object)?, n.execution_date_time),
        };

    let expiring_at = last_relevant + TTL;

    item.insert(
        "expiring_at".to_string(),
        AttributeValue::N(expiring_at.unix_timestamp().to_string()),
    );

    Ok(item)
}
