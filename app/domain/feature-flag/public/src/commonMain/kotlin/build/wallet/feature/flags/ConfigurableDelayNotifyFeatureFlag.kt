package build.wallet.feature.flags

import build.wallet.feature.FeatureFlag
import build.wallet.feature.FeatureFlagDao
import build.wallet.feature.FeatureFlagValue

/**
 * Flag determining whether the customer-configurable Delay & Notify period is enabled.
 *
 * When enabled, the Security Hub surfaces the "Delay & Notify period" action, letting the
 * customer change their recovery security waiting period (e.g. 7/14/30 days). When disabled,
 * the action is hidden and the account keeps its server-assigned default period.
 */
class ConfigurableDelayNotifyFeatureFlag(
  featureFlagDao: FeatureFlagDao,
) : FeatureFlag<FeatureFlagValue.BooleanFlag>(
    identifier = "mobile-configurable-delay-notify-enabled",
    title = "Configurable Delay & Notify Period",
    description = "Enables the customer-configurable recovery Delay & Notify period in Security Hub",
    defaultFlagValue = FeatureFlagValue.BooleanFlag(false),
    featureFlagDao = featureFlagDao,
    type = FeatureFlagValue.BooleanFlag::class
  )
