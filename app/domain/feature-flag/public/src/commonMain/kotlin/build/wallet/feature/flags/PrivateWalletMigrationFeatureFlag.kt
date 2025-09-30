package build.wallet.feature.flags

import build.wallet.feature.FeatureFlag
import build.wallet.feature.FeatureFlagDao
import build.wallet.feature.FeatureFlagValue.BooleanFlag

/**
 * Flag determining whether the private wallet migration feature is enabled.
 * When enabled, users with existing legacy wallets will see options to upgrade
 * to private collaborative custody using chaincode delegation.
 *
 * Defaults to false on all builds.
 */
class PrivateWalletMigrationFeatureFlag(
  featureFlagDao: FeatureFlagDao,
) : FeatureFlag<BooleanFlag>(
    identifier = "mobile-private-wallet-migration-enabled",
    title = "Private Wallet Migration",
    description = "Enable private wallet upgrade flow for existing users with legacy wallets",
    defaultFlagValue = BooleanFlag(false),
    featureFlagDao = featureFlagDao,
    type = BooleanFlag::class
  )
