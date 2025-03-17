package build.wallet.platform.permissions

import kotlinx.coroutines.flow.StateFlow

interface PushNotificationPermissionStatusProvider {
  /**
   * A flow of [PermissionStatus] specifically for [PushNotification] permissions.
   * Sends updated values when updated via [updatePushNotificationStatus]
   *
   * Note!!! If Android permissions are changed from settings, this flow will not be updated.
   * Use [PermissionChecker] to check directly if current status is required.
   */
  fun pushNotificationStatus(): StateFlow<PermissionStatus>

  /**
   * Updates the emitted value for the [pushNotificationStatus] flow.
   * Should be called after permission is requested or when the app re-enters the
   * foreground in case permissions were changed at the system level.
   */
  fun updatePushNotificationStatus(status: PermissionStatus)
}
