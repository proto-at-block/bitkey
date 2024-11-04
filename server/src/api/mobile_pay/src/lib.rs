use feature_flags::flag::Flag;

pub mod daily_spend_record;
pub mod entities;
pub mod error;
pub(crate) mod metrics;
pub mod routes;
pub mod signed_psbt_cache;
pub mod signing_processor;
pub mod spend_rules;
pub(crate) mod util;

pub(crate) const SERVER_SIGNING_ENABLED: Flag<bool> = Flag::new("f8e-mobile-pay-enabled");
