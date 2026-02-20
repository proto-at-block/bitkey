package build.wallet.feature.flags

import build.wallet.feature.FeatureFlag
import build.wallet.feature.FeatureFlagDao
import build.wallet.feature.FeatureFlagUpdateBehavior
import build.wallet.feature.FeatureFlagValue

/**
 * Feature flag for enabling BDK 2 upgraded code paths
 *
 * When enabled, the wallet uses BDK 2 APIs instead of legacy BDK.
 *
 * Defaults to false for gradual rollout and testing.
 */
class Bdk2FeatureFlag(
  featureFlagDao: FeatureFlagDao,
) : FeatureFlag<FeatureFlagValue.BooleanFlag>(
    identifier = "mobile-bdk-2-upgrade",
    title = "BDK 2",
    description = "Enables BDK 2 code paths for wallet operations. Update via the main debug menu.",
    defaultFlagValue = FeatureFlagValue.BooleanFlag(false),
    updateBehavior = FeatureFlagUpdateBehavior.UpdateOnLaunch,
    featureFlagDao = featureFlagDao,
    type = FeatureFlagValue.BooleanFlag::class
  )
