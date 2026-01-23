package build.wallet.feature.flags

import build.wallet.feature.FeatureFlag
import build.wallet.feature.FeatureFlagDao
import build.wallet.feature.FeatureFlagValue

/**
 * Flag controlling the maximum number of NFC session retry attempts
 * when a session is invalidated unexpectedly.
 * Defaults to 3 attempts. Set to 0 to disable retry mechanism.
 */
class NfcSessionRetryAttemptsFeatureFlag(
  featureFlagDao: FeatureFlagDao,
) : FeatureFlag<FeatureFlagValue.DoubleFlag>(
    identifier = "mobile-nfc-session-retry-attempts",
    title = "NFC Session Retry Attempts",
    description = "Maximum number of retry attempts for NFC sessions that are invalidated unexpectedly. Set to 0 to disable retries.",
    defaultFlagValue = FeatureFlagValue.DoubleFlag(0.0),
    featureFlagDao = featureFlagDao,
    type = FeatureFlagValue.DoubleFlag::class
  )
