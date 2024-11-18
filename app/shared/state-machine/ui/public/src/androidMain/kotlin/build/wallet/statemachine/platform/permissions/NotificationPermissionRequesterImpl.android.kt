package build.wallet.statemachine.platform.permissions

import android.Manifest
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import build.wallet.platform.permissions.PermissionStatus.Authorized
import build.wallet.platform.permissions.PermissionStatus.Denied
import build.wallet.platform.permissions.PushNotificationPermissionStatusProvider

actual class NotificationPermissionRequesterImpl actual constructor(
  private val pushNotificationPermissionStatusProvider: PushNotificationPermissionStatusProvider,
) : NotificationPermissionRequester {
  @Composable
  actual override fun requestNotificationPermission(
    onGranted: () -> Unit,
    onDeclined: () -> Unit,
  ) {
    if (VERSION.SDK_INT >= VERSION_CODES.TIRAMISU) {
      val requestPermissionLauncher =
        rememberLauncherForActivityResult(
          contract = RequestPermission(),
          onResult = { granted ->
            if (granted) {
              pushNotificationPermissionStatusProvider.updatePushNotificationStatus(Authorized)
              onGranted()
            } else {
              pushNotificationPermissionStatusProvider.updatePushNotificationStatus(Denied)
              onDeclined()
            }
          }
        )

      LaunchedEffect("showing-system-permission") {
        requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
      }
    } else {
      // This is a band-aid to handle edge cases when we can't request the permission below android
      // 13 but the permission is denied.
      // TODO W-3007 investigate why this case happens when it should not
      pushNotificationPermissionStatusProvider.updatePushNotificationStatus(Authorized)
      onGranted()
    }
  }
}
