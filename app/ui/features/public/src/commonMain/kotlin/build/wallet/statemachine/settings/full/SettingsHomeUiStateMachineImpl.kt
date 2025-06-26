package build.wallet.statemachine.settings.full

import androidx.compose.runtime.*
import bitkey.ui.framework.NavigatorPresenter
import bitkey.ui.screens.recoverychannels.RecoveryChannelSettingsScreen
import bitkey.ui.verification.TxVerificationPolicyProps
import bitkey.ui.verification.TxVerificationPolicyStateMachine
import build.wallet.bitkey.account.FullAccount
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.fwup.FirmwareDataService
import build.wallet.platform.config.AppVariant
import build.wallet.statemachine.biometric.BiometricSettingScreen
import build.wallet.statemachine.cloud.health.CloudBackupHealthDashboardScreen
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.dev.DebugMenuScreen
import build.wallet.statemachine.export.ExportToolsUiProps
import build.wallet.statemachine.export.ExportToolsUiStateMachine
import build.wallet.statemachine.inheritance.InheritanceManagementUiProps
import build.wallet.statemachine.inheritance.InheritanceManagementUiStateMachine
import build.wallet.statemachine.inheritance.ManagingInheritanceTab
import build.wallet.statemachine.money.currency.AppearancePreferenceProps
import build.wallet.statemachine.money.currency.AppearancePreferenceUiStateMachine
import build.wallet.statemachine.notifications.NotificationPreferencesProps
import build.wallet.statemachine.notifications.NotificationPreferencesProps.Source.Settings
import build.wallet.statemachine.notifications.NotificationPreferencesUiStateMachine
import build.wallet.statemachine.recovery.cloud.RotateAuthKeyUIOrigin
import build.wallet.statemachine.recovery.cloud.RotateAuthKeyUIStateMachine
import build.wallet.statemachine.recovery.cloud.RotateAuthKeyUIStateMachineProps
import build.wallet.statemachine.recovery.socrec.TrustedContactManagementScreen
import build.wallet.statemachine.settings.SettingsListUiProps
import build.wallet.statemachine.settings.SettingsListUiStateMachine
import build.wallet.statemachine.settings.full.SettingsHomeUiStateMachineImpl.SettingsListState.*
import build.wallet.statemachine.settings.full.device.DeviceSettingsProps
import build.wallet.statemachine.settings.full.device.DeviceSettingsUiStateMachine
import build.wallet.statemachine.settings.full.electrum.CustomElectrumServerProps
import build.wallet.statemachine.settings.full.electrum.CustomElectrumServerSettingUiStateMachine
import build.wallet.statemachine.settings.full.feedback.FeedbackUiProps
import build.wallet.statemachine.settings.full.feedback.FeedbackUiStateMachine
import build.wallet.statemachine.settings.full.mobilepay.MobilePaySettingsUiProps
import build.wallet.statemachine.settings.full.mobilepay.MobilePaySettingsUiStateMachine
import build.wallet.statemachine.settings.helpcenter.HelpCenterUiProps
import build.wallet.statemachine.settings.helpcenter.HelpCenterUiStateMachine
import build.wallet.statemachine.settings.showDebugMenu
import build.wallet.statemachine.utxo.UtxoConsolidationProps
import build.wallet.statemachine.utxo.UtxoConsolidationUiStateMachine
import build.wallet.ui.model.alert.ButtonAlertModel

@BitkeyInject(ActivityScope::class)
class SettingsHomeUiStateMachineImpl(
  private val appVariant: AppVariant,
  private val mobilePaySettingsUiStateMachine: MobilePaySettingsUiStateMachine,
  private val notificationPreferencesUiStateMachine: NotificationPreferencesUiStateMachine,
  private val appearancePreferenceUiStateMachine: AppearancePreferenceUiStateMachine,
  private val customElectrumServerSettingUiStateMachine: CustomElectrumServerSettingUiStateMachine,
  private val deviceSettingsUiStateMachine: DeviceSettingsUiStateMachine,
  private val feedbackUiStateMachine: FeedbackUiStateMachine,
  private val helpCenterUiStateMachine: HelpCenterUiStateMachine,
  private val settingsListUiStateMachine: SettingsListUiStateMachine,
  private val rotateAuthKeyUIStateMachine: RotateAuthKeyUIStateMachine,
  private val navigatorPresenter: NavigatorPresenter,
  private val firmwareDataService: FirmwareDataService,
  private val utxoConsolidationUiStateMachine: UtxoConsolidationUiStateMachine,
  private val inheritanceManagementUiStateMachine: InheritanceManagementUiStateMachine,
  private val exportToolsUiStateMachine: ExportToolsUiStateMachine,
  private val transactionVerificationPolicyStateMachine: TxVerificationPolicyStateMachine,
) : SettingsHomeUiStateMachine {
  @Composable
  override fun model(props: SettingsHomeUiProps): ScreenModel {
    var state: SettingsListState by remember {
      mutableStateOf(props.settingsListState ?: ShowingAllSettingsUiState)
    }

    return when (state) {
      is ShowingAllSettingsUiState -> {
        // Check for new FW whenever we navigate to the Settings screen
        LaunchedEffect("check-for-new-fw") {
          firmwareDataService.syncLatestFwupData()
        }

        var alertModel: ButtonAlertModel? by remember { mutableStateOf(null) }

        ScreenModel(
          body =
            settingsListUiStateMachine.model(
              props =
                SettingsListUiProps(
                  onBack = props.onBack,
                  supportedRows =
                    setOfNotNull(
                      SettingsListUiProps.SettingsListRow.BitkeyDevice {
                        state = ShowingBitkeyDeviceSettingsUiState
                      },
                      SettingsListUiProps.SettingsListRow.CustomElectrumServer {
                        state = ShowingCustomElectrumServerSettingsUiState
                      },
                      SettingsListUiProps.SettingsListRow.AppearancePreference {
                        state = ShowingAppearancePreferenceUiState
                      },
                      SettingsListUiProps.SettingsListRow.HelpCenter {
                        state = ShowingHelpCenterUiState
                      },
                      SettingsListUiProps.SettingsListRow.NotificationPreferences {
                        state = ShowingNotificationPreferencesUiState
                      },
                      SettingsListUiProps.SettingsListRow.CriticalAlerts {
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
                      },
                      SettingsListUiProps.SettingsListRow.DebugMenu {
                        state = ShowingDebugMenuUiState
                      }.takeIf { appVariant.showDebugMenu },
                      SettingsListUiProps.SettingsListRow.Biometric {
                        state = ShowingBiometricSettingUiState
                      },
                      SettingsListUiProps.SettingsListRow.UtxoConsolidation {
                        state = ShowingUtxoConsolidationUiState
                      },
                      SettingsListUiProps.SettingsListRow.InheritanceManagement {
                        state = ShowingInheritanceUiState(ManagingInheritanceTab.Beneficiaries)
                      },
                      SettingsListUiProps.SettingsListRow.ExportTools {
                        state = ShowingExportToolsUiState
                      }
                    ),
                  onShowAlert = { alertModel = it },
                  onDismissAlert = { alertModel = null },
                  goToSecurityHub = props.goToSecurityHub,
                  isLiteAccount = false
                )
            ),
          statusBannerModel = props.homeStatusBannerModel,
          alertModel = alertModel
        )
      }

      is ShowingMobilePaySettingsUiState ->
        mobilePaySettingsUiStateMachine.model(
          props = MobilePaySettingsUiProps(
            onBack = { state = ShowingAllSettingsUiState },
            account = props.account as FullAccount
          )
        )

      is ShowingNotificationPreferencesUiState ->
        notificationPreferencesUiStateMachine.model(
          NotificationPreferencesProps(
            accountId = props.account.accountId,
            onBack = { state = ShowingAllSettingsUiState },
            source = Settings,
            onComplete = { state = ShowingAllSettingsUiState }
          )
        )

      is ShowingRecoveryChannelsUiState ->
        navigatorPresenter.model(
          RecoveryChannelSettingsScreen(
            account = (props.account as FullAccount),
            origin = null
          ),
          onExit = {
            state = ShowingAllSettingsUiState
          }
        )

      is ShowingCustomElectrumServerSettingsUiState ->
        customElectrumServerSettingUiStateMachine.model(
          props =
            CustomElectrumServerProps(
              onBack = { state = ShowingAllSettingsUiState }
            )
        )

      is ShowingAppearancePreferenceUiState -> {
        appearancePreferenceUiStateMachine.model(
          props = AppearancePreferenceProps(
            onBack = { state = ShowingAllSettingsUiState }
          )
        )
      }

      is ShowingBitkeyDeviceSettingsUiState ->
        deviceSettingsUiStateMachine.model(
          props = DeviceSettingsProps(
            account = props.account as FullAccount,
            lostHardwareRecoveryData = props.lostHardwareRecoveryData,
            onBack = { state = ShowingAllSettingsUiState },
            onUnwindToMoneyHome = props.onBack
          )
        )

      is ShowingSendFeedbackUiState ->
        feedbackUiStateMachine.model(
          props =
            FeedbackUiProps(
              accountId = props.account.accountId,
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
        navigatorPresenter.model(
          initialScreen = TrustedContactManagementScreen(
            account = props.account as FullAccount,
            inviteCode = null,
            onExit = { state = ShowingAllSettingsUiState }
          ),
          onExit = { state = ShowingAllSettingsUiState }
        )

      is ShowingCloudBackupHealthUiState -> navigatorPresenter.model(
        CloudBackupHealthDashboardScreen(
          account = props.account as FullAccount,
          origin = null
        ),
        onExit = {
          state = ShowingAllSettingsUiState
        }
      )

      is ShowingRotateAuthKeyUiState ->
        rotateAuthKeyUIStateMachine.model(
          RotateAuthKeyUIStateMachineProps(
            account = props.account as FullAccount,
            origin = RotateAuthKeyUIOrigin.Settings(
              onBack = { state = ShowingAllSettingsUiState }
            )
          )
        )

      is ShowingDebugMenuUiState -> navigatorPresenter.model(
        initialScreen = DebugMenuScreen,
        onExit = { state = ShowingAllSettingsUiState }
      )
      ShowingBiometricSettingUiState -> navigatorPresenter.model(
        initialScreen = BiometricSettingScreen(
          fullAccount = (props.account as FullAccount),
          origin = null
        ),
        onExit = {
          state = ShowingAllSettingsUiState
        }
      )
      is ShowingUtxoConsolidationUiState -> utxoConsolidationUiStateMachine.model(
        UtxoConsolidationProps(
          onConsolidationSuccess = props.onBack,
          onBack = {
            state = ShowingAllSettingsUiState
          }
        )
      )
      is ShowingInheritanceUiState -> inheritanceManagementUiStateMachine.model(
        props = InheritanceManagementUiProps(
          account = props.account as FullAccount,
          selectedTab = (state as ShowingInheritanceUiState).selectedTab,
          onBack = { state = ShowingAllSettingsUiState },
          onGoToUtxoConsolidation = { state = ShowingUtxoConsolidationUiState }
        )
      )
      is ShowingExportToolsUiState -> exportToolsUiStateMachine.model(
        ExportToolsUiProps(
          onBack = { state = ShowingAllSettingsUiState }
        )
      )

      is ShowingTransactionVerificationPolicyState -> {
        transactionVerificationPolicyStateMachine.model(
          TxVerificationPolicyProps(
            account = props.account as FullAccount,
            onExit = props.onBack
          )
        )
      }
    }
  }

  sealed class SettingsListState {
    /**
     * Showing home of all settings.
     */
    data object ShowingAllSettingsUiState : SettingsListState()

    /**
     * Showing settings for Mobile Pay.
     */
    data object ShowingMobilePaySettingsUiState : SettingsListState()

    /**
     * Showing notification preferences for transactions and marketing
     */
    data object ShowingNotificationPreferencesUiState : SettingsListState()

    /**
     * Showing recovery channel configuration
     */
    data object ShowingRecoveryChannelsUiState : SettingsListState()

    /**
     * Showing settings for custom Electrum server
     */
    data object ShowingCustomElectrumServerSettingsUiState : SettingsListState()

    data object ShowingBitkeyDeviceSettingsUiState : SettingsListState()

    data object ShowingAppearancePreferenceUiState : SettingsListState()

    data object ShowingSendFeedbackUiState : SettingsListState()

    data object ShowingHelpCenterUiState : SettingsListState()

    data object ShowingTrustedContactsUiState : SettingsListState()

    data object ShowingCloudBackupHealthUiState : SettingsListState()

    /**
     * Showing the UI for rotating the app auth key,
     * removing access from other mobile devices.
     */
    data object ShowingRotateAuthKeyUiState : SettingsListState()

    data object ShowingDebugMenuUiState : SettingsListState()

    data object ShowingBiometricSettingUiState : SettingsListState()

    data object ShowingUtxoConsolidationUiState : SettingsListState()

    data class ShowingInheritanceUiState(
      val selectedTab: ManagingInheritanceTab,
    ) : SettingsListState()

    data object ShowingExportToolsUiState : SettingsListState()

    /**
     * Showing the UI for toggling/changing the Transaction Verification Policy.
     */
    data object ShowingTransactionVerificationPolicyState : SettingsListState()
  }
}
