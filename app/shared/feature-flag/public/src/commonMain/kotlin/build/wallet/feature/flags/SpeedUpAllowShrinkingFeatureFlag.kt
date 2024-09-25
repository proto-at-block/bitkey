package build.wallet.feature.flags

import build.wallet.feature.FeatureFlag
import build.wallet.feature.FeatureFlagDao
import build.wallet.feature.FeatureFlagValue.BooleanFlag

/**
 * Flag determining whether a customer can use bdk allow_shrinking and therefore speed up sweep
 * transactions and utxo consolidations.
 */
class SpeedUpAllowShrinkingFeatureFlag(
  featureFlagDao: FeatureFlagDao,
) : FeatureFlag<BooleanFlag>(
    identifier = "mobile-speed-up-allow-shrinking-is-enabled",
    title = "Speed Up Allow Shrinking",
    description = "Allows users to speed up sweep transactions by leveraging allow_shrinking",
    defaultFlagValue = BooleanFlag(false),
    featureFlagDao = featureFlagDao,
    type = BooleanFlag::class
  )
