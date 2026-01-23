package build.wallet.feature.flags

import build.wallet.feature.FeatureFlag
import build.wallet.feature.FeatureFlagDao
import build.wallet.feature.FeatureFlagValue

/**
 * Enables comparison logging between Augur and Mempool fee rate estimates.
 * When enabled, logs the relative difference category (e.g., HIGHER, LOWER, EQUAL)
 * without logging actual fee values for privacy.
 */
class AugurFeeComparisonLoggingFeatureFlag(
  featureFlagDao: FeatureFlagDao,
) : FeatureFlag<FeatureFlagValue.BooleanFlag>(
    identifier = "augur-fee-comparison-logging-enabled",
    title = "Augur Fee Comparison Logging",
    description = "Enables comparison logging between Augur and Mempool fee estimates",
    defaultFlagValue = FeatureFlagValue.BooleanFlag(false),
    featureFlagDao = featureFlagDao,
    type = FeatureFlagValue.BooleanFlag::class
  )
