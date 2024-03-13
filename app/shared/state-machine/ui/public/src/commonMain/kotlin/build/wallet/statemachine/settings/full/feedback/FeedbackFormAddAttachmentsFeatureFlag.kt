package build.wallet.statemachine.settings.full.feedback

import build.wallet.feature.FeatureFlag
import build.wallet.feature.FeatureFlagDao
import build.wallet.feature.FeatureFlagValue

/**
 * Flag determining whether the customer can add attachments to the new feedback form is enabled.
 */
class FeedbackFormAddAttachmentsFeatureFlag(
  featureFlagDao: FeatureFlagDao,
) : FeatureFlag<FeatureFlagValue.BooleanFlag>(
    identifier = "feedback-form-add-attachments",
    title = "Feedback Form Add Attachments",
    description = "Controls whether or not the customer can add attachments to the customer feedback form.",
    defaultFlagValue = FeatureFlagValue.BooleanFlag(true),
    featureFlagDao = featureFlagDao,
    type = FeatureFlagValue.BooleanFlag::class
  )
