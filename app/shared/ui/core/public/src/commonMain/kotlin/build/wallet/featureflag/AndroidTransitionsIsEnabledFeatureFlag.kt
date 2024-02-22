package build.wallet.featureflag

import build.wallet.feature.FeatureFlag
import build.wallet.feature.FeatureFlagDao
import build.wallet.feature.FeatureFlagValue.BooleanFlag
import build.wallet.platform.config.AppVariant

/**
 * Flag determining whether or not to enable android transition animations.
 */
class AndroidTransitionsIsEnabledFeatureFlag(
  featureFlagDao: FeatureFlagDao,
  appVariant: AppVariant,
) : FeatureFlag<BooleanFlag>(
    identifier = "android-transitions-is-enabled",
    title = "Android Transitions Enabled",
    description = "Enables transition animations between screens on android.",
    defaultFlagValue =
      when (appVariant) {
        AppVariant.Development -> BooleanFlag(true)
        AppVariant.Team -> BooleanFlag(true)
        AppVariant.Beta -> BooleanFlag(false)
        AppVariant.Customer -> BooleanFlag(false)
        AppVariant.Emergency -> BooleanFlag(false)
      },
    featureFlagDao = featureFlagDao,
    type = BooleanFlag::class
  )
