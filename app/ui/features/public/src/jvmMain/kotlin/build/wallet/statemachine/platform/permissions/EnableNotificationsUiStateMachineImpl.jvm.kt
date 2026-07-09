package build.wallet.statemachine.platform.permissions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.analytics.events.EventTrackerContext
import build.wallet.analytics.events.screen.id.NotificationsEventTrackerScreenId
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.platform.permissions.EnableNotificationsUiStateMachineImpl.UiState.RequestingPermissionUiState
import build.wallet.statemachine.platform.permissions.EnableNotificationsUiStateMachineImpl.UiState.ShowingExplanationUiState
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Footer

/**
 * Desktop (JVM) dev-mode "enable notifications" state machine.
 *
 * Mirrors the Android flow: tapping "Enable notifications" routes through the
 * [NotificationPermissionRequester], which on desktop auto-grants deterministically (and updates
 * the push notification status). This keeps notification-gated flows drivable to completion without
 * an OS prompt, and ensures downstream status checks see the permission as granted.
 *
 * Dev-only: this `actual` lives in jvmMain; Android/iOS implement the real prompts.
 */
@BitkeyInject(ActivityScope::class)
class EnableNotificationsUiStateMachineImpl(
  private val notificationPermissionRequester: NotificationPermissionRequester,
) : EnableNotificationsUiStateMachine {
  @Composable
  override fun model(props: EnableNotificationsUiProps): BodyModel {
    var uiState: UiState by remember { mutableStateOf(ShowingExplanationUiState) }

    when (uiState) {
      RequestingPermissionUiState ->
        notificationPermissionRequester.requestNotificationPermission(
          onGranted = props.onComplete,
          onDeclined = props.onComplete
        )

      ShowingExplanationUiState -> Unit
    }

    return EnableNotificationsBodyModel(
      eventTrackerContext = props.eventTrackerContext,
      onBack = props.retreat.onRetreat,
      onComplete = { uiState = RequestingPermissionUiState }
    )
  }

  private sealed interface UiState {
    data object ShowingExplanationUiState : UiState

    data object RequestingPermissionUiState : UiState
  }
}

data class EnableNotificationsBodyModel(
  override val eventTrackerContext: EventTrackerContext,
  val onComplete: () -> Unit,
  override val onBack: () -> Unit,
) : FormBodyModel(
    id = NotificationsEventTrackerScreenId.ENABLE_PUSH_NOTIFICATIONS,
    eventTrackerContext = eventTrackerContext,
    onBack = onBack,
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
        onClick = StandardClick(onComplete)
      )
  )
