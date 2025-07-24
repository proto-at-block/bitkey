use notification::{
    payloads::{
        privileged_action_canceled_delay_period::PrivilegedActionCanceledDelayPeriodPayload,
        privileged_action_canceled_oob_verification::PrivilegedActionCanceledOutOfBandVerificationPayload,
    },
    service::SendNotificationInput,
    NotificationPayloadBuilder, NotificationPayloadType,
};
use serde_json::Value;
use tracing::instrument;
use types::privileged_action::repository::{
    AuthorizationStrategyRecord, DelayAndNotifyRecord, OutOfBandRecord,
    PrivilegedActionInstanceRecord, RecordStatus,
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

        if delay_and_notify.status != RecordStatus::Pending {
            return Err(ServiceError::RecordDelayAndNotifyStatusConflict);
        }

        let updated_instance = PrivilegedActionInstanceRecord {
            authorization_strategy: AuthorizationStrategyRecord::DelayAndNotify(
                DelayAndNotifyRecord {
                    status: RecordStatus::Canceled,
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

    #[instrument(skip(self))]
    pub async fn cancel_pending_instance_by_web_auth_token(
        &self,
        web_auth_token: &str,
    ) -> Result<(), ServiceError> {
        let instance_record: PrivilegedActionInstanceRecord<Value> = self
            .privileged_action_repository
            .fetch_by_web_auth_token(web_auth_token)
            .await?;

        let AuthorizationStrategyRecord::OutOfBand(out_of_band) =
            instance_record.authorization_strategy
        else {
            return Err(ServiceError::RecordAuthorizationStrategyTypeConflict);
        };

        if out_of_band.status != RecordStatus::Pending {
            return Err(ServiceError::RecordOutofBandStatusConflict);
        }

        let updated_instance = PrivilegedActionInstanceRecord {
            authorization_strategy: AuthorizationStrategyRecord::OutOfBand(OutOfBandRecord {
                status: RecordStatus::Canceled,
                ..out_of_band
            }),
            ..instance_record
        };

        self.privileged_action_repository
            .persist(&updated_instance)
            .await?;

        self.notification_service
            .send_notification(SendNotificationInput {
                account_id: &updated_instance.account_id,
                payload_type:
                    NotificationPayloadType::PrivilegedActionCanceledOutOfBandVerification,
                payload: &NotificationPayloadBuilder::default()
                    .privileged_action_canceled_oob_verification_payload(Some(
                        PrivilegedActionCanceledOutOfBandVerificationPayload {
                            privileged_action_instance_id: updated_instance.id,
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
