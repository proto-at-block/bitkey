package build.wallet.feature.flags

import build.wallet.feature.FeatureFlag
import build.wallet.feature.FeatureFlagDao
import build.wallet.feature.FeatureFlagValue

class FingerprintResetMinFirmwareVersionFeatureFlag(
  featureFlagDao: FeatureFlagDao,
) : FeatureFlag<FeatureFlagValue.StringFlag>(
    identifier = "fingerprint-reset-min-firmware-version",
    title = "Fingerprint Reset Min Firmware Version",
    description = "Minimum firmware version required for fingerprint reset functionality",
    defaultFlagValue = FeatureFlagValue.StringFlag("1.0.98"),
    featureFlagDao = featureFlagDao,
    type = FeatureFlagValue.StringFlag::class
  )
