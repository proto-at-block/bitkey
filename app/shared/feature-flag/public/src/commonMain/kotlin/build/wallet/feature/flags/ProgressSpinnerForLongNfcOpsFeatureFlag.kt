package build.wallet.feature.flags

import build.wallet.feature.FeatureFlag
import build.wallet.feature.FeatureFlagDao
import build.wallet.feature.FeatureFlagValue.BooleanFlag

/**
 * Flag determining whether to show extra metadata/progress spinners when performing potentially
 * long nfc operations.
 */
class ProgressSpinnerForLongNfcOpsFeatureFlag(
  featureFlagDao: FeatureFlagDao,
) : FeatureFlag<BooleanFlag>(
    identifier = "mobile-show-progress-for-long-nfc-ops",
    title = "Show progress for long NFC operations",
    description = "When enabled, on Android displays an indeterminate for potentially long NFC operations " +
      "such as signing, speed ups, or utxo consolidations. On iOS, merely displays extra text on the nfc " +
      "screen indicating the operation could take awhile",
    defaultFlagValue = BooleanFlag(true),
    featureFlagDao = featureFlagDao,
    type = BooleanFlag::class
  )
