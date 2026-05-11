package build.wallet.feature.flags

import build.wallet.feature.FeatureFlag
import build.wallet.feature.FeatureFlagDao
import build.wallet.feature.FeatureFlagValue

class OnboardingCanUseKeyboxKeysetsFeatureFlag(
  featureFlagDao: FeatureFlagDao,
) : FeatureFlag<FeatureFlagValue.BooleanFlag>(
    identifier = "mobile-onboarding-can-use-keybox-keysets-enabled",
    title = "Initial Can Use Keybox Keysets",
    description = "Controls whether new full account keyboxes start with authoritative keysets.",
    defaultFlagValue = FeatureFlagValue.BooleanFlag(true),
    featureFlagDao = featureFlagDao,
    type = FeatureFlagValue.BooleanFlag::class
  )
