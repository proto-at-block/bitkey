package build.wallet.feature.flags

import build.wallet.feature.FeatureFlag
import build.wallet.feature.FeatureFlagDao
import build.wallet.feature.FeatureFlagValue.DoubleFlag

/**
 * Flag with the minimum amount of bitcoin that the user can sell.
 * Defaults to $1 on all builds
 */
class SellBitcoinMinAmountFeatureFlag(
  featureFlagDao: FeatureFlagDao,
) : FeatureFlag<DoubleFlag>(
    identifier = "mobile-sell-bitcoin-min-amount",
    title = "Sell Bitcoin Min Amount",
    description = "The minimum amount of bitcoin that the user can sell",
    defaultFlagValue = DoubleFlag(0.0005),
    featureFlagDao = featureFlagDao,
    type = DoubleFlag::class
  )
