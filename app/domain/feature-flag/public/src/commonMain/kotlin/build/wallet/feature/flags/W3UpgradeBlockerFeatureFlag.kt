package build.wallet.feature.flags

import build.wallet.feature.FeatureFlag
import build.wallet.feature.FeatureFlagDao
import build.wallet.feature.FeatureFlagValue.BooleanFlag

/**
 * Flag determining whether the W3 upgrade blocker should be shown to eligible legacy-device users.
 *
 * Defaults to false on all builds.
 */
class W3UpgradeBlockerFeatureFlag(
  featureFlagDao: FeatureFlagDao,
) : FeatureFlag<BooleanFlag>(
    identifier = "mobile-w3-upgrade-blocker-is-enabled",
    title = "W3 upgrade blocker",
    description = "Shows a blocker prompting eligible customers to upgrade to the all-new Bitkey",
    defaultFlagValue = BooleanFlag(false),
    featureFlagDao = featureFlagDao,
    type = BooleanFlag::class
  )
