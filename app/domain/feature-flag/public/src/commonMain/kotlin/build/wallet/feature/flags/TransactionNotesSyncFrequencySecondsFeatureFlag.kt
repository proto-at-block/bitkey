package build.wallet.feature.flags

import build.wallet.feature.FeatureFlag
import build.wallet.feature.FeatureFlagDao
import build.wallet.feature.FeatureFlagValue

class TransactionNotesSyncFrequencySecondsFeatureFlag(
  featureFlagDao: FeatureFlagDao,
) : FeatureFlag<FeatureFlagValue.DoubleFlag>(
    identifier = "mobile-transaction-notes-sync-frequency-seconds",
    title = "Transaction Notes Sync Frequency Seconds",
    description = "How often transaction notes sync runs while the app is foregrounded.",
    defaultFlagValue = FeatureFlagValue.DoubleFlag(900.0),
    featureFlagDao = featureFlagDao,
    type = FeatureFlagValue.DoubleFlag::class
  )
