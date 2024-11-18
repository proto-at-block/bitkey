package build.wallet.platform.permissions

import build.wallet.platform.PlatformContext

expect class PermissionCheckerImpl constructor(
  platformContext: PlatformContext,
  pushNotificationPermissionStatusProvider: PushNotificationPermissionStatusProvider,
) : PermissionChecker {
  override fun getPermissionStatus(permission: Permission): PermissionStatus
}
