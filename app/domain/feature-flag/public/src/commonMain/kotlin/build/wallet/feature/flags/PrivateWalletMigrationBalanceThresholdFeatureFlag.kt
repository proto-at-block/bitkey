package build.wallet.feature.flags

import build.wallet.feature.FeatureFlag
import build.wallet.feature.FeatureFlagDao
import build.wallet.feature.FeatureFlagValue

/**
 * Defines the maximum balance (in sats) a user can have in their wallet to be eligible for
 * migration to a private wallet.
 *
 * A value of 0 (the default) means the feature flag may not have synced, and migrations should not
 * be allowed.
 *
 * A negative value means there is no balance threshold, and all users are eligible for migration.
 *
 * Intended to be used in conjunction with [PrivateWalletMigrationFeatureFlag] to carefully rollout
 * private wallet migrations.
 */
class PrivateWalletMigrationBalanceThresholdFeatureFlag(
  featureFlagDao: FeatureFlagDao,
) : FeatureFlag<FeatureFlagValue.DoubleFlag>(
    identifier = "mobile-private-wallet-migration-balance-threshold",
    title = "Private Wallet Migration Balance Threshold",
    description = "Defines the maximum balance a user can have in their wallet to be eligible for migration to a private wallet.",
    defaultFlagValue = FeatureFlagValue.DoubleFlag(0.0),
    featureFlagDao = featureFlagDao,
    type = FeatureFlagValue.DoubleFlag::class
  )
