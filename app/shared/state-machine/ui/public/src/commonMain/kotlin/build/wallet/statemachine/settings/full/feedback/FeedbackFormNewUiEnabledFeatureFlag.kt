package build.wallet.statemachine.settings.full.feedback

import build.wallet.feature.FeatureFlag
import build.wallet.feature.FeatureFlagDao
import build.wallet.feature.FeatureFlagValue

/**
 * Flag determining whether or not the new feedback form is enabled. If not, the previous link to "Contact UI" will be present.
 */
class FeedbackFormNewUiEnabledFeatureFlag(
  featureFlagDao: FeatureFlagDao,
) : FeatureFlag<FeatureFlagValue.BooleanFlag>(
    identifier = "feedback-form-ui",
    title = "Customer Feedback Form UI",
    description = "Controls whether or not to show the new customer feedback form.",
    defaultFlagValue = FeatureFlagValue.BooleanFlag(false),
    featureFlagDao = featureFlagDao,
    type = FeatureFlagValue.BooleanFlag::class
  )
