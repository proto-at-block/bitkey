package build.wallet.feature.flags

import build.wallet.feature.FeatureFlag
import build.wallet.feature.FeatureFlagDao
import build.wallet.feature.FeatureFlagValue

/**
 * Force a prompt on the home screen asking the user to start a sweep transaction.
 *
 * This flag can be used to help users who inadvertently sent funds to an old wallet address.
 * In the future, this will be replaced with logic to detect this state automatically.
 */
class PromptSweepFeatureFlag(
  featureFlagDao: FeatureFlagDao,
) : FeatureFlag<FeatureFlagValue.BooleanFlag>(
    identifier = "mobile-prompt-sweep",
    title = "Prompt for Sweep",
    description = "Manually enables a prompt on the home screen to start a sweep operation.",
    defaultFlagValue = FeatureFlagValue.BooleanFlag(false),
    featureFlagDao = featureFlagDao,
    type = FeatureFlagValue.BooleanFlag::class
  )
