package build.wallet.feature.flags

import build.wallet.feature.FeatureFlag
import build.wallet.feature.FeatureFlagDao
import build.wallet.feature.FeatureFlagValue.DoubleFlag

class SellBitcoinMaxAmountFeatureFlag(
  featureFlagDao: FeatureFlagDao,
) : FeatureFlag<DoubleFlag>(
    identifier = "mobile-sell-bitcoin-max-amount",
    title = "Sell Bitcoin Max Amount",
    description = "The fallback maximum amount of bitcoin that the user can sell",
    defaultFlagValue = DoubleFlag(0.5),
    featureFlagDao = featureFlagDao,
    type = DoubleFlag::class
  )
