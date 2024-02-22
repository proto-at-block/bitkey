use errors::ApiError;
use tracing::instrument;

use super::{PersistCustomerNotificationsInput, PersistScheduledNotificationsInput, Service};

impl Service {
    #[instrument(skip(self))]
    pub async fn persist_scheduled_notifications(
        &self,
        input: PersistScheduledNotificationsInput,
    ) -> Result<(), ApiError> {
        self.notification_repo
            .persist_notifications(input.notifications.into_iter().map(|n| n.into()).collect())
            .await?;
        Ok(())
    }

    #[instrument(skip(self))]
    pub async fn persist_customer_notifications(
        &self,
        input: PersistCustomerNotificationsInput,
    ) -> Result<(), ApiError> {
        self.notification_repo
            .persist_notifications(input.notifications.into_iter().map(|n| n.into()).collect())
            .await?;
        Ok(())
    }
}
