package build.wallet.feature.flags

import build.wallet.feature.FeatureFlag
import build.wallet.feature.FeatureFlagDao
import build.wallet.feature.FeatureFlagValue.DoubleFlag

/**
 * Flag with the maximum amount of bitcoin that the user can sell.
 * Defaults to $1 on all builds
 */
class SellBitcoinMaxAmountFeatureFlag(
  featureFlagDao: FeatureFlagDao,
) : FeatureFlag<DoubleFlag>(
    identifier = "mobile-sell-bitcoin-max-amount",
    title = "Sell Bitcoin Max Amount",
    description = "The maximum amount of bitcoin that the user can sell",
    defaultFlagValue = DoubleFlag(0.5),
    featureFlagDao = featureFlagDao,
    type = DoubleFlag::class
  )
