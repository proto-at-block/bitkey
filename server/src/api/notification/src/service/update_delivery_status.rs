use errors::ApiError;
use tracing::instrument;

use super::{Service, UpdateDeliveryStatusInput};

//TODO: Pull more functionality into this method to perform delivery of notifications
impl Service {
    #[instrument(skip(self))]
    pub async fn update_delivery_status(
        &self,
        input: UpdateDeliveryStatusInput,
    ) -> Result<(), ApiError> {
        self.notification_repo
            .update_delivery_status(input.composite_key, input.status)
            .await
            .map_err(|e| e.into())
    }
}
