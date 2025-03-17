package build.wallet.feature.flags

import build.wallet.feature.FeatureFlag
import build.wallet.feature.FeatureFlagDao
import build.wallet.feature.FeatureFlagValue.BooleanFlag

/**
 * Flag determining whether or not to allow users to speed up a transaction.
 * Defaults to false on all builds
 */
class FeeBumpIsAvailableFeatureFlag(
  featureFlagDao: FeatureFlagDao,
) : FeatureFlag<BooleanFlag>(
    identifier = "fee-bumping-is-available",
    title = "Fee Bumping Enabled",
    description = "Allows you to use RBF to speed up your transaction.",
    defaultFlagValue = BooleanFlag(true),
    featureFlagDao = featureFlagDao,
    type = BooleanFlag::class
  )
