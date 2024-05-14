use account::entities::Factor;
use bdk_utils::{generate_electrum_rpc_uris, metrics as bdk_utils_metrics};
use instrumentation::metrics::{factory::Histogram, factory::ObservableCallbackRegistry, KeyValue};
use notification::{
    metrics as notification_metrics, EMAIL_QUEUE_ENV_VAR, PUSH_QUEUE_ENV_VAR, SMS_QUEUE_ENV_VAR,
};
use recovery::metrics as recovery_metrics;

use bdk_utils::bdk::bitcoin::Network;
use bdk_utils::bdk::electrum_client::ElectrumApi;
use bdk_utils::get_electrum_client;
use std::{
    env,
    sync::{Arc, RwLock},
};
use tracing::{event, instrument, Level};
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
        }
    }

    // The callbacks registered here with OTEL are invoked once per metrics reporting cycle; currently
    //   configured to be every 15 seconds globally. They simply read the most recent measurement from
    //   the cache and emit that value. We decoupled the reporting from the gathering of the metrics
    //   so that we don't need to either do expensive work more frequently or reduce the global metrics
    //   reporting period.
    fn register_callbacks(&self) -> Result<(), WorkerError> {
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
                    notification_metrics::CUSTOMER_NOTIFICATION_TYPE,
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
                    notification_metrics::CUSTOMER_NOTIFICATION_TYPE,
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
                    notification_metrics::CUSTOMER_NOTIFICATION_TYPE,
                    NotificationChannel::Sms.to_string(),
                )],
            )
            .map_err(|_| WorkerError::MetricsRegisterCallback)?;
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
            measurements_cache.register_callbacks()?;
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

    measure_electrum_signet_ping_response_time(state).await;
    measure_electrum_mainnet_ping_response_time(state).await;

    event!(Level::INFO, "Ending metrics job");
    Ok(())
}

async fn measure_electrum_ping_response_time(
    network: Network,
    state: &WorkerState,
    metric: &Histogram<u64>,
) {
    if let Err(e) = (|| -> Result<(), WorkerError> {
        let rpc_uris = generate_electrum_rpc_uris(&state.feature_flags_service)?;
        let electrum_client = get_electrum_client(network, &rpc_uris)?;

        let start = std::time::Instant::now();
        electrum_client.ping()?;
        let end = std::time::Instant::now();

        let elapsed = end.duration_since(start).as_millis() as u64;
        metric.record(elapsed, &[]);
        Ok(())
    })() {
        event!(
            Level::ERROR,
            "Error measuring {network} Electrum ping response time: {e}"
        )
    }
}

async fn measure_electrum_mainnet_ping_response_time(state: &WorkerState) {
    measure_electrum_ping_response_time(
        Network::Bitcoin,
        state,
        &bdk_utils_metrics::ELECTRUM_MAINNET_PING_RESPONSE_TIME,
    )
    .await
}

async fn measure_electrum_signet_ping_response_time(state: &WorkerState) {
    measure_electrum_ping_response_time(
        Network::Signet,
        state,
        &bdk_utils_metrics::ELECTRUM_SIGNET_PING_RESPONSE_TIME,
    )
    .await
}
