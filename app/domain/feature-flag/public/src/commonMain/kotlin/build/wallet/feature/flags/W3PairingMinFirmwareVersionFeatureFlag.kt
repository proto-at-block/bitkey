package build.wallet.feature.flags

import build.wallet.feature.FeatureFlag
import build.wallet.feature.FeatureFlagDao
import build.wallet.feature.FeatureFlagValue

class W3PairingMinFirmwareVersionFeatureFlag(
  featureFlagDao: FeatureFlagDao,
) : FeatureFlag<FeatureFlagValue.StringFlag>(
    identifier = "w3-pairing-min-firmware-version",
    title = "W3 Pairing Min Firmware Version",
    description = "Minimum firmware version required to pair W3 hardware",
    defaultFlagValue = FeatureFlagValue.StringFlag("1.2.0"),
    featureFlagDao = featureFlagDao,
    type = FeatureFlagValue.StringFlag::class
  )
