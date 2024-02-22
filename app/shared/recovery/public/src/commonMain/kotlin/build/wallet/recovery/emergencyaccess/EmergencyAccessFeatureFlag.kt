package build.wallet.recovery.emergencyaccess

import build.wallet.feature.FeatureFlag
import build.wallet.feature.FeatureFlagDao
import build.wallet.feature.FeatureFlagValue.BooleanFlag

class EmergencyAccessFeatureFlag(
  featureFlagDao: FeatureFlagDao,
) : FeatureFlag<BooleanFlag>(
    identifier = "emergency-access-enabled",
    title = "Emergency Access Kit",
    description = "Enables Emergency Access Kit features and flows.",
    defaultFlagValue = BooleanFlag(value = false),
    featureFlagDao = featureFlagDao,
    type = BooleanFlag::class
  )
