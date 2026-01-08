package build.wallet.feature.flags

import build.wallet.feature.FeatureFlag
import build.wallet.feature.FeatureFlagDao
import build.wallet.feature.FeatureFlagValue

/**
 * Enables Augur fee estimation API (https://pricing.bitcoin.block.xyz)
 * as the primary source for transaction fee estimates.
 */
class AugurFeesEstimationFeatureFlag(
  featureFlagDao: FeatureFlagDao,
) : FeatureFlag<FeatureFlagValue.BooleanFlag>(
    identifier = "augur-fees-estimation-enabled",
    title = "Augur Fees Estimation",
    description = "Enables Augur fee estimation API as the primary source for transaction fee estimates",
    defaultFlagValue = FeatureFlagValue.BooleanFlag(false),
    featureFlagDao = featureFlagDao,
    type = FeatureFlagValue.BooleanFlag::class
  )
