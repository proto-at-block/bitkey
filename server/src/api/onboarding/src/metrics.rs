use metrics::factory::MetricsFactory;
use once_cell::sync::Lazy;

pub(crate) static FACTORY: Lazy<MetricsFactory> = Lazy::new(|| MetricsFactory::new("onboarding"));
