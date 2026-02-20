package build.wallet.feature.flags

import build.wallet.feature.FeatureFlag
import build.wallet.feature.FeatureFlagDao
import build.wallet.feature.FeatureFlagValue

/**
 * Feature flag to control Bitkey Vaults availability.
 *
 * Vaults constrain how bitcoin can move under physical threat using time-delayed withdrawals,
 * eventual self-custody expiry, and automatic ejection on failed liveness checks.
 */
class VaultsFeatureFlag(
  featureFlagDao: FeatureFlagDao,
) : FeatureFlag<FeatureFlagValue.BooleanFlag>(
    identifier = "mobile-vaults-enabled",
    title = "Vaults",
    description = "Constrain bitcoin movement under physical threat using time delays, expiry, and liveness ejection.",
    defaultFlagValue = FeatureFlagValue.BooleanFlag(false),
    featureFlagDao = featureFlagDao,
    type = FeatureFlagValue.BooleanFlag::class
  )
