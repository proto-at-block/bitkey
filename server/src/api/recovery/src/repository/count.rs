use account::entities::Factor;
use database::{
    aws_sdk_dynamodb::{error::ProvideErrorMetadata, types::AttributeValue},
    ddb::{try_from_items, try_to_attribute_val, DatabaseError, Repository},
};

use tracing::{event, instrument, Level};

use crate::entities::{RecoveryStatus, RecoveryType, WalletRecovery};

use super::RecoveryRepository;

pub struct PendingDelayNotifyCounts {
    pub lost_app_delay_incomplete: u64,
    pub lost_app_delay_complete: u64,
    pub lost_hw_delay_incomplete: u64,
    pub lost_hw_delay_complete: u64,
}

impl RecoveryRepository {
    #[instrument(skip(self))]
    pub async fn count_pending_delay_notify(
        &self,
    ) -> Result<PendingDelayNotifyCounts, DatabaseError> {
        let table_name = self.get_table_name().await?;
        let database_object = self.get_database_object();

        let recovery_type_attr: AttributeValue =
            try_to_attribute_val(RecoveryType::DelayAndNotify, database_object)?;
        let recovery_status_attr: AttributeValue =
            try_to_attribute_val(RecoveryStatus::Pending, database_object)?;

        let mut result = PendingDelayNotifyCounts {
            lost_app_delay_incomplete: 0,
            lost_app_delay_complete: 0,
            lost_hw_delay_incomplete: 0,
            lost_hw_delay_complete: 0,
        };
        let mut exclusive_start_key = None;

        loop {
            let item_output = self
                .connection
                .client
                .scan()
                .table_name(table_name.clone())
                .filter_expression("recovery_type = :recovery_type AND recovery_status = :rs")
                .expression_attribute_values(":recovery_type", recovery_type_attr.clone())
                .expression_attribute_values(":rs", recovery_status_attr.clone())
                .set_exclusive_start_key(exclusive_start_key.clone())
                .send()
                .await
                .map_err(|err| {
                    let service_err = err.into_service_error();
                    event!(
                        Level::ERROR,
                        "Could not scan recoveries: {service_err:?} with message: {:?}",
                        service_err.message()
                    );
                    DatabaseError::FetchError(database_object)
                })?;

            let items = item_output.items();
            let recoveries: Vec<WalletRecovery> =
                try_from_items(items.to_owned(), database_object)?;

            recoveries.into_iter().for_each(|r| {
                if let Some(requirements) = r.requirements.delay_notify_requirements.as_ref() {
                    match requirements.lost_factor {
                        Factor::App => {
                            if requirements.delay_end_time >= self.cur_time() {
                                result.lost_app_delay_incomplete += 1;
                            } else {
                                result.lost_app_delay_complete += 1;
                            }
                        }
                        Factor::Hw => {
                            if requirements.delay_end_time >= self.cur_time() {
                                result.lost_hw_delay_incomplete += 1;
                            } else {
                                result.lost_hw_delay_complete += 1;
                            }
                        }
                    }
                }
            });

            if let Some(last_evaluated_key) = item_output.last_evaluated_key() {
                exclusive_start_key = Some(last_evaluated_key.to_owned());
            } else {
                break;
            }
        }

        Ok(result)
    }
}
