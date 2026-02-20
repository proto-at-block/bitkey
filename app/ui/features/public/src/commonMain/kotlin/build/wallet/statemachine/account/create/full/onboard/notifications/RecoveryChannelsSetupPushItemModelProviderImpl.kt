package build.wallet.statemachine.account.create.full.onboard.notifications

import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.platform.permissions.PermissionStatus.Authorized
import build.wallet.platform.permissions.PermissionStatus.Denied
import build.wallet.platform.permissions.PermissionStatus.NotDetermined
import build.wallet.platform.permissions.PushNotificationPermissionStatusProvider
import build.wallet.platform.settings.SystemSettingsLauncher
import build.wallet.statemachine.account.create.full.onboard.notifications.RecoveryChannelsSetupFormItemModel.State.Completed
import build.wallet.statemachine.account.create.full.onboard.notifications.RecoveryChannelsSetupFormItemModel.State.NotCompleted
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@BitkeyInject(ActivityScope::class)
class RecoveryChannelsSetupPushItemModelProviderImpl(
  private val appCoroutineScope: CoroutineScope,
  private val pushNotificationPermissionStatusProvider: PushNotificationPermissionStatusProvider,
  private val systemSettingsLauncher: SystemSettingsLauncher,
) : RecoveryChannelsSetupPushItemModelProvider {
  override fun model(
    onShowAlert: (RecoveryChannelsSetupPushActionState) -> Unit,
  ): StateFlow<RecoveryChannelsSetupFormItemModel> =
    pushNotificationPermissionStatusProvider
      .pushNotificationStatus()
      .map { permissionStatus ->
        RecoveryChannelsSetupFormItemModel(
          state =
            when (permissionStatus) {
              NotDetermined, Denied -> NotCompleted
              Authorized -> Completed
            },
          uiErrorHint = UiErrorHint.None,
          onClick =
            when (permissionStatus) {
              NotDetermined -> {
                { onShowAlert(RecoveryChannelsSetupPushActionState.AppInfoPromptRequestingPush) }
              }

              Denied -> {
                {
                  onShowAlert(
                    RecoveryChannelsSetupPushActionState.OpenSettings(openAction = {
                      systemSettingsLauncher.launchAppSettings()
                    })
                  )
                }
              }

              Authorized -> {
                {
                  onShowAlert(
                    RecoveryChannelsSetupPushActionState.OpenSettings(openAction = {
                      systemSettingsLauncher.launchAppSettings()
                    })
                  )
                }
              }
            }
        )
      }
      .stateIn(
        scope = appCoroutineScope,
        started = SharingStarted.Eagerly,
        initialValue = RecoveryChannelsSetupFormItemModel(
          state = NotCompleted,
          uiErrorHint = UiErrorHint.None,
          onClick = { onShowAlert(RecoveryChannelsSetupPushActionState.AppInfoPromptRequestingPush) }
        )
      )
}
