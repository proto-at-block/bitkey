package build.wallet.platform.permissions

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.platform.permissions.Permission.*
import build.wallet.platform.permissions.PermissionStatus.Authorized

@BitkeyInject(AppScope::class)
class PermissionCheckerImpl(
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
