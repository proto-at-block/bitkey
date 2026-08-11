const ONE_DAY_SECS: usize = 24 * 60 * 60;
const SEVEN_DAYS_SECS: usize = 7 * ONE_DAY_SECS;
const FOURTEEN_DAYS_SECS: usize = 14 * ONE_DAY_SECS;
const THIRTY_DAYS_SECS: usize = 30 * ONE_DAY_SECS;

/// Returns the effective delay notify period in seconds, accounting for test accounts.
///
/// Test accounts keep the same relative period choices while using minute-scale delays:
/// 7 days -> 1 minute, 14 days -> 2 minutes, 30 days -> 3 minutes.
pub fn effective_delay_notify_period_secs(
    delay_period_secs: usize,
    is_test_account: bool,
) -> usize {
    if !is_test_account {
        return delay_period_secs;
    }

    match delay_period_secs {
        SEVEN_DAYS_SECS => 60,
        FOURTEEN_DAYS_SECS => 2 * 60,
        THIRTY_DAYS_SECS => 3 * 60,
        _ => 60,
    }
}

#[cfg(test)]
mod tests {
    use super::{
        effective_delay_notify_period_secs, FOURTEEN_DAYS_SECS, SEVEN_DAYS_SECS, THIRTY_DAYS_SECS,
    };

    #[test]
    fn test_effective_delay_notify_period_for_test_account() {
        assert_eq!(
            effective_delay_notify_period_secs(SEVEN_DAYS_SECS, true),
            60
        );
        assert_eq!(
            effective_delay_notify_period_secs(FOURTEEN_DAYS_SECS, true),
            2 * 60
        );
        assert_eq!(
            effective_delay_notify_period_secs(THIRTY_DAYS_SECS, true),
            3 * 60
        );
    }

    #[test]
    fn test_effective_delay_notify_period_for_non_test_account() {
        assert_eq!(
            effective_delay_notify_period_secs(FOURTEEN_DAYS_SECS, false),
            FOURTEEN_DAYS_SECS
        );
    }
}
