package build.wallet.feature.flags

import build.wallet.feature.FeatureFlag
import build.wallet.feature.FeatureFlagDao
import build.wallet.feature.FeatureFlagValue.DoubleFlag

/**
 * Flag controlling how long the cooldown UI stays visible while the next FWUP NFC session starts
 * in the background after an iOS NoSession failure.
 */
class FwupNfcBackgroundRetryStartupRevealDelayMsFeatureFlag(
  featureFlagDao: FeatureFlagDao,
) : FeatureFlag<DoubleFlag>(
    identifier = "mobile-fwup-nfc-background-retry-startup-reveal-delay-ms",
    title = "FWUP NFC Background Retry Startup Reveal Delay Ms",
    description = "Delay before revealing the searching UI while a background FWUP retry starts.",
    defaultFlagValue = DoubleFlag(500.0),
    featureFlagDao = featureFlagDao,
    type = DoubleFlag::class
  )
