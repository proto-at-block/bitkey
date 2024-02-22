package build.wallet.nfc.haptics

import build.wallet.feature.FeatureFlag
import build.wallet.feature.FeatureFlagDao
import build.wallet.feature.FeatureFlagValue

/**
 * Flag determining whether or not vibration haptics on NFC failure are enabled.
 * Defaults to true on all builds.
 */
class NfcHapticsOnFailureIsEnabledFeatureFlag(
  featureFlagDao: FeatureFlagDao,
) : FeatureFlag<FeatureFlagValue.BooleanFlag>(
    identifier = "nfc-haptics-failure-is-enabled",
    title = "NFC App Haptics – Failure – Enabled",
    description =
      "Controls whether or not the phone vibrates on unsuccessful NFC interactions with Bitkey\n" +
        "Currently only supported on Android",
    defaultFlagValue = FeatureFlagValue.BooleanFlag(true),
    featureFlagDao = featureFlagDao,
    type = FeatureFlagValue.BooleanFlag::class
  )
