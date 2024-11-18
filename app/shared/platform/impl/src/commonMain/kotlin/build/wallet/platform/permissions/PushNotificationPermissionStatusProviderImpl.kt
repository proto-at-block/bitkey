package build.wallet.platform.permissions

import build.wallet.platform.PlatformContext
import kotlinx.coroutines.flow.StateFlow

expect class PushNotificationPermissionStatusProviderImpl constructor(
  platformContext: PlatformContext,
) : PushNotificationPermissionStatusProvider {
  override fun pushNotificationStatus(): StateFlow<PermissionStatus>

  override fun updatePushNotificationStatus(status: PermissionStatus)
}
