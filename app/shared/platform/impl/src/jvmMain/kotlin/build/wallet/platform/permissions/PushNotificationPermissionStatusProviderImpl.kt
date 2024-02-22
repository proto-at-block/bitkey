package build.wallet.platform.permissions

import build.wallet.platform.PlatformContext
import build.wallet.platform.permissions.PermissionStatus.NotDetermined
import kotlinx.coroutines.flow.MutableStateFlow

actual class PushNotificationPermissionStatusProviderImpl actual constructor(
  @Suppress("unused")
  private val platformContext: PlatformContext,
) : PushNotificationPermissionStatusProvider {
  private val internalFlow = MutableStateFlow(NotDetermined)

  override fun pushNotificationStatus() = internalFlow

  override fun updatePushNotificationStatus(status: PermissionStatus) {
    internalFlow.value = status
  }
}
