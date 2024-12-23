package build.wallet.platform.permissions

import android.app.Application
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.platform.permissions.PermissionStatus.Authorized
import build.wallet.platform.permissions.PermissionStatus.Denied

@BitkeyInject(AppScope::class)
class PermissionCheckerImpl(
  private val application: Application,
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
            application,
            manifestPermission
          )
        ) {
          PERMISSION_GRANTED -> Authorized
          else -> Denied
        }
    }

  private fun oldAndroidPush() =
    if (NotificationManagerCompat.from(application)
        .areNotificationsEnabled()
    ) {
      Authorized
    } else {
      Denied
    }
}
