package build.wallet.bitcoin.lightning

import build.wallet.feature.FeatureFlag
import build.wallet.feature.FeatureFlagDao
import build.wallet.feature.FeatureFlagValue.BooleanFlag

/**
 * Flag determining whether or not lightning is available as an option.
 * Defaults to false on all builds except for Development
 */
class LightningIsAvailableFeatureFlag(
  featureFlagDao: FeatureFlagDao,
) : FeatureFlag<BooleanFlag>(
    identifier = "lightning-is-available",
    title = "Lightning Availability",
    description = "Controls whether or not the option for lightning appears in the main debug menu",
    defaultFlagValue = BooleanFlag(false),
    featureFlagDao = featureFlagDao,
    type = BooleanFlag::class
  )
