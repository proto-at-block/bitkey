package build.wallet.feature.flags

import build.wallet.feature.FeatureFlag
import build.wallet.feature.FeatureFlagDao
import build.wallet.feature.FeatureFlagValue

class FirmwareCommsLoggingFeatureFlag(
  featureFlagDao: FeatureFlagDao,
) : FeatureFlag<FeatureFlagValue.BooleanFlag>(
    identifier = "firmware-comms-logging",
    title = "Firmware Comms Logging",
    description = "Enables logging NFC commands and responses. This is privacy sensitive!",
    defaultFlagValue = FeatureFlagValue.BooleanFlag(false), // *MUST* be false by default
    featureFlagDao = featureFlagDao,
    type = FeatureFlagValue.BooleanFlag::class
  )
