package build.wallet.statemachine.core

import build.wallet.analytics.events.screen.id.GeneralEventTrackerScreenId
import build.wallet.platform.device.DevicePlatform
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.ui.model.toolbar.ToolbarModel

/**
 * Full screen blocking model shown when [AgeRangeVerificationResult.Denied] is returned.
 *
 * This screen has no navigation and no action buttons - the user cannot proceed
 * until they resolve their age verification status with the platform.
 *
 * @param devicePlatform The current device platform, used to customize the message
 *   to reference the correct platform's age verification system (Google Play vs Apple Accounts)
 * @see AgeRangeVerificationResult for all possible verification outcomes
 */
data class AgeRestrictedBodyModel(
  val devicePlatform: DevicePlatform,
) : FormBodyModel(
    id = GeneralEventTrackerScreenId.AGE_RESTRICTED,
    onBack = null,
    toolbar = ToolbarModel(),
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
    primaryButton = null,
    secondaryButton = null
  )
