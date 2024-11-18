package build.wallet.statemachine.platform.permissions

import androidx.compose.runtime.Composable
import build.wallet.platform.permissions.PushNotificationPermissionStatusProvider

actual class NotificationPermissionRequesterImpl actual constructor(
  pushNotificationPermissionStatusProvider: PushNotificationPermissionStatusProvider,
) : NotificationPermissionRequester {
  @Composable
  actual override fun requestNotificationPermission(
    onGranted: () -> Unit,
    onDeclined: () -> Unit,
  ) {
    // noop
  }
}
