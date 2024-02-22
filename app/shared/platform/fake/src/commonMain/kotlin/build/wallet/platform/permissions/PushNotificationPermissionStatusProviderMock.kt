package build.wallet.platform.permissions

import build.wallet.platform.permissions.PermissionStatus.NotDetermined
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class PushNotificationPermissionStatusProviderMock : PushNotificationPermissionStatusProvider {
  var pushNotificationStatusFlow = MutableStateFlow(NotDetermined)

  override fun pushNotificationStatus(): StateFlow<PermissionStatus> = pushNotificationStatusFlow

  override fun updatePushNotificationStatus(status: PermissionStatus) {
    TODO("Not yet implemented")
  }
}
