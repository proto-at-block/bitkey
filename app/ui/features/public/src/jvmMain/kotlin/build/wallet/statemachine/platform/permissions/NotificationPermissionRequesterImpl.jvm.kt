package build.wallet.statemachine.platform.permissions

import androidx.compose.runtime.Composable
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject

@BitkeyInject(AppScope::class)
class NotificationPermissionRequesterImpl : NotificationPermissionRequester {
  @Composable
  override fun requestNotificationPermission(
    onGranted: () -> Unit,
    onDeclined: () -> Unit,
  ) {
    // noop
  }
}
