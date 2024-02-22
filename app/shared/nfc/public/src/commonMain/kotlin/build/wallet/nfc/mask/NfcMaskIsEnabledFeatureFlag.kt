package build.wallet.nfc.mask

import build.wallet.feature.FeatureFlag
import build.wallet.feature.FeatureFlagDao
import build.wallet.feature.FeatureFlagValue.BooleanFlag
import build.wallet.platform.config.AppVariant
import build.wallet.platform.config.AppVariant.Beta
import build.wallet.platform.config.AppVariant.Customer
import build.wallet.platform.config.AppVariant.Development
import build.wallet.platform.config.AppVariant.Emergency
import build.wallet.platform.config.AppVariant.Team

/**
 * Flag determining whether or showing a mask on NFC interaction on iOS is enabled.
 * Defaults to true on all builds except for `Customer`
 */
class NfcMaskIsEnabledFeatureFlag(
  appVariant: AppVariant,
  featureFlagDao: FeatureFlagDao,
) : FeatureFlag<BooleanFlag>(
    identifier = "nfc-mask-is-enabled",
    title = "NFC Mask Enabled",
    description =
      "Controls whether or not a full screen overlay is shown on NFC interactions with Bitkey\n" +
        "Only supported on iOS",
    defaultFlagValue =
      BooleanFlag(
        value =
          when (appVariant) {
            Development, Team -> true
            Beta, Customer, Emergency -> false
          }
      ),
    featureFlagDao = featureFlagDao,
    type = BooleanFlag::class
  )
