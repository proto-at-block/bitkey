package build.wallet.statemachine.settings.full

import app.cash.turbine.test
import bitkey.ui.framework.NavigatorModelFake
import bitkey.ui.framework.NavigatorPresenterFake
import bitkey.ui.verification.TxVerificationPolicyProps
import bitkey.ui.verification.TxVerificationPolicyStateMachine
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.coroutines.turbine.awaitUntil
import build.wallet.coroutines.turbine.turbines
import build.wallet.fwup.FirmwareData.FirmwareUpdateState.PendingUpdate
import build.wallet.fwup.FirmwareDataPendingUpdateMock
import build.wallet.fwup.FirmwareDataServiceFake
import build.wallet.fwup.FwupDataMock
import build.wallet.platform.config.AppVariant
import build.wallet.statemachine.BodyStateMachineMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryDataMock
import build.wallet.statemachine.export.ExportToolsUiProps
import build.wallet.statemachine.export.ExportToolsUiStateMachine
import build.wallet.statemachine.inheritance.InheritanceManagementUiProps
import build.wallet.statemachine.inheritance.InheritanceManagementUiStateMachine
import build.wallet.statemachine.inheritance.ManagingInheritanceTab
import build.wallet.statemachine.money.currency.AppearancePreferenceProps
import build.wallet.statemachine.money.currency.AppearancePreferenceUiStateMachine
import build.wallet.statemachine.notifications.NotificationPreferencesProps
import build.wallet.statemachine.notifications.NotificationPreferencesUiStateMachine
import build.wallet.statemachine.recovery.cloud.RotateAuthKeyUIOrigin
import build.wallet.statemachine.recovery.cloud.RotateAuthKeyUIStateMachine
import build.wallet.statemachine.recovery.cloud.RotateAuthKeyUIStateMachineProps
import build.wallet.statemachine.recovery.socrec.TrustedContactManagementScreen
import build.wallet.statemachine.settings.SettingsListUiProps
import build.wallet.statemachine.settings.SettingsListUiProps.SettingsListRow.DebugMenu
import build.wallet.statemachine.settings.SettingsListUiStateMachine
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
import build.wallet.statemachine.status.StatusBannerModelMock
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitBodyMock
import build.wallet.statemachine.utxo.UtxoConsolidationProps
import build.wallet.statemachine.utxo.UtxoConsolidationUiStateMachine
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf

class SettingsHomeUiStateMachineImplTests : FunSpec({

  val propsOnBackCalls = turbines.create<Unit>("props onBack calls")
  val firmwareDataService = FirmwareDataServiceFake()

  val props =
    SettingsHomeUiProps(
      account = FullAccountMock,
      settingsListState = null,
      lostHardwareRecoveryData = LostHardwareRecoveryDataMock,
      homeStatusBannerModel = null,
      onBack = { propsOnBackCalls.add(Unit) },
      goToSecurityHub = {}
    )

  val navigatorPresenter = NavigatorPresenterFake()

  fun stateMachine(appVariant: AppVariant = AppVariant.Customer) =
    SettingsHomeUiStateMachineImpl(
      appVariant = appVariant,
      mobilePaySettingsUiStateMachine = object : MobilePaySettingsUiStateMachine,
        ScreenStateMachineMock<MobilePaySettingsUiProps>("mobile-txn") {},
      notificationPreferencesUiStateMachine = object : NotificationPreferencesUiStateMachine,
        ScreenStateMachineMock<NotificationPreferencesProps>("notifications-preferences") {},
      appearancePreferenceUiStateMachine = object : AppearancePreferenceUiStateMachine,
        ScreenStateMachineMock<AppearancePreferenceProps>("currency-preference") {},
      customElectrumServerSettingUiStateMachine = object :
        CustomElectrumServerSettingUiStateMachine,
        ScreenStateMachineMock<CustomElectrumServerProps>("custom-electrum-server") {},
      deviceSettingsUiStateMachine = object : DeviceSettingsUiStateMachine,
        ScreenStateMachineMock<DeviceSettingsProps>("device-settings") {},
      feedbackUiStateMachine = object : FeedbackUiStateMachine,
        ScreenStateMachineMock<FeedbackUiProps>("feedback") {},
      helpCenterUiStateMachine = object : HelpCenterUiStateMachine,
        ScreenStateMachineMock<HelpCenterUiProps>("help-center") {},
      settingsListUiStateMachine = object : SettingsListUiStateMachine,
        BodyStateMachineMock<SettingsListUiProps>("settings-list") {},
      rotateAuthKeyUIStateMachine = object : RotateAuthKeyUIStateMachine,
        ScreenStateMachineMock<RotateAuthKeyUIStateMachineProps>("rotate-auth-key") {},
      navigatorPresenter = navigatorPresenter,
      firmwareDataService = firmwareDataService,
      utxoConsolidationUiStateMachine = object : UtxoConsolidationUiStateMachine,
        ScreenStateMachineMock<UtxoConsolidationProps>("utxo-consolidation") {},
      inheritanceManagementUiStateMachine = object : InheritanceManagementUiStateMachine,
        ScreenStateMachineMock<InheritanceManagementUiProps>("inheritance-management") {},
      exportToolsUiStateMachine = object : ExportToolsUiStateMachine,
        ScreenStateMachineMock<ExportToolsUiProps>("export-tools") {},
      transactionVerificationPolicyStateMachine = object : TxVerificationPolicyStateMachine,
        ScreenStateMachineMock<TxVerificationPolicyProps>(
          "tx-verification-policy"
        ) {}
    )

  beforeTest {
    firmwareDataService.reset()
  }

  test("onBack calls props onBack") {
    stateMachine().test(props) {
      awaitBodyMock<SettingsListUiProps> {
        onBack()
      }
      propsOnBackCalls.awaitItem()
    }
  }

  test("settings list") {
    stateMachine().test(props) {
      awaitBodyMock<SettingsListUiProps> {
        supportedRows
          .map { it::class }.toSet()
          .shouldBe(
            setOf(
              SettingsListUiProps.SettingsListRow.BitkeyDevice::class,
              SettingsListUiProps.SettingsListRow.CustomElectrumServer::class,
              SettingsListUiProps.SettingsListRow.AppearancePreference::class,
              SettingsListUiProps.SettingsListRow.HelpCenter::class,
              SettingsListUiProps.SettingsListRow.MobilePay::class,
              SettingsListUiProps.SettingsListRow.NotificationPreferences::class,
              SettingsListUiProps.SettingsListRow.CriticalAlerts::class,
              SettingsListUiProps.SettingsListRow.ContactUs::class,
              SettingsListUiProps.SettingsListRow.TrustedContacts::class,
              SettingsListUiProps.SettingsListRow.CloudBackupHealth::class,
              SettingsListUiProps.SettingsListRow.RotateAuthKey::class,
              SettingsListUiProps.SettingsListRow.Biometric::class,
              SettingsListUiProps.SettingsListRow.InheritanceManagement::class,
              SettingsListUiProps.SettingsListRow.UtxoConsolidation::class,
              SettingsListUiProps.SettingsListRow.ExportTools::class
            )
          )
      }
    }
  }

  test("settings list deeplink") {
    val props = props.copy(
      settingsListState = SettingsHomeUiStateMachineImpl.SettingsListState.ShowingInheritanceUiState(
        ManagingInheritanceTab.Beneficiaries
      )
    )
    stateMachine().test(props) {
      awaitBodyMock<InheritanceManagementUiProps> {
        onBack()
      }
      awaitBodyMock<SettingsListUiProps>()
    }
  }

  test("open and close mobile pay") {
    stateMachine().test(props) {
      awaitBodyMock<SettingsListUiProps> {
        supportedRows.first { it is SettingsListUiProps.SettingsListRow.MobilePay }.onClick()
      }
      awaitBodyMock<MobilePaySettingsUiProps> {
        onBack()
      }
      awaitBodyMock<SettingsListUiProps>()
    }
  }

  test("open and close notifications") {
    stateMachine().test(props) {
      awaitBodyMock<SettingsListUiProps> {
        supportedRows.first { it is SettingsListUiProps.SettingsListRow.NotificationPreferences }
          .onClick()
      }

      awaitBodyMock<NotificationPreferencesProps> {
        onBack()
      }

      awaitBodyMock<SettingsListUiProps>()
    }
  }

  test("open and close custom electrum server") {
    stateMachine().test(props) {
      awaitBodyMock<SettingsListUiProps> {
        supportedRows.first { it is SettingsListUiProps.SettingsListRow.CustomElectrumServer }
          .onClick()
      }
      awaitBodyMock<CustomElectrumServerProps> {
        onBack()
      }
      awaitBodyMock<SettingsListUiProps>()
    }
  }

  test("open and close currency preference") {
    stateMachine().test(props) {
      awaitBodyMock<SettingsListUiProps> {
        supportedRows.first { it is SettingsListUiProps.SettingsListRow.AppearancePreference }
          .onClick()
      }
      awaitBodyMock<AppearancePreferenceProps> {
        onBack.shouldNotBeNull().invoke()
      }
      awaitBodyMock<SettingsListUiProps>()
    }
  }

  test("open and close Recovery Contacts settings") {
    stateMachine().test(props) {
      awaitBodyMock<SettingsListUiProps> {
        supportedRows.first { it is SettingsListUiProps.SettingsListRow.TrustedContacts }.onClick()
      }

      awaitBody<NavigatorModelFake> {
        initialScreen.shouldBeTypeOf<TrustedContactManagementScreen>()
        onExit()
      }

      awaitBodyMock<SettingsListUiProps>()
    }
  }

  test("open and close bitkey device settings") {
    stateMachine().test(props) {
      awaitBodyMock<SettingsListUiProps> {
        supportedRows.first { it is SettingsListUiProps.SettingsListRow.BitkeyDevice }.onClick()
      }
      awaitBodyMock<DeviceSettingsProps> {
        onBack()
      }
      awaitBodyMock<SettingsListUiProps>()
    }
  }

  test("open and close help center") {
    stateMachine().test(props) {
      awaitBodyMock<SettingsListUiProps> {
        supportedRows.first { it is SettingsListUiProps.SettingsListRow.HelpCenter }.onClick()
      }
      awaitBodyMock<HelpCenterUiProps> {
        onBack()
      }
      awaitBodyMock<SettingsListUiProps>()
    }
  }

  test("open and close mobile devices") {
    stateMachine().test(props) {
      awaitBodyMock<SettingsListUiProps> {
        supportedRows.first { it is SettingsListUiProps.SettingsListRow.RotateAuthKey }.onClick()
      }
      awaitBodyMock<RotateAuthKeyUIStateMachineProps> {
        origin.shouldBeTypeOf<RotateAuthKeyUIOrigin.Settings>().onBack()
      }
      awaitBodyMock<SettingsListUiProps>()
    }
  }

  test("all settings screen checks for new firmware") {
    val firstUpdate = FirmwareDataPendingUpdateMock
    val secondUpdate = firstUpdate.copy(
      firmwareUpdateState =
        PendingUpdate(
          fwupData = FwupDataMock.copy(version = "second update")
        )
    )

    // Prepare an update
    firmwareDataService.pendingUpdate = firstUpdate

    stateMachine().test(props) {
      awaitBodyMock<SettingsListUiProps> {
        firmwareDataService.firmwareData.test {
          awaitUntil(firstUpdate)
        }

        // Prepare a new update
        firmwareDataService.pendingUpdate = secondUpdate
        // Navigate somewhere else and back
        supportedRows.first { it is SettingsListUiProps.SettingsListRow.MobilePay }.onClick()
      }

      awaitBodyMock<MobilePaySettingsUiProps> {
        onBack()
      }
      awaitBodyMock<SettingsListUiProps>()
      firmwareDataService.firmwareData.test {
        awaitUntil(secondUpdate)
      }
    }
  }

  test("shows status bar from props") {
    stateMachine().test(props.copy(homeStatusBannerModel = StatusBannerModelMock)) {
      awaitItem().statusBannerModel.shouldNotBeNull()
    }
  }

  context("debug menu") {
    test("enabled in AppVariant.Team") {
      stateMachine(AppVariant.Team).test(props) {
        awaitBodyMock<SettingsListUiProps> {
          supportedRows.single { it is DebugMenu }
        }
      }
    }

    test("enabled in AppVariant.Development") {
      stateMachine(AppVariant.Development).test(props) {
        awaitBodyMock<SettingsListUiProps> {
          supportedRows.single { it is DebugMenu }
        }
      }
    }

    test("disabled in AppVariant.Customer") {
      stateMachine(AppVariant.Customer).test(props) {
        awaitBodyMock<SettingsListUiProps> {
          supportedRows.none { it is DebugMenu }
        }
      }
    }

    test("disabled in AppVariant.Emergency") {
      stateMachine(AppVariant.Emergency).test(props) {
        awaitBodyMock<SettingsListUiProps> {
          supportedRows.none { it is DebugMenu }
        }
      }
    }
  }
})
