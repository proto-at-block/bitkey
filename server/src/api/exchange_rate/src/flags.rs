use feature_flags::flag::Flag;

pub const FLAG_USE_CASH_EXCHANGE_RATE_PROVIDER: Flag<bool> =
    Flag::new("f8e-is-using-cash-exchange-rate-provider");
