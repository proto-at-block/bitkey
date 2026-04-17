package build.wallet.feature.flags

import build.wallet.feature.FeatureFlag
import build.wallet.feature.FeatureFlagDao
import build.wallet.feature.FeatureFlagValue.DoubleFlag

/**
 * Flag controlling the cooldown delay before we let the user continue FWUP after an iOS NoSession
 * failure.
 *
 * Initial investigation suggests the underlying cooldown is usually in the 1-4 second range, so 8
 * seconds is intentionally conservative and leaves room to experiment downward later.
 *
 * This only works if the customer removes their Bitkey from the phone during the cooldown. If the
 * tag stays on the phone, iOS can keep spinning up background tag work, which defeats the cooldown
 * and prevents the NFC subsystem from settling.
 */
class FwupNfcCooldownPeriodSecondsFeatureFlag(
  featureFlagDao: FeatureFlagDao,
) : FeatureFlag<DoubleFlag>(
    identifier = "mobile-fwup-nfc-cooldown-period-seconds",
    title = "FWUP NFC Cooldown Period Seconds",
    description = "Cooldown delay shown before retrying an iOS firmware update NFC session.",
    defaultFlagValue = DoubleFlag(8.0),
    featureFlagDao = featureFlagDao,
    type = DoubleFlag::class
  )
