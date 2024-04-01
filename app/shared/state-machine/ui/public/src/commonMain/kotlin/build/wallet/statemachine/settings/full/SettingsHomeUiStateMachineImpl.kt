package build.wallet.statemachine.settings.full

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.statemachine.cloud.health.CloudBackupHealthDashboardProps
import build.wallet.statemachine.cloud.health.CloudBackupHealthDashboardUiStateMachine
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.money.currency.CurrencyPreferenceProps
import build.wallet.statemachine.money.currency.CurrencyPreferenceUiStateMachine
import build.wallet.statemachine.notifications.NotificationPreferencesProps
import build.wallet.statemachine.notifications.NotificationPreferencesProps.Source.Settings
import build.wallet.statemachine.notifications.NotificationPreferencesUiStateMachine
import build.wallet.statemachine.recovery.cloud.RotateAuthKeyUIOrigin
import build.wallet.statemachine.recovery.cloud.RotateAuthKeyUIStateMachine
import build.wallet.statemachine.recovery.cloud.RotateAuthKeyUIStateMachineProps
import build.wallet.statemachine.recovery.socrec.TrustedContactManagementProps
import build.wallet.statemachine.recovery.socrec.TrustedContactManagementUiStateMachine
import build.wallet.statemachine.settings.SettingsListUiProps
import build.wallet.statemachine.settings.SettingsListUiStateMachine
import build.wallet.statemachine.settings.full.SettingsHomeUiStateMachineImpl.State.ShowingAllSettingsUiState
import build.wallet.statemachine.settings.full.SettingsHomeUiStateMachineImpl.State.ShowingBitkeyDeviceSettingsUiState
import build.wallet.statemachine.settings.full.SettingsHomeUiStateMachineImpl.State.ShowingCloudBackupHealthUiState
import build.wallet.statemachine.settings.full.SettingsHomeUiStateMachineImpl.State.ShowingCurrencyPreferenceUiState
import build.wallet.statemachine.settings.full.SettingsHomeUiStateMachineImpl.State.ShowingCustomElectrumServerSettingsUiState
import build.wallet.statemachine.settings.full.SettingsHomeUiStateMachineImpl.State.ShowingHelpCenterUiState
import build.wallet.statemachine.settings.full.SettingsHomeUiStateMachineImpl.State.ShowingMobilePaySettingsUiState
import build.wallet.statemachine.settings.full.SettingsHomeUiStateMachineImpl.State.ShowingNotificationPreferencesUiState
import build.wallet.statemachine.settings.full.SettingsHomeUiStateMachineImpl.State.ShowingNotificationsSettingsUiState
import build.wallet.statemachine.settings.full.SettingsHomeUiStateMachineImpl.State.ShowingRecoveryChannelsUiState
import build.wallet.statemachine.settings.full.SettingsHomeUiStateMachineImpl.State.ShowingRotateAuthKeyUiState
import build.wallet.statemachine.settings.full.SettingsHomeUiStateMachineImpl.State.ShowingSendFeedbackUiState
import build.wallet.statemachine.settings.full.SettingsHomeUiStateMachineImpl.State.ShowingTrustedContactsUiState
import build.wallet.statemachine.settings.full.device.DeviceSettingsProps
import build.wallet.statemachine.settings.full.device.DeviceSettingsUiStateMachine
import build.wallet.statemachine.settings.full.electrum.CustomElectrumServerProps
import build.wallet.statemachine.settings.full.electrum.CustomElectrumServerSettingUiStateMachine
import build.wallet.statemachine.settings.full.feedback.FeedbackUiProps
import build.wallet.statemachine.settings.full.feedback.FeedbackUiStateMachine
import build.wallet.statemachine.settings.full.mobilepay.MobilePaySettingsUiProps
import build.wallet.statemachine.settings.full.mobilepay.MobilePaySettingsUiStateMachine
import build.wallet.statemachine.settings.full.notifications.NotificationsSettingsProps
import build.wallet.statemachine.settings.full.notifications.NotificationsSettingsUiStateMachine
import build.wallet.statemachine.settings.full.notifications.RecoveryChannelSettingsProps
import build.wallet.statemachine.settings.full.notifications.RecoveryChannelSettingsUiStateMachine
import build.wallet.statemachine.settings.helpcenter.HelpCenterUiProps
import build.wallet.statemachine.settings.helpcenter.HelpCenterUiStateMachine
import build.wallet.ui.model.alert.AlertModel

class SettingsHomeUiStateMachineImpl(
  private val mobilePaySettingsUiStateMachine: MobilePaySettingsUiStateMachine,
  private val notificationsSettingsUiStateMachine: NotificationsSettingsUiStateMachine,
  private val notificationPreferencesUiStateMachine: NotificationPreferencesUiStateMachine,
  private val recoveryChannelSettingsUiStateMachine: RecoveryChannelSettingsUiStateMachine,
  private val currencyPreferenceUiStateMachine: CurrencyPreferenceUiStateMachine,
  private val customElectrumServerSettingUiStateMachine: CustomElectrumServerSettingUiStateMachine,
  private val deviceSettingsUiStateMachine: DeviceSettingsUiStateMachine,
  private val feedbackUiStateMachine: FeedbackUiStateMachine,
  private val helpCenterUiStateMachine: HelpCenterUiStateMachine,
  private val trustedContactManagementUiStateMachine: TrustedContactManagementUiStateMachine,
  private val settingsListUiStateMachine: SettingsListUiStateMachine,
  private val cloudBackupHealthDashboardUiStateMachine: CloudBackupHealthDashboardUiStateMachine,
  private val rotateAuthKeyUIStateMachine: RotateAuthKeyUIStateMachine,
) : SettingsHomeUiStateMachine {
  @Composable
  override fun model(props: SettingsHomeUiProps): ScreenModel {
    var state: State by remember { mutableStateOf(ShowingAllSettingsUiState) }

    return when (state) {
      is ShowingAllSettingsUiState -> {
        // Check for new FW whenever we navigate to the Settings screen
        LaunchedEffect("check-for-new-fw") {
          props.firmwareData.checkForNewFirmware()
        }

        var alertModel: AlertModel? by remember { mutableStateOf(null) }

        ScreenModel(
          body =
            settingsListUiStateMachine.model(
              props =
                SettingsListUiProps(
                  onBack = props.onBack,
                  f8eEnvironment = props.accountData.account.config.f8eEnvironment,
                  supportedRows =
                    setOf(
                      SettingsListUiProps.SettingsListRow.BitkeyDevice {
                        state = ShowingBitkeyDeviceSettingsUiState
                      },
                      SettingsListUiProps.SettingsListRow.CustomElectrumServer {
                        state = ShowingCustomElectrumServerSettingsUiState
                      },
                      SettingsListUiProps.SettingsListRow.CurrencyPreference {
                        state = ShowingCurrencyPreferenceUiState
                      },
                      SettingsListUiProps.SettingsListRow.HelpCenter {
                        state = ShowingHelpCenterUiState
                      },
                      SettingsListUiProps.SettingsListRow.NotificationPreferences {
                        state = ShowingNotificationPreferencesUiState
                      },
                      SettingsListUiProps.SettingsListRow.RecoveryChannels {
                        state = ShowingRecoveryChannelsUiState
                      },
                      SettingsListUiProps.SettingsListRow.MobilePay {
                        state = ShowingMobilePaySettingsUiState
                      },
                      SettingsListUiProps.SettingsListRow.ContactUs {
                        state = ShowingSendFeedbackUiState
                      },
                      SettingsListUiProps.SettingsListRow.TrustedContacts {
                        state = ShowingTrustedContactsUiState
                      },
                      SettingsListUiProps.SettingsListRow.CloudBackupHealth {
                        state = ShowingCloudBackupHealthUiState
                      },
                      SettingsListUiProps.SettingsListRow.RotateAuthKey {
                        state = ShowingRotateAuthKeyUiState
                      }
                    ),
                  onShowAlert = { alertModel = it },
                  onDismissAlert = { alertModel = null }
                )
            ),
          bottomSheetModel = props.homeBottomSheetModel,
          statusBannerModel = props.homeStatusBannerModel,
          alertModel = alertModel
        )
      }

      is ShowingMobilePaySettingsUiState ->
        mobilePaySettingsUiStateMachine.model(
          props =
            MobilePaySettingsUiProps(
              onBack = { state = ShowingAllSettingsUiState },
              accountData = props.accountData,
              fiatCurrency = props.currencyPreferenceData.fiatCurrencyPreference
            )
        )

      is ShowingNotificationsSettingsUiState ->
        notificationsSettingsUiStateMachine.model(
          props =
            NotificationsSettingsProps(
              accountData = props.accountData,
              onBack = { state = ShowingAllSettingsUiState }
            )
        )

      is ShowingNotificationPreferencesUiState ->
        notificationPreferencesUiStateMachine.model(
          NotificationPreferencesProps(
            f8eEnvironment = props.accountData.account.config.f8eEnvironment,
            fullAccountId = props.accountData.account.accountId,
            onBack = { state = ShowingAllSettingsUiState },
            source = Settings,
            onComplete = { state = ShowingAllSettingsUiState }
          )
        )

      is ShowingRecoveryChannelsUiState ->
        recoveryChannelSettingsUiStateMachine.model(
          RecoveryChannelSettingsProps(
            props.accountData,
            onBack = { state = ShowingAllSettingsUiState }
          )
        )

      is ShowingCustomElectrumServerSettingsUiState ->
        customElectrumServerSettingUiStateMachine.model(
          props =
            CustomElectrumServerProps(
              onBack = { state = ShowingAllSettingsUiState },
              electrumServerPreferenceValue =
                props.electrumServerData.userDefinedElectrumServerPreferenceValue,
              disableCustomElectrumServer = {
                props.electrumServerData.disableCustomElectrumServer(
                  props.electrumServerData.userDefinedElectrumServerPreferenceValue
                )
              },
              activeNetwork = props.accountData.account.config.bitcoinNetworkType
            )
        )

      is ShowingCurrencyPreferenceUiState ->
        currencyPreferenceUiStateMachine.model(
          props =
            CurrencyPreferenceProps(
              onBack = { state = ShowingAllSettingsUiState },
              btcDisplayAmount = props.accountData.transactionsData.balance.total,
              currencyPreferenceData = props.currencyPreferenceData,
              onDone = null
            )
        )

      is ShowingBitkeyDeviceSettingsUiState ->
        deviceSettingsUiStateMachine.model(
          props =
            DeviceSettingsProps(
              accountData = props.accountData,
              firmwareData = props.firmwareData,
              fiatCurrency = props.currencyPreferenceData.fiatCurrencyPreference,
              onBack = { state = ShowingAllSettingsUiState }
            )
        )

      is ShowingSendFeedbackUiState ->
        feedbackUiStateMachine.model(
          props =
            FeedbackUiProps(
              f8eEnvironment = props.accountData.account.config.f8eEnvironment,
              accountId = props.accountData.account.accountId,
              onBack = { state = ShowingAllSettingsUiState }
            )
        )

      is ShowingHelpCenterUiState ->
        helpCenterUiStateMachine.model(
          props =
            HelpCenterUiProps(
              onBack = { state = ShowingAllSettingsUiState }
            )
        )

      is ShowingTrustedContactsUiState ->
        trustedContactManagementUiStateMachine.model(
          TrustedContactManagementProps(
            account = props.accountData.account,
            socRecRelationships = props.socRecRelationships,
            socRecActions = props.socRecActions,
            inviteCode = null,
            onExit = { state = ShowingAllSettingsUiState }
          )
        )

      is ShowingCloudBackupHealthUiState -> {
        cloudBackupHealthDashboardUiStateMachine.model(
          CloudBackupHealthDashboardProps(
            account = props.accountData.account,
            onExit = { state = ShowingAllSettingsUiState }
          )
        )
      }

      is ShowingRotateAuthKeyUiState ->
        rotateAuthKeyUIStateMachine.model(
          RotateAuthKeyUIStateMachineProps(
            account = props.accountData.account,
            origin = RotateAuthKeyUIOrigin.Settings(
              onBack = { state = ShowingAllSettingsUiState }
            )
          )
        )
    }
  }

  private sealed class State {
    /**
     * Showing home of all settings.
     */
    data object ShowingAllSettingsUiState : State()

    /**
     * Showing settings for Mobile Pay.
     */
    data object ShowingMobilePaySettingsUiState : State()

    /**
     * Showing settings for notification touchpoints (sms and email).
     */
    data object ShowingNotificationsSettingsUiState : State()

    /**
     * Showing notification preferences for transactions and marketing
     */
    data object ShowingNotificationPreferencesUiState : State()

    /**
     * Showing recovery channel configuration
     */
    data object ShowingRecoveryChannelsUiState : State()

    /**
     * Showing settings for custom Electrum server
     */
    data object ShowingCustomElectrumServerSettingsUiState : State()

    data object ShowingBitkeyDeviceSettingsUiState : State()

    data object ShowingCurrencyPreferenceUiState : State()

    data object ShowingSendFeedbackUiState : State()

    data object ShowingHelpCenterUiState : State()

    data object ShowingTrustedContactsUiState : State()

    data object ShowingCloudBackupHealthUiState : State()

    /**
     * Showing the UI for rotating the app auth key,
     * removing access from other mobile devices.
     */
    data object ShowingRotateAuthKeyUiState : State()
  }
}
