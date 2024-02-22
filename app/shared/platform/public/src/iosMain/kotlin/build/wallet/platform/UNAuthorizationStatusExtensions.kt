package build.wallet.platform

import build.wallet.platform.permissions.PermissionStatus
import platform.UserNotifications.UNAuthorizationStatus
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusDenied
import platform.UserNotifications.UNAuthorizationStatusEphemeral
import platform.UserNotifications.UNAuthorizationStatusNotDetermined
import platform.UserNotifications.UNAuthorizationStatusProvisional

object UNAuthorizationStatusExtensions {
  fun convertToNotificationPermissionStatus(
    authorizationStatus: UNAuthorizationStatus?,
  ): PermissionStatus {
    return when (authorizationStatus) {
      // we treat "not determined", "provisional" and "ephemeral" the same for now
      UNAuthorizationStatusNotDetermined,
      UNAuthorizationStatusProvisional,
      UNAuthorizationStatusEphemeral,
      -> PermissionStatus.NotDetermined
      UNAuthorizationStatusDenied -> PermissionStatus.Denied
      UNAuthorizationStatusAuthorized -> PermissionStatus.Authorized
      else -> {
        PermissionStatus.NotDetermined
      }
    }
  }
}
