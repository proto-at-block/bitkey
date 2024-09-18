use notification::{
    payloads::privileged_action_canceled_delay_period::PrivilegedActionCanceledDelayPeriodPayload,
    service::SendNotificationInput, NotificationPayloadBuilder, NotificationPayloadType,
};
use serde_json::Value;
use tracing::instrument;
use types::privileged_action::repository::{
    AuthorizationStrategyRecord, DelayAndNotifyRecord, DelayAndNotifyStatus,
    PrivilegedActionInstanceRecord,
};

use super::{error::ServiceError, Service};

#[derive(Debug)]
pub struct CancelPendingDelayAndNotifyInstanceByTokenInput {
    pub cancellation_token: String,
}

impl Service {
    #[instrument(skip(self, input))]
    pub async fn cancel_pending_delay_and_notify_instance_by_token(
        &self,
        input: CancelPendingDelayAndNotifyInstanceByTokenInput,
    ) -> Result<(), ServiceError> {
        let instance_record: PrivilegedActionInstanceRecord<Value> = self
            .privileged_action_repository
            .fetch_by_cancellation_token(input.cancellation_token)
            .await?;

        let AuthorizationStrategyRecord::DelayAndNotify(delay_and_notify) =
            instance_record.authorization_strategy
        else {
            return Err(ServiceError::RecordAuthorizationStrategyTypeConflict);
        };

        if delay_and_notify.status != DelayAndNotifyStatus::Pending {
            return Err(ServiceError::RecordDelayAndNotifyStatusConflict);
        }

        let updated_instance = PrivilegedActionInstanceRecord {
            authorization_strategy: AuthorizationStrategyRecord::DelayAndNotify(
                DelayAndNotifyRecord {
                    status: DelayAndNotifyStatus::Canceled,
                    ..delay_and_notify
                },
            ),
            ..instance_record
        };

        self.privileged_action_repository
            .persist(&updated_instance)
            .await?;

        let account = &self
            .account_repository
            .fetch(&updated_instance.account_id)
            .await?;

        self.notification_service
            .send_notification(SendNotificationInput {
                account_id: &updated_instance.account_id,
                payload_type: NotificationPayloadType::PrivilegedActionCanceledDelayPeriod,
                payload: &NotificationPayloadBuilder::default()
                    .privileged_action_canceled_delay_period_payload(Some(
                        PrivilegedActionCanceledDelayPeriodPayload {
                            privileged_action_instance_id: updated_instance.id,
                            account_type: account.into(),
                            privileged_action_type: updated_instance.privileged_action_type,
                        },
                    ))
                    .build()?,
                only_touchpoints: None,
            })
            .await?;

        Ok(())
    }
}
