use errors::ApiError;
use tracing::instrument;

use crate::entities::{CustomerNotification, ScheduledNotification};

use super::{FetchForAccountInput, FetchForCompositeKeyInput, Service};

impl Service {
    #[instrument(skip(self))]
    pub async fn fetch_scheduled_for_account(
        &self,
        input: FetchForAccountInput,
    ) -> Result<Vec<ScheduledNotification>, ApiError> {
        self.notification_repo
            .fetch_scheduled_for_account_id(&input.account_id)
            .await
            .map_err(|e| e.into())
    }

    #[instrument(skip(self))]
    pub async fn fetch_pending(
        &self,
        input: FetchForCompositeKeyInput,
    ) -> Result<Option<CustomerNotification>, ApiError> {
        self.notification_repo
            .fetch_pending(input.composite_key)
            .await
            .map_err(|e| e.into())
    }

    #[instrument(skip(self))]
    pub async fn fetch_customer_for_account(
        &self,
        input: FetchForAccountInput,
    ) -> Result<Vec<CustomerNotification>, ApiError> {
        self.notification_repo
            .fetch_customer_for_account_id_and_payload_type(&input.account_id, None)
            .await
            .map_err(|e| e.into())
    }
}
