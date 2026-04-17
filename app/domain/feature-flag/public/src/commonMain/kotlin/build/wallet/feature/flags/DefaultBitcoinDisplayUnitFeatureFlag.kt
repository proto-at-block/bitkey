package build.wallet.feature.flags

import build.wallet.feature.FeatureFlag
import build.wallet.feature.FeatureFlagDao
import build.wallet.feature.FeatureFlagValue

/**
 * Feature flag to control the default Bitcoin display unit for new users.
 *
 * The string value should match a [BitcoinDisplayUnit] serialized name:
 * - "SATOSHI" — display as satoshis (e.g., "100,000 sats" or "₿100,000")
 * - "BITCOIN" — display as BTC (e.g., "0.001 BTC")
 *
 * Defaults to "SATOSHI". Set to "BITCOIN" via LaunchDarkly to roll out BTC as
 * the default for new users. Unrecognized values fall back to Satoshi.
 *
 * This flag only affects users who have never explicitly chosen a display unit.
 * Once a user sets their preference, the stored value takes precedence.
 */
class DefaultBitcoinDisplayUnitFeatureFlag(
  featureFlagDao: FeatureFlagDao,
) : FeatureFlag<FeatureFlagValue.StringFlag>(
    identifier = "mobile-default-bitcoin-display-unit",
    title = "Default Bitcoin Display Unit",
    description = "Controls the default Bitcoin display unit for new users (SATOSHI or BITCOIN)",
    defaultFlagValue = FeatureFlagValue.StringFlag("SATOSHI"),
    featureFlagDao = featureFlagDao,
    type = FeatureFlagValue.StringFlag::class
  )
