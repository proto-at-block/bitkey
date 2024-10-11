use std::{
    collections::HashMap,
    env,
    sync::{Arc, RwLock},
};

use bdk_utils::bdk::electrum_client::ElectrumApi;
use bdk_utils::get_electrum_client;
use bdk_utils::{
    metrics::{
        self as bdk_utils_metrics, MonitoredElectrumNode, ELECTRUM_NETWORK_KEY,
        ELECTRUM_PROVIDER_KEY, ELECTRUM_URI_KEY,
    },
    ElectrumRpcUris,
};
use instrumentation::metrics::{factory::ObservableCallbackRegistry, KeyValue};
use notification::{
    metrics as notification_metrics, EMAIL_QUEUE_ENV_VAR, PUSH_QUEUE_ENV_VAR, SMS_QUEUE_ENV_VAR,
};
use recovery::metrics as recovery_metrics;
use tracing::{event, instrument, Level};
use types::account::entities::Factor;
use types::notification::NotificationChannel;

use super::WorkerState;
use crate::error::WorkerError;

// This job is a global singleton intended to gather and emit metrics that are meaningful
//   as absolute system-wide measurements. For example, the total number of currently-pending
//   delay-notify recoveries.

pub struct MeasurementsCache {
    recovery_delay_notify_lost_app_delay_incomplete: Arc<RwLock<u64>>,
    recovery_delay_notify_lost_app_delay_complete: Arc<RwLock<u64>>,
    recovery_delay_notify_lost_hw_delay_incomplete: Arc<RwLock<u64>>,
    recovery_delay_notify_lost_hw_delay_complete: Arc<RwLock<u64>>,
    customer_notification_push_queue_num_messages: Arc<RwLock<u64>>,
    customer_notification_email_queue_num_messages: Arc<RwLock<u64>>,
    customer_notification_sms_queue_num_messages: Arc<RwLock<u64>>,
    electrum_tip_height_by_node: Arc<RwLock<HashMap<MonitoredElectrumNode, u64>>>,
}

// This cache holds the "current" value of each measurement. The job performs the necessary work
//   to determine their values (e.g. queries databases and counts) and updates the cache once per
//   run; currently configured to be every 5 minutes, since this work may be expensive.
impl MeasurementsCache {
    fn new() -> Self {
        Self {
            recovery_delay_notify_lost_app_delay_incomplete: Arc::new(RwLock::new(0)),
            recovery_delay_notify_lost_app_delay_complete: Arc::new(RwLock::new(0)),
            recovery_delay_notify_lost_hw_delay_incomplete: Arc::new(RwLock::new(0)),
            recovery_delay_notify_lost_hw_delay_complete: Arc::new(RwLock::new(0)),
            customer_notification_push_queue_num_messages: Arc::new(RwLock::new(0)),
            customer_notification_email_queue_num_messages: Arc::new(RwLock::new(0)),
            customer_notification_sms_queue_num_messages: Arc::new(RwLock::new(0)),
            electrum_tip_height_by_node: Arc::new(RwLock::new(HashMap::new())),
        }
    }

    // The callbacks registered here with OTEL are invoked once per metrics reporting cycle; currently
    //   configured to be every 15 seconds globally. They simply read the most recent measurement from
    //   the cache and emit that value. We decoupled the reporting from the gathering of the metrics
    //   so that we don't need to either do expensive work more frequently or reduce the global metrics
    //   reporting period.
    fn register_callbacks(&self, state: &WorkerState) -> Result<(), WorkerError> {
        let recovery_delay_notify_lost_app_delay_incomplete =
            self.recovery_delay_notify_lost_app_delay_incomplete.clone();
        recovery_metrics::FACTORY
            .register_callback(
                recovery_metrics::DELAY_NOTIFY_PENDING.to_owned(),
                move || {
                    recovery_delay_notify_lost_app_delay_incomplete
                        .read()
                        .unwrap()
                        .to_owned()
                },
                &[
                    KeyValue::new(recovery_metrics::LOST_FACTOR_KEY, Factor::App.to_string()),
                    KeyValue::new(recovery_metrics::DELAY_COMPLETE_KEY, false),
                ],
            )
            .map_err(|_| WorkerError::MetricsRegisterCallback)?;

        let recovery_delay_notify_lost_app_delay_complete =
            self.recovery_delay_notify_lost_app_delay_complete.clone();
        recovery_metrics::FACTORY
            .register_callback(
                recovery_metrics::DELAY_NOTIFY_PENDING.to_owned(),
                move || {
                    recovery_delay_notify_lost_app_delay_complete
                        .read()
                        .unwrap()
                        .to_owned()
                },
                &[
                    KeyValue::new(recovery_metrics::LOST_FACTOR_KEY, Factor::App.to_string()),
                    KeyValue::new(recovery_metrics::DELAY_COMPLETE_KEY, true),
                ],
            )
            .map_err(|_| WorkerError::MetricsRegisterCallback)?;

        let recovery_delay_notify_lost_hw_delay_incomplete =
            self.recovery_delay_notify_lost_hw_delay_incomplete.clone();
        recovery_metrics::FACTORY
            .register_callback(
                recovery_metrics::DELAY_NOTIFY_PENDING.to_owned(),
                move || {
                    recovery_delay_notify_lost_hw_delay_incomplete
                        .read()
                        .unwrap()
                        .to_owned()
                },
                &[
                    KeyValue::new(recovery_metrics::LOST_FACTOR_KEY, Factor::Hw.to_string()),
                    KeyValue::new(recovery_metrics::DELAY_COMPLETE_KEY, false),
                ],
            )
            .map_err(|_| WorkerError::MetricsRegisterCallback)?;

        let recovery_delay_notify_lost_hw_delay_complete =
            self.recovery_delay_notify_lost_hw_delay_complete.clone();
        recovery_metrics::FACTORY
            .register_callback(
                recovery_metrics::DELAY_NOTIFY_PENDING.to_owned(),
                move || {
                    recovery_delay_notify_lost_hw_delay_complete
                        .read()
                        .unwrap()
                        .to_owned()
                },
                &[
                    KeyValue::new(recovery_metrics::LOST_FACTOR_KEY, Factor::Hw.to_string()),
                    KeyValue::new(recovery_metrics::DELAY_COMPLETE_KEY, true),
                ],
            )
            .map_err(|_| WorkerError::MetricsRegisterCallback)?;

        let customer_notification_push_queue_num_messages =
            self.customer_notification_push_queue_num_messages.clone();
        notification_metrics::FACTORY
            .register_callback(
                notification_metrics::NOTIFICATION_QUEUE_NUM_MESSAGES.to_owned(),
                move || {
                    customer_notification_push_queue_num_messages
                        .read()
                        .unwrap()
                        .to_owned()
                },
                &[KeyValue::new(
                    notification_metrics::CUSTOMER_NOTIFICATION_TYPE_KEY,
                    NotificationChannel::Push.to_string(),
                )],
            )
            .map_err(|_| WorkerError::MetricsRegisterCallback)?;

        let customer_notification_email_queue_num_messages =
            self.customer_notification_email_queue_num_messages.clone();
        notification_metrics::FACTORY
            .register_callback(
                notification_metrics::NOTIFICATION_QUEUE_NUM_MESSAGES.to_owned(),
                move || {
                    customer_notification_email_queue_num_messages
                        .read()
                        .unwrap()
                        .to_owned()
                },
                &[KeyValue::new(
                    notification_metrics::CUSTOMER_NOTIFICATION_TYPE_KEY,
                    NotificationChannel::Email.to_string(),
                )],
            )
            .map_err(|_| WorkerError::MetricsRegisterCallback)?;

        let customer_notification_sms_queue_num_messages =
            self.customer_notification_sms_queue_num_messages.clone();
        notification_metrics::FACTORY
            .register_callback(
                notification_metrics::NOTIFICATION_QUEUE_NUM_MESSAGES.to_owned(),
                move || {
                    customer_notification_sms_queue_num_messages
                        .read()
                        .unwrap()
                        .to_owned()
                },
                &[KeyValue::new(
                    notification_metrics::CUSTOMER_NOTIFICATION_TYPE_KEY,
                    NotificationChannel::Sms.to_string(),
                )],
            )
            .map_err(|_| WorkerError::MetricsRegisterCallback)?;

        for node in state.config.monitored_electrum_nodes.iter() {
            let electrum_tip_height_by_node = self.electrum_tip_height_by_node.clone();
            let _node = node.clone();
            bdk_utils_metrics::FACTORY
                .register_callback(
                    bdk_utils_metrics::ELECTRUM_TIP_HEIGHT.to_owned(),
                    move || {
                        electrum_tip_height_by_node
                            .read()
                            .unwrap()
                            .get(&_node)
                            .unwrap_or(&0)
                            .to_owned()
                    },
                    &[
                        KeyValue::new(ELECTRUM_URI_KEY, node.uri.to_owned()),
                        KeyValue::new(ELECTRUM_NETWORK_KEY, node.network.to_string()),
                        KeyValue::new(ELECTRUM_PROVIDER_KEY, node.provider.to_owned()),
                    ],
                )
                .map_err(|_| WorkerError::MetricsRegisterCallback)?;
        }

        Ok(())
    }
}

#[instrument(skip(state))]
pub async fn handler(state: &WorkerState, sleep_duration_seconds: u64) -> Result<(), WorkerError> {
    // TODO: We should schedule events to trigger the job rather than using an infinite poll-loop
    // in a http handler: W-3245/scheduled-workers-refactor
    let sleep_duration = std::time::Duration::from_secs(sleep_duration_seconds);
    let measurements_cache = MeasurementsCache::new();

    let mut callbacks_registered = false;

    loop {
        let result = run_once(state, &measurements_cache).await;
        if let Err(e) = result {
            event!(Level::ERROR, "Failed to run metrics job: {e}")
        } else if !callbacks_registered {
            // Only register the metrics callbacks once we know the measurements have been taken
            //   once successfully
            measurements_cache.register_callbacks(state)?;
            callbacks_registered = true;
        }
        tokio::time::sleep(sleep_duration).await;
    }
}

pub async fn run_once(
    state: &WorkerState,
    measurements_cache: &MeasurementsCache,
) -> Result<(), WorkerError> {
    event!(Level::INFO, "Starting metrics job");

    // Do the work to calculate the measurements and update the cache

    let pending_delay_notify_counts = state.recovery_service.count_pending_delay_notify().await?;
    let push_queue_url: String = env::var(PUSH_QUEUE_ENV_VAR).unwrap_or_default();
    let email_queue_url: String = env::var(EMAIL_QUEUE_ENV_VAR).unwrap_or_default();
    let sms_queue_url: String = env::var(SMS_QUEUE_ENV_VAR).unwrap_or_default();
    let queue_urls = vec![
        push_queue_url.clone(),
        email_queue_url.clone(),
        sms_queue_url.clone(),
    ];
    let messages_per_queue = state.sqs.fetch_number_of_messages(queue_urls).await?;
    {
        let mut recovery_delay_notify_lost_app_delay_incomplete = measurements_cache
            .recovery_delay_notify_lost_app_delay_incomplete
            .write()
            .unwrap();
        *recovery_delay_notify_lost_app_delay_incomplete =
            pending_delay_notify_counts.lost_app_delay_incomplete;
    }

    {
        let mut recovery_delay_notify_lost_app_delay_complete = measurements_cache
            .recovery_delay_notify_lost_app_delay_complete
            .write()
            .unwrap();
        *recovery_delay_notify_lost_app_delay_complete =
            pending_delay_notify_counts.lost_app_delay_complete;
    }

    {
        let mut recovery_delay_notify_lost_hw_delay_incomplete = measurements_cache
            .recovery_delay_notify_lost_hw_delay_incomplete
            .write()
            .unwrap();
        *recovery_delay_notify_lost_hw_delay_incomplete =
            pending_delay_notify_counts.lost_hw_delay_incomplete;
    }

    {
        let mut recovery_delay_notify_lost_hw_delay_complete = measurements_cache
            .recovery_delay_notify_lost_hw_delay_complete
            .write()
            .unwrap();
        *recovery_delay_notify_lost_hw_delay_complete =
            pending_delay_notify_counts.lost_hw_delay_complete;
    }

    {
        let mut customer_notification_push_queue_num_messages = measurements_cache
            .customer_notification_push_queue_num_messages
            .write()
            .unwrap();
        *customer_notification_push_queue_num_messages =
            messages_per_queue.get(&push_queue_url).unwrap().to_owned();
    }

    {
        let mut customer_notification_email_queue_num_messages = measurements_cache
            .customer_notification_email_queue_num_messages
            .write()
            .unwrap();
        *customer_notification_email_queue_num_messages =
            messages_per_queue.get(&email_queue_url).unwrap().to_owned();
    }

    {
        let mut customer_notification_sms_queue_num_messages = measurements_cache
            .customer_notification_sms_queue_num_messages
            .write()
            .unwrap();
        *customer_notification_sms_queue_num_messages =
            messages_per_queue.get(&sms_queue_url).unwrap().to_owned();
    }

    measure_electrum_ping_response_time(state).await;
    measure_electrum_tip_height(state, measurements_cache).await?;

    event!(Level::INFO, "Ending metrics job");
    Ok(())
}

async fn measure_electrum_ping_response_time(state: &WorkerState) {
    for node in &state.config.monitored_electrum_nodes {
        let electrum_client = match get_electrum_client(
            node.network,
            &ElectrumRpcUris {
                mainnet: node.uri.clone(),
                testnet: node.uri.clone(),
                signet: node.uri.clone(),
            },
        ) {
            Ok(electrum_client) => electrum_client,
            Err(e) => {
                event!(
                    Level::WARN,
                    "Error instantiating {node}'s client: {e}; skipping",
                    node = node.uri,
                    e = e
                );
                continue;
            }
        };

        let start = std::time::Instant::now();
        if let Err(e) = electrum_client.ping() {
            event!(
                Level::WARN,
                "Error measuring {node}'s electrum ping: {e}; skipping",
                node = node.uri,
                e = e
            );
            continue;
        }
        let end = std::time::Instant::now();

        let elapsed = end.duration_since(start).as_millis() as u64;
        bdk_utils_metrics::ELECTRUM_PING_RESPONSE_TIME.record(
            elapsed,
            &[
                KeyValue::new(ELECTRUM_URI_KEY, node.uri.to_owned()),
                KeyValue::new(ELECTRUM_NETWORK_KEY, node.network.to_string()),
                KeyValue::new(ELECTRUM_PROVIDER_KEY, node.provider.to_owned()),
            ],
        );
    }
}

async fn measure_electrum_tip_height(
    state: &WorkerState,
    measurements_cache: &MeasurementsCache,
) -> Result<(), WorkerError> {
    for node in state.config.monitored_electrum_nodes.iter() {
        let electrum_client = match get_electrum_client(
            node.network,
            &ElectrumRpcUris {
                mainnet: node.uri.clone(),
                testnet: node.uri.clone(),
                signet: node.uri.clone(),
            },
        ) {
            Ok(electrum_client) => electrum_client,
            Err(e) => {
                event!(
                    Level::WARN,
                    "Error instantiating {node}'s client: {e}; skipping",
                    node = node.uri,
                    e = e
                );
                continue;
            }
        };

        let tip_height = electrum_client.block_headers_subscribe().map(|r| r.height);

        match tip_height {
            Ok(tip_height) => {
                measurements_cache
                    .electrum_tip_height_by_node
                    .write()
                    .unwrap()
                    .insert(node.to_owned(), tip_height.try_into().unwrap_or_default());
            }
            Err(e) => {
                event!(
                    Level::WARN,
                    "Error measuring {node}'s electrum tip height: {e}; skipping",
                    node = node.uri,
                    e = e
                );
                continue;
            }
        }
    }

    Ok(())
}
