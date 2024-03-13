package build.wallet.statemachine.platform.permissions

import androidx.compose.runtime.Composable
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.events.screen.id.NotificationsEventTrackerScreenId
import build.wallet.platform.permissions.PermissionChecker
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Footer

actual class EnableNotificationsUiStateMachineImpl actual constructor(
  notificationPermissionRequester: NotificationPermissionRequester,
  permissionChecker: PermissionChecker,
  eventTracker: EventTracker,
) : EnableNotificationsUiStateMachine {
  @Composable
  override fun model(props: EnableNotificationsUiProps): BodyModel {
    return FormBodyModel(
      id = NotificationsEventTrackerScreenId.ENABLE_PUSH_NOTIFICATIONS,
      eventTrackerScreenIdContext = props.eventTrackerContext,
      onBack = props.retreat.onRetreat,
      toolbar = null,
      header =
        FormHeaderModel(
          headline = "Turn on notifications"
        ),
      primaryButton =
        ButtonModel(
          text = "Enable notifications",
          isEnabled = true,
          isLoading = false,
          size = Footer,
          onClick = StandardClick(props.onComplete)
        )
    )
  }
}
