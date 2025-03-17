package build.wallet.platform.permissions

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.platform.permissions.PermissionStatus.NotDetermined
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@BitkeyInject(AppScope::class)
class PushNotificationPermissionStatusProviderImpl : PushNotificationPermissionStatusProvider {
  private val internalFlow = MutableStateFlow(NotDetermined)

  override fun pushNotificationStatus(): StateFlow<PermissionStatus> = internalFlow

  override fun updatePushNotificationStatus(status: PermissionStatus) {
    internalFlow.value = status
  }
}
