package build.wallet.nfc.haptics

import build.wallet.feature.FeatureFlag
import build.wallet.feature.FeatureFlagDao
import build.wallet.feature.FeatureFlagValue.BooleanFlag

/**
 * Flag determining whether or not vibration haptics on NFC interaction are enabled.
 * Defaults to true on all builds.
 */
class NfcHapticsIsEnabledFeatureFlag(
  featureFlagDao: FeatureFlagDao,
) : FeatureFlag<BooleanFlag>(
    identifier = "nfc-haptics-is-enabled",
    title = "NFC App Haptics Enabled",
    description =
      "Controls whether or not the phone vibrates on NFC interactions with Bitkey\n" +
        "Currently only supported on Android",
    defaultFlagValue = BooleanFlag(true),
    featureFlagDao = featureFlagDao,
    type = BooleanFlag::class
  )
