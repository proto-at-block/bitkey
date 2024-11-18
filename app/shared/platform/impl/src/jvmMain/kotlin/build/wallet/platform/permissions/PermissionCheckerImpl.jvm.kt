package build.wallet.platform.permissions

import build.wallet.platform.PlatformContext

actual class PermissionCheckerImpl actual constructor(
  platformContext: PlatformContext,
  pushNotificationPermissionStatusProvider: PushNotificationPermissionStatusProvider,
) : PermissionChecker {
  actual override fun getPermissionStatus(permission: Permission): PermissionStatus {
    return PermissionStatus.NotDetermined
  }
}
