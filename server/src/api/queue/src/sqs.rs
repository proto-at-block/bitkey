use std::collections::HashMap;
use std::sync::Arc;
use std::sync::Mutex;

use aws_config::BehaviorVersion;
use aws_sdk_sqs::error::BuildError;
use aws_sdk_sqs::error::ProvideErrorMetadata;
use aws_sdk_sqs::operation::delete_message_batch::DeleteMessageBatchError;
use aws_sdk_sqs::operation::get_queue_attributes::GetQueueAttributesError;
use aws_sdk_sqs::operation::receive_message::ReceiveMessageError;
use aws_sdk_sqs::operation::send_message::SendMessageError;
use aws_sdk_sqs::types::DeleteMessageBatchRequestEntry;
use aws_sdk_sqs::types::Message;
use aws_sdk_sqs::types::QueueAttributeName;
use aws_sdk_sqs::Client as SQSClient;
use errors::ApiError;
use futures::future::join_all;
use serde::Deserialize;
use strum_macros::EnumString;
use thiserror::Error;
use tracing::event;
use tracing::instrument;
use tracing::Level;

const WAIT_TIME_SECONDS: i32 = 1;
const MAX_NUM_RECEIVE_MESSAGES: i32 = 10;

#[derive(Deserialize)]
pub struct Config {
    pub sqs: SQSMode,
}

#[derive(Deserialize, EnumString, Clone, PartialEq)]
#[serde(rename_all = "lowercase")]
pub enum SQSMode {
    Test,
    Environment,
}

#[derive(Clone)]
pub enum SqsQueue {
    Real(SQSClient),
    Test(Arc<Mutex<Vec<Message>>>),
}

impl SqsQueue {
    pub async fn new(config: Config) -> Self {
        match config.sqs {
            SQSMode::Environment => {
                let sdk_config = aws_config::load_defaults(BehaviorVersion::latest()).await;
                let client = SQSClient::new(&sdk_config);
                Self::Real(client)
            }
            SQSMode::Test => Self::Test(Arc::new(Mutex::new(Vec::new()))),
        }
    }

    #[instrument(skip(self))]
    pub async fn enqueue(&self, queue_url: &str, message: &str) -> Result<(), QueueError> {
        match self {
            Self::Real(client) => {
                if queue_url.is_empty() {
                    return Err(QueueError::InvalidQueueURL);
                }
                client
                    .send_message()
                    .queue_url(queue_url)
                    .message_body(message)
                    .send()
                    .await
                    .map_err(|e| {
                        let service_err = e.into_service_error();
                        event!(
                            Level::ERROR,
                            "Could not publish to queue url: {queue_url} due to error: {service_err:?} with message: {:?}",
                            service_err.message()
                        );
                        QueueError::SqsEnqueue(service_err)
                    })?;
                Ok(())
            }
            Self::Test(messages) => {
                messages
                    .lock()
                    .unwrap()
                    .push(Message::builder().body(message).build());
                Ok(())
            }
        }
    }

    #[instrument(skip(self))]
    pub async fn fetch_messages(&self, queue_url: &str) -> Result<Vec<Message>, QueueError> {
        match self {
            Self::Real(client) => {
                if queue_url.is_empty() {
                    return Err(QueueError::InvalidQueueURL);
                }
                let receive_messages_output = client
                    .receive_message()
                    .queue_url(queue_url)
                    .max_number_of_messages(MAX_NUM_RECEIVE_MESSAGES)
                    .wait_time_seconds(WAIT_TIME_SECONDS)
                    .send()
                    .await
                    .map_err(|e| {
                        let service_err = e.into_service_error();
                        event!(
                            Level::ERROR,
                            "Could not retrieve messages to queue url: {queue_url} due to error: {service_err:?} with error message: {:?}",
                            service_err.message()
                        );
                        QueueError::SqsReceive(service_err)
                    })?;

                let received_messages = receive_messages_output.messages().to_vec();
                Ok(received_messages)
            }
            Self::Test(v) => Ok(v.lock().unwrap().clone()),
        }
    }

    #[instrument(skip(self))]
    pub async fn delete_messages(
        &self,
        queue_url: &str,
        messages: Vec<Message>,
    ) -> Result<(), QueueError> {
        match self {
            Self::Real(client) => {
                if queue_url.is_empty() {
                    return Err(QueueError::InvalidQueueURL);
                }

                if messages.is_empty() {
                    return Ok(());
                }

                let delete_entries = messages
                    .iter()
                    .map(|message| {
                        DeleteMessageBatchRequestEntry::builder()
                            .id(message.message_id.clone().unwrap_or_default())
                            .receipt_handle(message.receipt_handle.clone().unwrap_or_default())
                            .build()
                    })
                    .collect::<Result<Vec<DeleteMessageBatchRequestEntry>, BuildError>>()?;
                client.delete_message_batch().queue_url(queue_url).set_entries(delete_entries.into()).send().await.map_err(|e| {
                    let service_err = e.into_service_error();
                    event!(
                        Level::ERROR,
                        "Could not delete messages to queue url: {queue_url} due to error: {service_err:?} with error message: {:?}",
                        service_err.message()
                    );
                    QueueError::SqsDelete(service_err)
                })?;
                Ok(())
            }
            Self::Test(messages) => {
                messages.lock().unwrap().clear();
                Ok(())
            }
        }
    }

    #[instrument(skip(self))]
    pub async fn fetch_number_of_messages(
        &self,
        queue_urls: Vec<String>,
    ) -> Result<HashMap<String, u64>, QueueError> {
        match self {
            Self::Real(client) => {
                let fetch_num_messages_for_queue = |queue_url: String| async move {
                    let attribute = QueueAttributeName::ApproximateNumberOfMessages;
                    let output = client
                        .get_queue_attributes()
                        .queue_url(&queue_url)
                        .attribute_names(attribute.clone())
                        .send()
                        .await
                        .map_err(|e| {
                            let service_err = e.into_service_error();
                            QueueError::SqsFetchAttributes(service_err)
                        })?;
                    let attribute_value = output
                        .attributes
                        .ok_or(QueueError::SqsQueueAttributeNotFound)?
                        .get(&attribute)
                        .ok_or(QueueError::SqsQueueAttributeNotFound)?
                        .to_owned();

                    Ok::<(String, u64), QueueError>((
                        queue_url,
                        attribute_value.parse::<u64>().map_err(|_| {
                            QueueError::SqsQueueAttributeParse(attribute_value.to_owned())
                        })?,
                    ))
                };

                let fetch_futures = queue_urls.into_iter().map(fetch_num_messages_for_queue);
                join_all(fetch_futures)
                    .await
                    .into_iter()
                    .collect::<Result<HashMap<String, u64>, QueueError>>()
            }
            Self::Test(_) => Ok(queue_urls
                .into_iter()
                .map(|queue_url| (queue_url, 0))
                .collect::<HashMap<String, u64>>()),
        }
    }

    pub async fn update_test_messages(&self, new_messages: Vec<Message>) -> Result<(), QueueError> {
        match self {
            Self::Real(_client) => Ok(()),
            Self::Test(messages) => {
                let mut messages = messages.lock().unwrap();
                messages.clear();
                messages.extend(new_messages);
                Ok(())
            }
        }
    }
}

#[derive(Debug, Error)]
pub enum QueueError {
    #[error("Failed to enqueue message onto SQS")]
    SqsEnqueue(#[from] SendMessageError),
    #[error("Failed to build message to call SQS")]
    SqsBuildError(#[from] BuildError),
    #[error("Failed to receive message from SQS")]
    SqsReceive(#[from] ReceiveMessageError),
    #[error("Failed to delete message from SQS")]
    SqsDelete(#[from] DeleteMessageBatchError),
    #[error("Failed to fetch queue attributes from SQS")]
    SqsFetchAttributes(#[from] GetQueueAttributesError),
    #[error("Failed to fetch attribute from SQS")]
    SqsQueueAttributeNotFound,
    #[error("Failed to parse attribute with value {0} from SQS")]
    SqsQueueAttributeParse(String),
    #[error("Invalid queue url")]
    InvalidQueueURL,
}

impl From<QueueError> for ApiError {
    fn from(val: QueueError) -> Self {
        let err_msg = val.to_string();
        match val {
            QueueError::SqsEnqueue(_)
            | QueueError::SqsReceive(_)
            | QueueError::SqsDelete(_)
            | QueueError::SqsBuildError(_)
            | QueueError::InvalidQueueURL
            | QueueError::SqsFetchAttributes(_)
            | QueueError::SqsQueueAttributeNotFound
            | QueueError::SqsQueueAttributeParse(_) => {
                ApiError::GenericInternalApplicationError(err_msg)
            }
        }
    }
}
