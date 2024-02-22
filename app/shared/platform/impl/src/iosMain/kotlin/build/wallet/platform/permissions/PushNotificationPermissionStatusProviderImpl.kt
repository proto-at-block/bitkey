package build.wallet.platform.permissions

import build.wallet.platform.PlatformContext
import build.wallet.platform.UNAuthorizationStatusExtensions
import build.wallet.platform.permissions.PermissionStatus.NotDetermined
import kotlinx.coroutines.flow.MutableStateFlow
import platform.UserNotifications.UNUserNotificationCenter

actual class PushNotificationPermissionStatusProviderImpl actual constructor(
  platformContext: PlatformContext,
) : PushNotificationPermissionStatusProvider {
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

  override fun pushNotificationStatus() = statusFlow

  override fun updatePushNotificationStatus(status: PermissionStatus) {
    statusFlow.tryEmit(status)
  }
}
