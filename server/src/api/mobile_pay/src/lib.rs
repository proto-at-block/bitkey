use feature_flags::flag::Flag;

pub mod daily_spend_record;
pub mod entities;
pub(crate) mod metrics;
pub mod routes;
pub mod signed_psbt_cache;
pub mod spend_rules;
pub(crate) mod util;

pub(crate) const FLAG_MOBILE_PAY_ENABLED: Flag<bool> = Flag::new("f8e-mobile-pay-enabled");
