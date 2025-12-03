package build.wallet.feature.flags

import build.wallet.feature.FeatureFlag
import build.wallet.feature.FeatureFlagDao
import build.wallet.feature.FeatureFlagValue

/**
 * Feature flag to control whether the Cash App info sheet is displayed in the partnerships
 * purchase quotes screen.
 */
class CashAppFeePromotionFeatureFlag(
  featureFlagDao: FeatureFlagDao,
) : FeatureFlag<FeatureFlagValue.BooleanFlag>(
    identifier = "mobile-cash-app-fee-promotion-enabled",
    title = "Cash App Fee Promotion",
    description = "When enabled, shows the Cash App promotional info sheet on purchase quotes.",
    defaultFlagValue = FeatureFlagValue.BooleanFlag(false),
    featureFlagDao = featureFlagDao,
    type = FeatureFlagValue.BooleanFlag::class
  )
