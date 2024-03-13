package build.wallet.firmware

import build.wallet.feature.FeatureFlag
import build.wallet.feature.FeatureFlagDao
import build.wallet.feature.FeatureFlagValue

/**
 * Flag determining whether to simulate "no firmware device" found scenario
 * for the "Settings -> Bitkey Device".
 * Defaults to false on all builds.
 */
class FirmwareDeviceNotFoundEnabledFeatureFlag(
  featureFlagDao: FeatureFlagDao,
) : FeatureFlag<FeatureFlagValue.BooleanFlag>(
    identifier = "no-firmware-device-found-enabled",
    title = "Firmware Device Not Found Enabled",
    description =
      "Makes Firmware device to be not found when enabled this flag",
    defaultFlagValue = FeatureFlagValue.BooleanFlag(false),
    featureFlagDao = featureFlagDao,
    type = FeatureFlagValue.BooleanFlag::class
  )
