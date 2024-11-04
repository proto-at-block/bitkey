use instrumentation::metrics::{
    factory::Counter, factory::Histogram, factory::MetricsFactory, KeyValue, Unit,
};
use once_cell::sync::Lazy;
use std::future::Future;
use time::OffsetDateTime;

pub(crate) const FACTORY_NAME: &str = "mobile_pay";
pub(crate) const IS_MOBILE_PAY: &str = "is_mobile_pay";

pub(crate) static FACTORY: Lazy<MetricsFactory> = Lazy::new(|| MetricsFactory::new(FACTORY_NAME));

// Counters

// Counts the number of attempts to sign Mobile Pay above spending limit. This number should
// *always* be 0.
pub(crate) static MOBILE_PAY_COSIGN_OVERFLOW: Lazy<Counter<u64>> =
    Lazy::new(|| FACTORY.u64_counter("cosign_overflow", None));

// Counts the number of attempts to sign a Mobile Pay PSBT where not all the inputs belong to
// the user's wallet. This number should *always* be 0.
pub(crate) static MOBILE_PAY_INPUTS_DO_NOT_BELONG_TO_SELF: Lazy<Counter<u64>> =
    Lazy::new(|| FACTORY.u64_counter("inputs_do_not_belong_to_self", None));

// Counts the number of attempts to sign a Mobile Pay PSBT where not all the outputs belong to
// the user's wallet. This number should *always* be 0.
pub(crate) static MOBILE_PAY_OUTPUTS_BELONG_TO_SELF: Lazy<Counter<u64>> =
    Lazy::new(|| FACTORY.u64_counter("outputs_belong_to_self", None));

// Counts the number of attempts to sign a Sweep PSBT where the outputs belong to an external wallet.
// This number should *always* be 0.
pub(crate) static SWEEP_OUTPUTS_DONT_BELONG_TO_ACTIVE_KEYSET: Lazy<Counter<u64>> =
    Lazy::new(|| FACTORY.u64_counter("outputs_dont_belong_to_active_keyset", None));

// Histograms

// Measures the spread of how long it takes to cosign a transaction.
pub(crate) static TIME_TO_COSIGN: Lazy<Histogram<u64>> =
    Lazy::new(|| FACTORY.u64_histogram("time_to_cosign", Some(Unit::new("ms"))));

// Measured the spread of how long it takes for F8e to broadcast a transaction.
pub(crate) static TIME_TO_BROADCAST: Lazy<Histogram<u64>> =
    Lazy::new(|| FACTORY.u64_histogram("f8e_time_to_broadcast", Some(Unit::new("ms"))));

// Measures the spread of how long it takes for F8e to check spending rules for a transaction.
pub(crate) static TIME_TO_CHECK_SPENDING_RULES: Lazy<Histogram<u64>> =
    Lazy::new(|| FACTORY.u64_histogram("f8e_time_to_check_spending_rules", Some(Unit::new("ms"))));

const SIGNING_CONTEXT: &str = "signing_context";

pub(crate) async fn record_histogram_async<T, F, Fut>(
    metric: Histogram<u64>,
    context: &str,
    func: F,
) -> T
where
    F: FnOnce() -> Fut,
    Fut: Future<Output = T>,
{
    let start_time = OffsetDateTime::now_utc();
    let result = func().await;
    let duration = (OffsetDateTime::now_utc() - start_time).whole_milliseconds() as u64;
    let attributes = [KeyValue::new(SIGNING_CONTEXT, context.to_string())];
    metric.record(duration, &attributes);
    result
}

pub(crate) fn record_histogram<T, F>(metric: Histogram<u64>, context: &str, func: F) -> T
where
    F: FnOnce() -> T,
{
    let start_time = OffsetDateTime::now_utc();
    let result = func();
    let duration = (OffsetDateTime::now_utc() - start_time).whole_milliseconds() as u64;
    let attributes = [KeyValue::new(SIGNING_CONTEXT, context.to_string())];
    metric.record(duration, &attributes);
    result
}
