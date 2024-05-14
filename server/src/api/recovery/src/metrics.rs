use instrumentation::metrics::{
    factory::Counter, factory::Histogram, factory::MetricsFactory, factory::ObservableGauge, Unit,
};
use once_cell::sync::Lazy;

pub const LOST_FACTOR_KEY: &str = "lost_factor";
pub const CREATED_DURING_CONTEST_KEY: &str = "created_during_contest";
pub const CANCELED_IN_CONTEST_KEY: &str = "canceled_in_contest";
pub const CODE_MATCHED_KEY: &str = "code_matched";
pub const DELAY_COMPLETE_KEY: &str = "delay_complete";
pub const METHOD_KEY: &str = "method";
pub const PATH_KEY: &str = "path";

//TODO[W-5630]: Replace with std once stabilized
pub static FACTORY: Lazy<MetricsFactory> = Lazy::new(|| MetricsFactory::new("recovery"));

// Counters
pub(crate) static DELAY_NOTIFY_CREATED: Lazy<Counter<u64>> =
    Lazy::new(|| FACTORY.u64_counter("delay_notify.created", None));
pub(crate) static DELAY_NOTIFY_CANCELED: Lazy<Counter<u64>> =
    Lazy::new(|| FACTORY.u64_counter("delay_notify.canceled", None));
pub(crate) static DELAY_NOTIFY_COMPLETED: Lazy<Counter<u64>> =
    Lazy::new(|| FACTORY.u64_counter("delay_notify.completed", None));
pub(crate) static DELAY_NOTIFY_CODE_SUBMITTED: Lazy<Counter<u64>> =
    Lazy::new(|| FACTORY.u64_counter("delay_notify.code_submitted", None));
pub(crate) static AUTH_KEYS_ROTATED: Lazy<Counter<u64>> =
    Lazy::new(|| FACTORY.u64_counter("auth_keys_rotated", None));

// Gauges
pub static DELAY_NOTIFY_PENDING: Lazy<ObservableGauge<u64>> =
    Lazy::new(|| FACTORY.u64_observable_gauge("delay_notify.pending", None));

// Histograms
pub(crate) static DELAY_NOTIFY_TIME_TO_CANCEL: Lazy<Histogram<u64>> =
    Lazy::new(|| FACTORY.u64_histogram("delay_notify.time_to_cancel", Some(Unit::new("min"))));
pub(crate) static DELAY_NOTIFY_TIME_TO_COMPLETE: Lazy<Histogram<u64>> =
    Lazy::new(|| FACTORY.u64_histogram("delay_notify.time_to_complete", Some(Unit::new("min"))));
