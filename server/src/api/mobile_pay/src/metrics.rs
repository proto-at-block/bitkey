use metrics::{factory::Counter, factory::Histogram, factory::MetricsFactory, Unit};
use once_cell::sync::Lazy;
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

// Measures the spread of how long it takes to cosign a Mobile Pay transaction.
pub(crate) static MOBILE_PAY_TIME_TO_COSIGN: Lazy<Histogram<u64>> =
    Lazy::new(|| FACTORY.u64_histogram("time_to_cosign", Some(Unit::new("ms"))));

// Measured the spread of how long it takes for F8e to broadcast a Mobile Pay transaction.
pub(crate) static MOBILE_PAY_F8E_TIME_TO_BROADCAST: Lazy<Histogram<u64>> =
    Lazy::new(|| FACTORY.u64_histogram("f8e_time_to_broadcast", Some(Unit::new("ms"))));
