package build.wallet.statemachine.platform.permissions

import androidx.compose.runtime.*
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.events.EventTrackerContext
import build.wallet.analytics.events.screen.id.NotificationsEventTrackerScreenId
import build.wallet.analytics.v1.Action.ACTION_APP_PUSH_NOTIFICATIONS_DISABLED
import build.wallet.analytics.v1.Action.ACTION_APP_PUSH_NOTIFICATIONS_ENABLED
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.platform.permissions.PermissionChecker
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.platform.permissions.EnableNotificationsUiStateMachineImpl.UiState.ShowingExplanationUiState
import build.wallet.statemachine.platform.permissions.EnableNotificationsUiStateMachineImpl.UiState.ShowingSystemPermissionUiState
import build.wallet.statemachine.platform.permissions.NotificationRationale.Generic
import build.wallet.statemachine.platform.permissions.NotificationRationale.Recovery
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Footer
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel

@BitkeyInject(ActivityScope::class)
class EnableNotificationsUiStateMachineImpl(
  private val notificationPermissionRequester: NotificationPermissionRequester,
  @Suppress("unused")
  private val permissionChecker: PermissionChecker,
  private val eventTracker: EventTracker,
) : EnableNotificationsUiStateMachine {
  @Composable
  override fun model(props: EnableNotificationsUiProps): BodyModel {
    var uiState: UiState by remember { mutableStateOf(ShowingExplanationUiState) }

    when (uiState) {
      ShowingSystemPermissionUiState -> {
        notificationPermissionRequester.requestNotificationPermission(
          onGranted = {
            eventTracker.track(ACTION_APP_PUSH_NOTIFICATIONS_ENABLED)
            props.onComplete()
          },
          onDeclined = {
            eventTracker.track(ACTION_APP_PUSH_NOTIFICATIONS_DISABLED)
            props.onComplete()
          }
        )
      }

      else -> {}
    }

    val sublineMessage = when (props.rationale) {
      Generic -> "Keep your wallet secure and stay updated."
      Recovery -> "You'll be notified with any updates to the status of your Bitkey recovery."
    }

    return EnableNotificationsBodyModel(
      eventTrackerContext = props.eventTrackerContext,
      subline = sublineMessage,
      onClick = { uiState = ShowingSystemPermissionUiState },
      onBack = props.retreat.onRetreat,
      leadingToolbarAccessory = props.retreat.leadingToolbarAccessory
    )
  }

  private sealed interface UiState {
    data object ShowingExplanationUiState : UiState

    data object ShowingSystemPermissionUiState : UiState
  }
}

data class EnableNotificationsBodyModel(
  val subline: String,
  val onClick: () -> Unit,
  val leadingToolbarAccessory: ToolbarAccessoryModel,
  override val onBack: (() -> Unit)?,
  override val eventTrackerContext: EventTrackerContext,
) : FormBodyModel(
    id = NotificationsEventTrackerScreenId.ENABLE_PUSH_NOTIFICATIONS,
    eventTrackerContext = eventTrackerContext,
    toolbar = ToolbarModel(leadingAccessory = leadingToolbarAccessory),
    onBack = onBack,
    header = FormHeaderModel(
      headline = "Enable Push Notifications on this Phone.",
      subline = subline
    ),
    primaryButton =
      ButtonModel(
        text = "Enable",
        size = Footer,
        onClick = StandardClick(onClick)
      )
  )
