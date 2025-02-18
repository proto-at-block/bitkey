package build.wallet.platform.permissions

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.platform.UNAuthorizationStatusExtensions
import build.wallet.platform.permissions.PermissionStatus.NotDetermined
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import platform.UserNotifications.UNUserNotificationCenter

@BitkeyInject(AppScope::class)
class PushNotificationPermissionStatusProviderImpl : PushNotificationPermissionStatusProvider {
  private val statusFlow = MutableStateFlow(NotDetermined)

  init {
    val notificationCenter = UNUserNotificationCenter.currentNotificationCenter()
    notificationCenter.getNotificationSettingsWithCompletionHandler { settings ->
      // assign the authorization status to the one provided by the notification center
      statusFlow.update {
        when (settings) {
          null -> NotDetermined
          else ->
            UNAuthorizationStatusExtensions.convertToNotificationPermissionStatus(
              settings.authorizationStatus
            )
        }
      }
    }
  }

  override fun pushNotificationStatus(): StateFlow<PermissionStatus> = statusFlow

  override fun updatePushNotificationStatus(status: PermissionStatus) {
    statusFlow.tryEmit(status)
  }
}
