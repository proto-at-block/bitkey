package build.wallet.statemachine.settings.full.notifications

import build.wallet.feature.FeatureFlag
import build.wallet.feature.FeatureFlagDao
import build.wallet.feature.FeatureFlagValue

/**
 * Flag determining whether or not to take the user through the updated onboarding flow and settings
 * functionality for notifications.
 */
class NotificationsFlowV2EnabledFeatureFlag(
  featureFlagDao: FeatureFlagDao,
) : FeatureFlag<FeatureFlagValue.BooleanFlag>(
    identifier = "notifications-flow-v2",
    title = "Updated Notifications Flow",
    description = "Show the new notifications onboarding and settings",
    defaultFlagValue = FeatureFlagValue.BooleanFlag(true),
    featureFlagDao = featureFlagDao,
    type = FeatureFlagValue.BooleanFlag::class
  )
