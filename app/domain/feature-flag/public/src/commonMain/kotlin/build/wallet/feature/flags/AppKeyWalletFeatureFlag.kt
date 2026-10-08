package build.wallet.feature.flags

import build.wallet.feature.FeatureFlag
import build.wallet.feature.FeatureFlagDao
import build.wallet.feature.FeatureFlagValue.BooleanFlag

class AppKeyWalletFeatureFlag(
  featureFlagDao: FeatureFlagDao,
) : FeatureFlag<BooleanFlag>(
    identifier = "app-key-wallet-enabled",
    title = "App Key Wallet",
    description = "Shows the App Key Wallet settings entry and enables user-initiated scanning.",
    defaultFlagValue = BooleanFlag(false),
    featureFlagDao = featureFlagDao,
    type = BooleanFlag::class
  )
