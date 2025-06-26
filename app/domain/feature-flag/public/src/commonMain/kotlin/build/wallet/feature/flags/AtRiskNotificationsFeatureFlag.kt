package build.wallet.feature.flags

import build.wallet.feature.FeatureFlag
import build.wallet.feature.FeatureFlagDao
import build.wallet.feature.FeatureFlagValue

class AtRiskNotificationsFeatureFlag(
  featureFlagDao: FeatureFlagDao,
) : FeatureFlag<FeatureFlagValue.BooleanFlag>(
    identifier = "mobile-at-risk-notifications-is-enabled",
    title = "At Risk Notifications",
    description = "Enables notifications to be triggered for accounts that are at risk",
    defaultFlagValue = FeatureFlagValue.BooleanFlag(false),
    featureFlagDao = featureFlagDao,
    type = FeatureFlagValue.BooleanFlag::class
  )
