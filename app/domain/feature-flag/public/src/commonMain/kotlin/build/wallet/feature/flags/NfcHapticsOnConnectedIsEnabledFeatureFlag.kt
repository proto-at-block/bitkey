package build.wallet.feature.flags

import build.wallet.feature.FeatureFlag
import build.wallet.feature.FeatureFlagDao
import build.wallet.feature.FeatureFlagValue

/**
 * Flag determining whether or not vibration haptics on NFC connection are enabled.
 * Defaults to false on all builds.
 */
class NfcHapticsOnConnectedIsEnabledFeatureFlag(
  featureFlagDao: FeatureFlagDao,
) : FeatureFlag<FeatureFlagValue.BooleanFlag>(
    identifier = "nfc-haptics-connection-is-enabled",
    title = "NFC App Haptics – Connection – Enabled",
    description =
      "Controls whether or not the phone vibrates on NFC connection with Bitkey\n" +
        "Currently only supported on Android",
    defaultFlagValue = FeatureFlagValue.BooleanFlag(false),
    featureFlagDao = featureFlagDao,
    type = FeatureFlagValue.BooleanFlag::class
  )
