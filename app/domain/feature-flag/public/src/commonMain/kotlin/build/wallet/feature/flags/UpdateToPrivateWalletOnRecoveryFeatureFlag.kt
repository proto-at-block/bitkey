package build.wallet.feature.flags

import build.wallet.feature.FeatureFlag
import build.wallet.feature.FeatureFlagDao
import build.wallet.feature.FeatureFlagValue.BooleanFlag

/**
 * Flag determining whether a user is automatically migrated to a private wallet when they perform
 * a D&N recovery sweep.
 *
 * Defaults to false on all builds.
 */
class UpdateToPrivateWalletOnRecoveryFeatureFlag(
  featureFlagDao: FeatureFlagDao,
) : FeatureFlag<BooleanFlag>(
    identifier = "mobile-update-to-private-wallet-on-recovery-enabled",
    title = "Update to Private Wallet on Recovery",
    description = "Enable implicit update to private wallet on recovery sweep",
    defaultFlagValue = BooleanFlag(false),
    featureFlagDao = featureFlagDao,
    type = BooleanFlag::class
  )
