use instrumentation::metrics::factory::{Counter, MetricsFactory};
use once_cell::sync::Lazy;

pub(crate) static FACTORY: Lazy<MetricsFactory> = Lazy::new(|| MetricsFactory::new("onboarding"));

// Counts the number of keyset creation events
pub(crate) static KEYSET_CREATED: Lazy<Counter<u64>> =
    Lazy::new(|| FACTORY.u64_counter("keyset_created", None));

pub const KEYSET_TYPE_KEY: &str = "keyset_type";
pub const LEGACY_VALUE: &str = "legacy";
pub const PRIVATE_VALUE: &str = "private";
pub const APP_ID_KEY: &str = "app_id";
