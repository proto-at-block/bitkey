package build.wallet.feature.flags

import build.wallet.feature.FeatureFlag
import build.wallet.feature.FeatureFlagDao
import build.wallet.feature.FeatureFlagValue.BooleanFlag

/**
 * Flag determining whether the keyset repair feature is enabled.
 * When enabled, the app will detect keyset mismatches between local and server
 * state (e.g., from stale cloud backup recovery) and provide a repair flow.
 *
 * Defaults to false on all builds.
 */
class KeysetRepairFeatureFlag(
  featureFlagDao: FeatureFlagDao,
) : FeatureFlag<BooleanFlag>(
    identifier = "mobile-keyset-repair-enabled",
    title = "Keyset Repair",
    description = "Enables detection and repair of keyset mismatches from stale cloud backup recovery",
    defaultFlagValue = BooleanFlag(false),
    featureFlagDao = featureFlagDao,
    type = BooleanFlag::class
  )
