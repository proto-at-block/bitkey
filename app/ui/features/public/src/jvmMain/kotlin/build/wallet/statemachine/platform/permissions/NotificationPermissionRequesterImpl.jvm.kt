package build.wallet.statemachine.platform.permissions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.platform.permissions.PermissionStatus.Authorized
import build.wallet.platform.permissions.PushNotificationPermissionStatusProvider

/**
 * Desktop (JVM) dev-mode notification permission requester.
 *
 * Desktop hosts have no OS notification permission prompt, so a real request would dead-end any
 * notification-gated flow. Instead this auto-grants deterministically: it marks the push status as
 * [Authorized] (matching Android's behavior when the platform has nothing to prompt) and invokes
 * [onGrant]. This keeps notification-gated flows drivable to completion on desktop.
 *
 * Dev-only: this `actual` lives in jvmMain; Android/iOS use the real system prompts.
 */
@BitkeyInject(AppScope::class)
class NotificationPermissionRequesterImpl(
  private val pushNotificationPermissionStatusProvider: PushNotificationPermissionStatusProvider,
) : NotificationPermissionRequester {
  @Composable
  override fun requestNotificationPermission(
    onGrant: () -> Unit,
    onDecline: () -> Unit,
  ) {
    val currentOnGrant by rememberUpdatedState(onGrant)
    LaunchedEffect("desktop-auto-grant-notifications") {
      pushNotificationPermissionStatusProvider.updatePushNotificationStatus(Authorized)
      currentOnGrant()
    }
  }
}
