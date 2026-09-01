package build.wallet.statemachine.core

import build.wallet.analytics.events.screen.id.GeneralEventTrackerScreenId
import build.wallet.platform.device.DevicePlatform
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel

/**
 * Full screen blocking model shown when [AgeRangeVerificationResult.Denied] is returned.
 *
 * This screen has no navigation - the user cannot proceed into the app until they resolve
 * their age verification status with the platform. It offers a Help Center link explaining
 * the 18+ requirement, and a retry affordance to re-run the age verification check (e.g.
 * after the user has updated their age status with the platform).
 *
 * @param devicePlatform The current device platform, used to customize the message
 *   to reference the correct platform's age verification system (Google Play vs Apple Accounts)
 * @param onLearnMore Invoked when the user taps "Learn more"; opens the Help Center article
 *   explaining the age requirement.
 * @param onRetry Invoked when the user taps "Try again"; re-runs the age verification check.
 * @see AgeRangeVerificationResult for all possible verification outcomes
 */
data class AgeRestrictedBodyModel(
  val devicePlatform: DevicePlatform,
  val onLearnMore: () -> Unit,
  val onRetry: () -> Unit,
) : FormBodyModel(
    id = GeneralEventTrackerScreenId.AGE_RESTRICTED,
    onBack = null,
    toolbar = null,
    header = FormHeaderModel(
      icon = Icon.LargeIconWarningFilled,
      headline = "Access denied",
      subline = when (devicePlatform) {
        DevicePlatform.Android ->
          "You must be 18 years old or older to access the Bitkey app. " +
            "Age verification is managed by Google Play."
        DevicePlatform.IOS ->
          "You must be 18 years old or older to access the Bitkey app. " +
            "Age verification is managed by Apple Accounts."
        DevicePlatform.Jvm ->
          "You must be 18 years old or older to access the Bitkey app. " +
            "Age verification is managed by the app store."
      }
    ),
    primaryButton = ButtonModel(
      text = "Learn more",
      treatment = ButtonModel.Treatment.Primary,
      size = ButtonModel.Size.Footer,
      onClick = StandardClick(onLearnMore)
    ),
    secondaryButton = ButtonModel(
      text = "Try again",
      treatment = ButtonModel.Treatment.Secondary,
      size = ButtonModel.Size.Footer,
      onClick = StandardClick(onRetry)
    )
  )
