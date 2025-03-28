use instrumentation::metrics::factory::{Counter, MetricsFactory};
use once_cell::sync::Lazy;

pub const FACTORY_NAME: &str = "linear";

pub static FACTORY: Lazy<MetricsFactory> = Lazy::new(|| MetricsFactory::new(FACTORY_NAME));

pub(crate) static CS_ESCALATION_SLA_BREACHED: Lazy<Counter<u64>> =
    Lazy::new(|| FACTORY.u64_counter("cs_escalation.sla_breached", None));

pub(crate) static CS_ESCALATION_SLA_MET: Lazy<Counter<u64>> =
    Lazy::new(|| FACTORY.u64_counter("cs_escalation.sla_met", None));
