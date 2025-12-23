package build.wallet.feature.flags

import build.wallet.feature.FeatureFlag
import build.wallet.feature.FeatureFlagDao
import build.wallet.feature.FeatureFlagValue

/**
 * Feature flag to control BIP 177 satoshi symbol display.
 *
 * When enabled, satoshi amounts display with the ₿ symbol prefix (e.g., "₿10,000")
 * instead of the "sats" suffix (e.g., "10,000 sats").
 *
 * @see <a href="https://github.com/bitcoin/bips/blob/master/bip-0177.mediawiki">BIP-177</a>
 */
class Bip177FeatureFlag(
  featureFlagDao: FeatureFlagDao,
) : FeatureFlag<FeatureFlagValue.BooleanFlag>(
    identifier = "mobile-bip-177-enabled",
    title = "BIP 177 ₿ Symbol",
    description = "Display satoshi amounts with ₿ symbol instead of 'sats' text",
    defaultFlagValue = FeatureFlagValue.BooleanFlag(false),
    featureFlagDao = featureFlagDao,
    type = FeatureFlagValue.BooleanFlag::class
  )
