package build.wallet.statemachine.account.notifications

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign
import build.wallet.statemachine.platform.permissions.NotificationPermissionRequester

class NotificationPermissionRequesterMock(
  val turbine: (String) -> Turbine<Any>,
) : NotificationPermissionRequester {
  var successful = true

  val requestNotificationPermissionCalls = turbine("request notification permission calls")

  @Composable
  override fun requestNotificationPermission(
    onGranted: () -> Unit,
    onDeclined: () -> Unit,
  ) {
    LaunchedEffect("request-notification-permission") {
      requestNotificationPermissionCalls += Unit
    }
    if (successful) onGranted() else onDeclined()
  }

  fun reset() {
    successful = true
  }
}
