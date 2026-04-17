package build.wallet.statemachine.settings.full.notifications

import bitkey.notifications.NotificationChannel
import build.wallet.notifications.NotificationTouchpointData
import build.wallet.statemachine.auth.ActionProofType
import build.wallet.statemachine.core.AppSegment

/**
 * App segment identifying the hardware auth flow for disabling notification channels.
 * Shared between [RecoveryChannelSettingsUiStateMachineImpl] and
 * [RecoveryChannelSettingsPresenter].
 */
internal object NotificationsDisableAppSegment : AppSegment {
  override val id: String = "NotificationsDisable"
}

/**
 * Returns a human-readable description of the disable operation for the given [NotificationChannel],
 * used as the subline on the hardware approval screen.
 * Shared between [RecoveryChannelSettingsUiStateMachineImpl] and [RecoveryChannelSettingsPresenter].
 */
internal fun NotificationChannel.disableOperationDescription(
  notificationTouchpointData: NotificationTouchpointData?,
): String =
  when (this) {
    NotificationChannel.Email ->
      notificationTouchpointData?.email?.value
        ?: "(Email Address)"
    NotificationChannel.Push -> "Push Notification"
    NotificationChannel.Sms ->
      notificationTouchpointData?.phoneNumber?.formattedDisplayValue
        ?: "(SMS Number)"
  }.let { "Recovery channel $it will be disabled" }

/**
 * Builds the [ActionProofType] required to disable the given [NotificationChannel].
 * Shared between [RecoveryChannelSettingsUiStateMachineImpl] and [RecoveryChannelSettingsPresenter].
 *
 * Throws [IllegalArgumentException] if [notificationTouchpointData] is missing required fields —
 * this path should not be reachable since the UI gates interaction until data is loaded.
 */
internal fun NotificationChannel.toDisableActionProofType(
  notificationTouchpointData: NotificationTouchpointData?,
): ActionProofType =
  when (this) {
    NotificationChannel.Email -> {
      val email = notificationTouchpointData?.email?.value
      val touchpointId = notificationTouchpointData?.emailTouchpointId
      require(!email.isNullOrBlank()) {
        "Cannot disable recovery email without a loaded email address."
      }
      require(!touchpointId.isNullOrBlank()) {
        "Cannot disable recovery email without a valid touchpointId."
      }
      ActionProofType.DisableRecoveryEmail(
        currentEmail = email,
        touchpointId = touchpointId
      )
    }
    NotificationChannel.Sms -> {
      val phone = notificationTouchpointData?.phoneNumber?.formattedE164Value
      val touchpointId = notificationTouchpointData?.phoneNumberTouchpointId
      require(!phone.isNullOrBlank()) {
        "Cannot disable recovery phone without a loaded phone number."
      }
      require(!touchpointId.isNullOrBlank()) {
        "Cannot disable recovery phone without a valid touchpointId."
      }
      ActionProofType.DisableRecoveryPhone(
        currentPhone = phone,
        touchpointId = touchpointId
      )
    }
    NotificationChannel.Push -> ActionProofType.DisablePushNotifications
  }
