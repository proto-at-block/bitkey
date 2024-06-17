package build.wallet.platform.permissions

import build.wallet.platform.PlatformContext
import build.wallet.platform.permissions.PermissionStatus.NotDetermined
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

actual class PushNotificationPermissionStatusProviderImpl actual constructor(
  @Suppress("unused")
  private val platformContext: PlatformContext,
) : PushNotificationPermissionStatusProvider {
  private val internalFlow = MutableStateFlow(NotDetermined)

  override fun pushNotificationStatus(): StateFlow<PermissionStatus> = internalFlow

  override fun updatePushNotificationStatus(status: PermissionStatus) {
    internalFlow.value = status
  }
}
