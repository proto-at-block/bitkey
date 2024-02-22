package build.wallet.statemachine.account.create.full.onboard.notifications

import build.wallet.platform.permissions.PermissionStatus.Authorized
import build.wallet.platform.permissions.PermissionStatus.Denied
import build.wallet.platform.permissions.PermissionStatus.NotDetermined
import build.wallet.platform.permissions.PushNotificationPermissionStatusProvider
import build.wallet.platform.settings.SystemSettingsLauncher
import build.wallet.statemachine.account.create.full.onboard.notifications.NotificationPreferencesSetupFormItemModel.State.Completed
import build.wallet.statemachine.account.create.full.onboard.notifications.NotificationPreferencesSetupFormItemModel.State.NeedsAction
import build.wallet.statemachine.account.create.full.onboard.notifications.NotificationPreferencesSetupFormItemModel.State.Skipped
import build.wallet.statemachine.account.create.full.onboard.notifications.NotificationPreferencesSetupUiStateMachineImpl.State.ShowingSetupInstructionsUiState.AlertState
import kotlinx.coroutines.flow.map

class NotificationPreferencesSetupPushItemModelProviderImpl(
  private val pushNotificationPermissionStatusProvider: PushNotificationPermissionStatusProvider,
  private val systemSettingsLauncher: SystemSettingsLauncher,
) : NotificationPreferencesSetupPushItemModelProvider {
  override fun model(onShowAlert: (AlertState) -> Unit) =
    pushNotificationPermissionStatusProvider
      .pushNotificationStatus()
      .map { permissionStatus ->
        NotificationPreferencesSetupFormItemModel(
          state =
            when (permissionStatus) {
              NotDetermined -> NeedsAction
              Denied -> Skipped
              Authorized -> Completed
            },
          onClick =
            when (permissionStatus) {
              NotDetermined -> {
                { onShowAlert(AlertState.SystemPromptRequestingPush) }
              }

              Denied -> {
                {
                  onShowAlert(
                    AlertState.OpenSettings(openAction = {
                      systemSettingsLauncher.launchSettings()
                    })
                  )
                }
              }

              Authorized -> null
            }
        )
      }
}
