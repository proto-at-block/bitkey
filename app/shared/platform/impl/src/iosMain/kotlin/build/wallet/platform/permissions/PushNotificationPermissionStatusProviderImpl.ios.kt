package build.wallet.platform.permissions

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.platform.UNAuthorizationStatusExtensions
import build.wallet.platform.permissions.PermissionStatus.NotDetermined
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import platform.UserNotifications.UNUserNotificationCenter

@BitkeyInject(AppScope::class)
class PushNotificationPermissionStatusProviderImpl : PushNotificationPermissionStatusProvider {
  private lateinit var statusFlow: MutableStateFlow<PermissionStatus>

  init {
    val notificationCenter = UNUserNotificationCenter.currentNotificationCenter()
    notificationCenter.getNotificationSettingsWithCompletionHandler { settings ->
      // assign the authorization status to the one provided by the notification center
      statusFlow =
        MutableStateFlow(
          value =
            when (settings) {
              null -> NotDetermined
              else ->
                UNAuthorizationStatusExtensions.convertToNotificationPermissionStatus(
                  settings.authorizationStatus
                )
            }
        )
    }
  }

  override fun pushNotificationStatus(): StateFlow<PermissionStatus> = statusFlow

  override fun updatePushNotificationStatus(status: PermissionStatus) {
    statusFlow.tryEmit(status)
  }
}
