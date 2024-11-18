package build.wallet.statemachine.platform.permissions

import androidx.compose.runtime.Composable
import build.wallet.analytics.events.EventTracker
import build.wallet.platform.permissions.PermissionChecker
import build.wallet.statemachine.core.BodyModel

expect class EnableNotificationsUiStateMachineImpl(
  notificationPermissionRequester: NotificationPermissionRequester,
  permissionChecker: PermissionChecker,
  eventTracker: EventTracker,
) : EnableNotificationsUiStateMachine {
  @Composable
  override fun model(props: EnableNotificationsUiProps): BodyModel
}
