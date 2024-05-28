use instrumentation::metrics::factory::{Counter, MetricsFactory, ObservableGauge};
use once_cell::sync::Lazy;

pub const FACTORY_NAME: &str = "notifications";
pub const CUSTOMER_NOTIFICATION_TYPE_KEY: &str = "customer_notification_type";
pub(crate) const COUNTRY_CODE_KEY: &str = "country_code";

pub static FACTORY: Lazy<MetricsFactory> = Lazy::new(|| MetricsFactory::new(FACTORY_NAME));

// Gauges
pub static NOTIFICATION_QUEUE_NUM_MESSAGES: Lazy<ObservableGauge<u64>> =
    Lazy::new(|| FACTORY.u64_observable_gauge("sqs.num_messages_for_queue", None));

// Counters
pub static SNS_PUBLISH_ATTEMPT: Lazy<Counter<u64>> =
    Lazy::new(|| FACTORY.u64_counter("sns.publish.attempt", None));
pub static SNS_PUBLISH_FAILURE: Lazy<Counter<u64>> =
    Lazy::new(|| FACTORY.u64_counter("sns.publish.failure", None));
pub(crate) static TWILIO_CREATE_MESSAGE_ATTEMPT: Lazy<Counter<u64>> =
    Lazy::new(|| FACTORY.u64_counter("twilio.create_message.attempt", None));
pub(crate) static TWILIO_CREATE_MESSAGE_FAILURE: Lazy<Counter<u64>> =
    Lazy::new(|| FACTORY.u64_counter("twilio.create_message.failure", None));
pub(crate) static TWILIO_MESSAGE_STATUS_SENT: Lazy<Counter<u64>> =
    Lazy::new(|| FACTORY.u64_counter("twilio.message_status.sent", None));
pub(crate) static TWILIO_MESSAGE_STATUS_FAILED: Lazy<Counter<u64>> =
    Lazy::new(|| FACTORY.u64_counter("twilio.message_status.failed", None));
pub(crate) static TWILIO_MESSAGE_STATUS_DELIVERED: Lazy<Counter<u64>> =
    Lazy::new(|| FACTORY.u64_counter("twilio.message_status.delivered", None));
pub(crate) static TWILIO_MESSAGE_STATUS_UNDELIVERED: Lazy<Counter<u64>> =
    Lazy::new(|| FACTORY.u64_counter("twilio.message_status.undelivered", None));
