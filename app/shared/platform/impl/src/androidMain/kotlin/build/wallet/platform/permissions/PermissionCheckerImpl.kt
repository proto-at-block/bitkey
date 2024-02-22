package build.wallet.platform.permissions

import android.content.pm.PackageManager.PERMISSION_GRANTED
import androidx.core.content.ContextCompat
import build.wallet.platform.PlatformContext
import build.wallet.platform.permissions.PermissionStatus.Authorized
import build.wallet.platform.permissions.PermissionStatus.Denied

actual class PermissionCheckerImpl actual constructor(
  private val platformContext: PlatformContext,
  pushNotificationPermissionStatusProvider: PushNotificationPermissionStatusProvider,
) : PermissionChecker {
  override fun getPermissionStatus(permission: Permission): PermissionStatus {
    return when (val manifestPermission = permission.manifestPermission()) {
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
  }
}
