use aws_config::BehaviorVersion;
use aws_sdk_sns::error::ProvideErrorMetadata;

use aws_sdk_sns::Client as SNSClient;

use notification::metrics as notification_metrics;
use notification::push::SNSPushPayload;
use serde::Deserialize;
use strum_macros::EnumString;
use tracing::event;
use tracing::instrument;
use tracing::Level;

use crate::error::WorkerError;

#[derive(Deserialize, EnumString, Clone)]
#[serde(rename_all = "lowercase")]
pub enum SNSMode {
    Test,
    Environment,
}

pub enum SendPushNotification {
    Real(SNSClient),
    Test,
}

impl SendPushNotification {
    pub async fn new(mode: SNSMode) -> Self {
        match mode {
            SNSMode::Environment => {
                let sdk_config = aws_config::load_defaults(BehaviorVersion::latest()).await;
                let client = SNSClient::new(&sdk_config);
                Self::Real(client)
            }
            SNSMode::Test => Self::Test,
        }
    }

    #[instrument(skip(self))]
    pub async fn send(
        &self,
        device_arn: &str,
        payload: &SNSPushPayload,
    ) -> Result<(), WorkerError> {
        match self {
            Self::Real(client) => {
                client
                    .publish()
                    .target_arn(device_arn)
                    .message_structure("json")
                    .message(payload.to_sns_message())
                    .send()
                    .await
                    .map_or_else(|e| {
                        let service_err = e.into_service_error();
                        let msg = format!("Notification lambda could not publish push notification to topic arn: {} due to error kind: {:?} with message: {:?}", device_arn, service_err, service_err.message());

                        // These are expected when the user either disables push notifications
                        //   or uninstalls the application, so don't record it as an attempt or
                        //   a failure.
                        // TODO: (W-4532) figure out if we should be pruning disabled endpoints
                        if service_err.is_endpoint_disabled_exception() {
                            event!(Level::INFO, msg);
                            Ok(())
                        } else {
                            event!(Level::ERROR, msg);
                            notification_metrics::SNS_PUBLISH_ATTEMPT.add(1, &[]);
                            notification_metrics::SNS_PUBLISH_FAILURE.add(1, &[]);
                            Err(WorkerError::SNSPublishError(service_err))
                        }
                    },|_| {
                        notification_metrics::SNS_PUBLISH_ATTEMPT.add(1, &[]);
                        Ok(())
                    })
            }
            Self::Test => Ok(()),
        }
    }
}
