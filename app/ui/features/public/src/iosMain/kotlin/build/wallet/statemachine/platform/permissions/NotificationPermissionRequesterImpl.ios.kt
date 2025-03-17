package build.wallet.statemachine.platform.permissions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.platform.permissions.PermissionStatus.Authorized
import build.wallet.platform.permissions.PermissionStatus.Denied
import build.wallet.platform.permissions.PushNotificationPermissionStatusProvider
import platform.UIKit.UIApplication
import platform.UIKit.registerForRemoteNotifications
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNUserNotificationCenter
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@BitkeyInject(AppScope::class)
class NotificationPermissionRequesterImpl(
  private val pushNotificationPermissionStatusProvider: PushNotificationPermissionStatusProvider,
) : NotificationPermissionRequester {
  @Composable
  override fun requestNotificationPermission(
    onGranted: () -> Unit,
    onDeclined: () -> Unit,
  ) {
    LaunchedEffect("showing-system-permission") {
      val granted = requestIOSNotificationPermission()
      if (granted) {
        registerForPushNotifications()
        pushNotificationPermissionStatusProvider.updatePushNotificationStatus(Authorized)
        onGranted()
      } else {
        pushNotificationPermissionStatusProvider.updatePushNotificationStatus(Denied)
        onDeclined()
      }
    }
  }

  private suspend fun requestIOSNotificationPermission() =
    suspendCoroutine { continuation ->
      val center = UNUserNotificationCenter.currentNotificationCenter()
      center.requestAuthorizationWithOptions(
        options = UNAuthorizationOptionAlert + UNAuthorizationOptionBadge
      ) { granted, _ ->
        continuation.resume(granted)
      }
    }

  private fun registerForPushNotifications() {
    dispatch_async(queue = dispatch_get_main_queue()) {
      UIApplication.sharedApplication.registerForRemoteNotifications()
    }
  }
}
