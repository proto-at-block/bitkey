use instrumentation::metrics::factory::MetricsFactory;
use once_cell::sync::Lazy;

pub const FACTORY_NAME: &str = "customer_feedback";

pub static FACTORY: Lazy<MetricsFactory> = Lazy::new(|| MetricsFactory::new(FACTORY_NAME));
