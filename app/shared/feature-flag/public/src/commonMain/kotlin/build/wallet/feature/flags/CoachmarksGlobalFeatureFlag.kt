package build.wallet.feature.flags

import build.wallet.feature.FeatureFlag
import build.wallet.feature.FeatureFlagDao
import build.wallet.feature.FeatureFlagValue

/**
 * This feature flag is a killswitch for the display of coachmarks in the application. By default
 * it is off. If toggled on, no coachmarks will display.
 */
class CoachmarksGlobalFeatureFlag(
  featureFlagDao: FeatureFlagDao,
) : FeatureFlag<FeatureFlagValue.BooleanFlag>(
    identifier = "mobile-coachmarks-global",
    title = "Coachmarks Global Toggle",
    description = "Killswitch for all coachmarks in the app.",
    defaultFlagValue = FeatureFlagValue.BooleanFlag(false),
    featureFlagDao = featureFlagDao,
    type = FeatureFlagValue.BooleanFlag::class
  )
