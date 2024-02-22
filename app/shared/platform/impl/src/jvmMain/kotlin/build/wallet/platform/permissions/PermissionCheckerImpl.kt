package build.wallet.platform.permissions

import build.wallet.platform.PlatformContext

actual class PermissionCheckerImpl actual constructor(
  platformContext: PlatformContext,
  pushNotificationPermissionStatusProvider: PushNotificationPermissionStatusProvider,
) : PermissionChecker {
  override fun getPermissionStatus(permission: Permission): PermissionStatus {
    return PermissionStatus.NotDetermined
  }
}
