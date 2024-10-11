use std::collections::HashMap;

use database::{
    aws_sdk_dynamodb::{
        error::ProvideErrorMetadata,
        types::{AttributeValue, PutRequest, WriteRequest},
    },
    ddb::{try_to_item, DatabaseError, Repository},
};
use tracing::{event, instrument, Level};

use crate::entities::Notification;

use super::NotificationRepository;

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
        let item = match n {
            Notification::Customer(n) => try_to_item(n.clone(), database_object)?,
            Notification::Scheduled(n) => try_to_item(n.clone(), database_object)?,
        };
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
            .into_iter()
            .map(|n| match n {
                Notification::Customer(n) => try_to_item(n, database_object),
                Notification::Scheduled(n) => try_to_item(n, database_object),
            })
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
