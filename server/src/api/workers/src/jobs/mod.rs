use account::service::Service as AccountService;

use bdk_utils::metrics::MonitoredElectrumNode;
use chain_indexer::service::Service as ChainIndexerService;
use feature_flags::service::Service as FeatureFlagsService;
use mempool_indexer::service::Service as MempoolIndexerService;
use notification::address_repo::AddressWatchlistTrait;
use notification::clients::{iterable::IterableMode, twilio::TwilioMode};
use notification::service::Service as NotificationService;
use notification_validation::NotificationValidationState;
use queue::sqs::SqsQueue;
use recovery::repository::Repository as RecoveryRepository;
use repository::privileged_action::Repository as PrivilegedActionRepository;
use serde::Deserialize;

use crate::{ses::SESMode, sns::SNSMode};

pub mod blockchain_polling;
pub mod coin_grinder;
pub mod customer_notification;
mod helpers;
pub mod mempool_polling;
pub mod metrics;
pub mod scheduled_notification;
pub mod unified_keyset_migration;

#[derive(Clone, Deserialize)]
pub struct Config {
    pub(crate) ses: SESMode,
    pub(crate) iterable: IterableMode,
    pub(crate) sns: SNSMode,
    pub(crate) twilio: TwilioMode,
    pub(crate) monitored_electrum_nodes: Vec<MonitoredElectrumNode>,
}

#[derive(Clone)]
pub struct WorkerState {
    pub config: Config,
    pub notification_service: NotificationService,
    pub account_service: AccountService,
    pub recovery_service: RecoveryRepository,
    pub chain_indexer_service: ChainIndexerService,
    pub mempool_indexer_service: MempoolIndexerService,
    pub address_repo: Box<dyn AddressWatchlistTrait>,
    pub sqs: SqsQueue,
    pub feature_flags_service: FeatureFlagsService,
    pub privileged_action_repository: PrivilegedActionRepository,
}

impl From<WorkerState> for NotificationValidationState {
    fn from(value: WorkerState) -> Self {
        NotificationValidationState::new(value.recovery_service, value.privileged_action_repository)
    }
}
