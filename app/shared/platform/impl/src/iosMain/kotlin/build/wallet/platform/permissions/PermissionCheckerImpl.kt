package build.wallet.platform.permissions

import build.wallet.platform.PlatformContext
import build.wallet.platform.permissions.Permission.Camera
import build.wallet.platform.permissions.Permission.HapticsVibrator
import build.wallet.platform.permissions.Permission.PushNotifications
import build.wallet.platform.permissions.PermissionStatus.Authorized

actual class PermissionCheckerImpl actual constructor(
  platformContext: PlatformContext,
  private val pushNotificationPermissionStatusProvider: PushNotificationPermissionStatusProvider,
) : PermissionChecker {
  override fun getPermissionStatus(permission: Permission): PermissionStatus {
    val hasPermissions =
      when (permission) {
        Camera -> throw NotImplementedError("W-1779: Not implemented")
        HapticsVibrator -> Authorized
        PushNotifications -> pushNotificationPermissionStatusProvider.pushNotificationStatus().value
      }
    return hasPermissions
  }
}
