package build.wallet.platform.permissions

import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import build.wallet.platform.PlatformContext
import build.wallet.platform.permissions.PermissionStatus.Authorized
import build.wallet.platform.permissions.PermissionStatus.Denied

actual class PermissionCheckerImpl actual constructor(
  private val platformContext: PlatformContext,
  pushNotificationPermissionStatusProvider: PushNotificationPermissionStatusProvider,
) : PermissionChecker {
  override fun getPermissionStatus(permission: Permission): PermissionStatus {
    return if (permission == Permission.PushNotifications && VERSION.SDK_INT < VERSION_CODES.TIRAMISU) {
      oldAndroidPush()
    } else {
      newAndroid(permission)
    }
  }

  private fun newAndroid(permission: Permission) =
    when (val manifestPermission = permission.manifestPermission()) {
      null -> Authorized
      else ->
        when (
          ContextCompat.checkSelfPermission(
            platformContext.appContext,
            manifestPermission
          )
        ) {
          PERMISSION_GRANTED -> Authorized
          else -> Denied
        }
    }

  private fun oldAndroidPush() =
    if (NotificationManagerCompat.from(platformContext.appContext)
        .areNotificationsEnabled()
    ) {
      Authorized
    } else {
      Denied
    }
}
