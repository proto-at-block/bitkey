package build.wallet.feature.flags

import build.wallet.feature.FeatureFlag
import build.wallet.feature.FeatureFlagDao
import build.wallet.feature.FeatureFlagValue.DoubleFlag

class SellBitcoinMinAmountFeatureFlag(
  featureFlagDao: FeatureFlagDao,
) : FeatureFlag<DoubleFlag>(
    identifier = "mobile-sell-bitcoin-min-amount",
    title = "Sell Bitcoin Min Amount",
    description = "The fallback minimum amount of bitcoin that the user can sell",
    defaultFlagValue = DoubleFlag(0.0005),
    featureFlagDao = featureFlagDao,
    type = DoubleFlag::class
  )
