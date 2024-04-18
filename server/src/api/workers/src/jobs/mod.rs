use account::service::Service as AccountService;
use chain_indexer::service::Service as ChainIndexerService;
use feature_flags::service::Service as FeatureFlagsService;
use notification::address_repo::AddressWatchlistTrait;
use notification::clients::{iterable::IterableMode, twilio::TwilioMode};
use notification::service::Service as NotificationService;
use notification_validation::NotificationValidationState;
use queue::sqs::SqsQueue;
use recovery::repository::Repository as RecoveryRepository;
use serde::Deserialize;

use crate::{ses::SESMode, sns::SNSMode};

pub mod blockchain_polling;
pub mod customer_notification;
pub mod metrics;
pub mod scheduled_notification;
pub mod unified_keyset_migration;

#[derive(Clone, Deserialize)]
pub struct Config {
    pub(crate) ses: SESMode,
    pub(crate) iterable: IterableMode,
    pub(crate) sns: SNSMode,
    pub(crate) twilio: TwilioMode,
}

#[derive(Clone)]
pub struct WorkerState {
    pub config: Config,
    pub notification_service: NotificationService,
    pub account_service: AccountService,
    pub recovery_service: RecoveryRepository,
    pub chain_indexer_service: ChainIndexerService,
    pub address_repo: Box<dyn AddressWatchlistTrait>,
    pub sqs: SqsQueue,
    pub feature_flags_service: FeatureFlagsService,
}

impl From<WorkerState> for NotificationValidationState {
    fn from(value: WorkerState) -> Self {
        NotificationValidationState::new(value.recovery_service)
    }
}
