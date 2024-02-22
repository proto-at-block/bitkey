package build.wallet.fwup

import build.wallet.feature.FeatureFlag
import build.wallet.feature.FeatureFlagDao
import build.wallet.feature.FeatureFlagValue

/**
 * Flag determining whether we use the new or old paradigm for FWUP
 * to allow us to more easily turn it off if we encounter any issues
 * in testing. Currently defaults to true (new paradigm) on all builds.
 */
class FwupNewParadigmFeatureFlag(
  featureFlagDao: FeatureFlagDao,
) : FeatureFlag<FeatureFlagValue.BooleanFlag>(
    identifier = "fwup-new-paradigm",
    title = "FWUP New Paradigm",
    description = "Controls whether or not to use the new paradigm for FWUP",
    defaultFlagValue = FeatureFlagValue.BooleanFlag(true),
    featureFlagDao = featureFlagDao,
    type = FeatureFlagValue.BooleanFlag::class
  )
