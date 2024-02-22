package build.wallet.platform.permissions

import kotlinx.coroutines.flow.StateFlow

interface PushNotificationPermissionStatusProvider {
  /**
   * A flow of [PermissionStatus] specifically for [PushNotification] permissions.
   * Sends updated values when updated via [updatePushNotificationStatus]
   */
  fun pushNotificationStatus(): StateFlow<PermissionStatus>

  /**
   * Updates the emitted value for the [pushNotificationStatus] flow.
   * Should be called after permission is requested or when the app re-enters the
   * foreground in case permissions were changed at the system level.
   */
  fun updatePushNotificationStatus(status: PermissionStatus)
}
