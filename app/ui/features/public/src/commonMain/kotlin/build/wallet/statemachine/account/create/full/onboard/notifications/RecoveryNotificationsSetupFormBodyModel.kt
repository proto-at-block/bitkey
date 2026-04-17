package build.wallet.statemachine.account.create.full.onboard.notifications

import build.wallet.analytics.events.screen.id.NotificationsEventTrackerScreenId
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarModel

/**
 * Screen model for the recovery notifications setup screen.
 * This is shown during sequential onboarding flow to ask the user to enable push notifications.
 *
 * @property onAllowNotifications Called when the user taps "Allow notifications" to enable push.
 * @property onSkip Called when the user taps "Skip" to skip push notification setup.
 * @property onNavigateBack Called when the user taps the back button to return to the previous step.
 */
data class RecoveryNotificationsSetupFormBodyModel(
  val onAllowNotifications: () -> Unit,
  val onSkip: () -> Unit,
  val onNavigateBack: () -> Unit,
) : FormBodyModel(
    id = NotificationsEventTrackerScreenId.ENABLE_PUSH_NOTIFICATIONS,
    onBack = onNavigateBack,
    toolbar = ToolbarModel(
      leadingAccessory = BackAccessory(onClick = onNavigateBack)
    ),
    header = FormHeaderModel(
      headline = "Set up recovery notifications",
      subline = "Enabling push notifications for recovery verification is highly recommended and will help keep you, and your funds, safe in case you lose your Bitkey device."
    ),
    primaryButton = ButtonModel(
      text = "Allow notifications",
      treatment = ButtonModel.Treatment.Primary,
      size = ButtonModel.Size.Footer,
      onClick = StandardClick(onAllowNotifications)
    ),
    secondaryButton = ButtonModel(
      text = "Skip",
      treatment = ButtonModel.Treatment.Secondary,
      size = ButtonModel.Size.Footer,
      onClick = StandardClick(onSkip)
    )
  )
