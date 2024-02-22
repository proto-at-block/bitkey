package build.wallet.auth

import build.wallet.feature.FeatureFlag
import build.wallet.feature.FeatureFlagDao
import build.wallet.feature.FeatureFlagValue

class InactiveDeviceIsEnabledFeatureFlag(
  featureFlagDao: FeatureFlagDao,
) : FeatureFlag<FeatureFlagValue.BooleanFlag>(
    identifier = "inactive-device-enabled",
    title = "Inactive Device",
    description = "Enables apps to log out other devices, making them inactive. Shows special UI when a device determines it is inactive.",
    defaultFlagValue = FeatureFlagValue.BooleanFlag(value = false),
    featureFlagDao = featureFlagDao,
    type = FeatureFlagValue.BooleanFlag::class
  )
