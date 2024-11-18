package build.wallet.statemachine.platform.permissions

import androidx.compose.runtime.Composable
import build.wallet.platform.permissions.PushNotificationPermissionStatusProvider

expect class NotificationPermissionRequesterImpl constructor(
  pushNotificationPermissionStatusProvider: PushNotificationPermissionStatusProvider,
) : NotificationPermissionRequester {
  @Composable
  override fun requestNotificationPermission(
    onGranted: () -> Unit,
    onDeclined: () -> Unit,
  )
}
