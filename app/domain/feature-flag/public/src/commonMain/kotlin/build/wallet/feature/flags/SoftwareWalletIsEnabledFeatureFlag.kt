package build.wallet.feature.flags

import build.wallet.feature.FeatureFlag
import build.wallet.feature.FeatureFlagDao
import build.wallet.feature.FeatureFlagValue.BooleanFlag

/**
 * Flag determining whether a customer can create a Software Wallet, without
 * using hardware.
 *
 * Defaults to false on all builds
 */
class SoftwareWalletIsEnabledFeatureFlag(
  featureFlagDao: FeatureFlagDao,
) : FeatureFlag<BooleanFlag>(
    identifier = "mobile-software-wallet-is-enabled",
    title = "Software Wallet",
    description = "Allows you to create Software Wallet",
    defaultFlagValue = BooleanFlag(false),
    featureFlagDao = featureFlagDao,
    type = BooleanFlag::class
  )
