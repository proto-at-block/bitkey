package build.wallet.feature.flags

import build.wallet.feature.FeatureFlag
import build.wallet.feature.FeatureFlagDao
import build.wallet.feature.FeatureFlagValue.BooleanFlag

/**
 * Flag determining whether the app update modal should be shown to users.
 * When enabled, users will see a modal prompting them to update their app
 * with links to the App Store or Google Play Store. Users can cancel to continue
 * using the app.
 *
 * Defaults to false on all builds.
 */
class AppUpdateModalFeatureFlag(
  featureFlagDao: FeatureFlagDao,
) : FeatureFlag<BooleanFlag>(
    identifier = "mobile-app-update-modal-is-enabled",
    title = "App Update Modal",
    description = "Shows app update modal prompting users to update their app",
    defaultFlagValue = BooleanFlag(false),
    featureFlagDao = featureFlagDao,
    type = BooleanFlag::class
  )
