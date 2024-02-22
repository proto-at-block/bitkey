use account::error::AccountError;
use aws_sdk_sesv2::operation::send_email::SendEmailError;
use aws_sdk_sns::operation::publish::PublishError;
use bdk_utils::error::BdkUtilError;
use chain_indexer::ChainIndexerError;
use errors::ApiError;
use notification::NotificationError;
use thiserror::Error;
use types::account::identifiers::AccountId;

#[derive(Debug, Error)]
pub enum WorkerError {
    #[error("Account not found")]
    AccountNotFound,
    #[error("Error when performing account operation in worker: {0}")]
    AccountError(#[from] AccountError),
    #[error("Error when performing operation with account {0}: {1}")]
    AccountErrorWithId(AccountId, #[source] AccountError),
    #[error(transparent)]
    NotificationPayloadBuilderError(#[from] notification::NotificationPayloadBuilderError),
    #[error("SNS Publish Error")]
    SNSPublishError(#[from] PublishError),
    #[error("SES Publish Error")]
    SESPublishError(#[from] SendEmailError),
    #[error("Failed to setup SIGTERM handler: {0}")]
    SetupSigtermHandler(#[from] ctrlc::Error),
    #[error("Failed to parse or serialize SNSNotificationMessage with error: {0}")]
    SerdeSerialization(#[from] serde_json::Error),
    #[error("Failed to fetch notifications due to error")]
    FetchNotifications,
    #[error("Queue Error: {0}")]
    SQSError(#[from] queue::sqs::QueueError),
    #[error("Unable to get balance")]
    GetBalanceError(#[from] BdkUtilError),
    #[error("Couldn't retrieve blockchain data due to error: {0}")]
    ChainIndexerError(#[from] ChainIndexerError),
    #[error("Database error due to error: {0}")]
    DatabaseError(#[from] database::ddb::DatabaseError),
    #[error("Unable to retrieve block height")]
    BlockHeightError,
    #[error("Unable to format time: {0}")]
    FormatError(#[from] time::error::Format),
    #[error("Unable to serialize notification due to error: {0}")]
    NotificationError(#[from] NotificationError),
    #[error("API error due to error: {0}")]
    ApiError(#[from] ApiError),
    #[error("Notification Clients Error: {0}")]
    NotificationClientsError(#[from] notification::clients::error::NotificationClientsError),
    #[error("BIP34 error: {0}")]
    Bip34Error(#[from] bdk_utils::bdk::bitcoin::blockdata::block::Bip34Error),
    #[error("Failed to retrieve addresses from watch list: {0}")]
    AddressWatchListError(#[from] notification::address_repo::errors::Error),
    #[error("Touchpoint not found for account {0}")]
    TouchpointNotFound(AccountId),
    #[error("Failed to register metrics callback")]
    MetricsRegisterCallback,
    #[error("Incorrect touchpoint type")]
    IncorrectTouchpointType,
    #[error("Electrum client error: {0}")]
    ElectrumClientError(#[from] bdk_utils::bdk::electrum_client::Error),
}

impl From<WorkerError> for ApiError {
    fn from(val: WorkerError) -> Self {
        let err_msg = val.to_string();
        match val {
            WorkerError::AccountError(_)
            | WorkerError::AccountErrorWithId(_, _)
            | WorkerError::NotificationPayloadBuilderError(_)
            | WorkerError::SetupSigtermHandler(_)
            | WorkerError::SESPublishError(_)
            | WorkerError::SNSPublishError(_)
            | WorkerError::SerdeSerialization(_)
            | WorkerError::GetBalanceError(_)
            | WorkerError::FetchNotifications
            | WorkerError::SQSError(_)
            | WorkerError::ChainIndexerError(_)
            | WorkerError::DatabaseError(_)
            | WorkerError::BlockHeightError
            | WorkerError::FormatError(_)
            | WorkerError::NotificationError(_)
            | WorkerError::ApiError(_)
            | WorkerError::Bip34Error(_)
            | WorkerError::AddressWatchListError(_)
            | WorkerError::TouchpointNotFound(_)
            | WorkerError::NotificationClientsError(_)
            | WorkerError::MetricsRegisterCallback
            | WorkerError::ElectrumClientError(_)
            | WorkerError::IncorrectTouchpointType => {
                ApiError::GenericInternalApplicationError(err_msg)
            }
            WorkerError::AccountNotFound => ApiError::GenericNotFound(err_msg),
        }
    }
}
