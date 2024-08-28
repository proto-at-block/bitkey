package build.wallet.statemachine.settings.full

import build.wallet.coroutines.turbine.turbines
import build.wallet.fwup.FirmwareData.FirmwareUpdateState.PendingUpdate
import build.wallet.fwup.FirmwareDataPendingUpdateMock
import build.wallet.fwup.FirmwareDataServiceFake
import build.wallet.fwup.FwupDataMock
import build.wallet.platform.config.AppVariant
import build.wallet.statemachine.BodyStateMachineMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.biometric.BiometricSettingUiProps
import build.wallet.statemachine.biometric.BiometricSettingUiStateMachine
import build.wallet.statemachine.cloud.health.CloudBackupHealthDashboardProps
import build.wallet.statemachine.cloud.health.CloudBackupHealthDashboardUiStateMachine
import build.wallet.statemachine.core.awaitScreenWithBodyModelMock
import build.wallet.statemachine.core.input.SheetModelMock
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.keybox.ActiveKeyboxLoadedDataMock
import build.wallet.statemachine.dev.DebugMenuProps
import build.wallet.statemachine.dev.DebugMenuStateMachine
import build.wallet.statemachine.money.currency.CurrencyPreferenceProps
import build.wallet.statemachine.money.currency.CurrencyPreferenceUiStateMachine
import build.wallet.statemachine.notifications.NotificationPreferencesProps
import build.wallet.statemachine.notifications.NotificationPreferencesUiStateMachine
import build.wallet.statemachine.recovery.cloud.RotateAuthKeyUIOrigin
import build.wallet.statemachine.recovery.cloud.RotateAuthKeyUIStateMachine
import build.wallet.statemachine.recovery.cloud.RotateAuthKeyUIStateMachineProps
import build.wallet.statemachine.recovery.socrec.TrustedContactManagementProps
import build.wallet.statemachine.recovery.socrec.TrustedContactManagementUiStateMachine
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
import build.wallet.statemachine.settings.full.notifications.RecoveryChannelSettingsProps
import build.wallet.statemachine.settings.full.notifications.RecoveryChannelSettingsUiStateMachine
import build.wallet.statemachine.settings.helpcenter.HelpCenterUiProps
import build.wallet.statemachine.settings.helpcenter.HelpCenterUiStateMachine
import build.wallet.statemachine.status.StatusBannerModelMock
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf

class SettingsHomeUiStateMachineImplTests : FunSpec({

  val propsOnBackCalls = turbines.create<Unit>("props onBack calls")
  val firmwareDataService = FirmwareDataServiceFake()

  val props =
    SettingsHomeUiProps(
      accountData = ActiveKeyboxLoadedDataMock,
      homeBottomSheetModel = null,
      homeStatusBannerModel = null,
      onBack = { propsOnBackCalls.add(Unit) }
    )

  fun stateMachine(appVariant: AppVariant = AppVariant.Customer) =
    SettingsHomeUiStateMachineImpl(
      appVariant = appVariant,
      mobilePaySettingsUiStateMachine = object : MobilePaySettingsUiStateMachine,
        ScreenStateMachineMock<MobilePaySettingsUiProps>("mobile-txn") {},
      notificationPreferencesUiStateMachine = object : NotificationPreferencesUiStateMachine,
        ScreenStateMachineMock<NotificationPreferencesProps>("notifications-preferences") {},
      recoveryChannelSettingsUiStateMachine = object : RecoveryChannelSettingsUiStateMachine,
        ScreenStateMachineMock<RecoveryChannelSettingsProps>("recovery-channel-settings") {},
      currencyPreferenceUiStateMachine = object : CurrencyPreferenceUiStateMachine,
        ScreenStateMachineMock<CurrencyPreferenceProps>("currency-preference") {},
      customElectrumServerSettingUiStateMachine = object :
        CustomElectrumServerSettingUiStateMachine,
        ScreenStateMachineMock<CustomElectrumServerProps>("custom-electrum-server") {},
      deviceSettingsUiStateMachine = object : DeviceSettingsUiStateMachine,
        ScreenStateMachineMock<DeviceSettingsProps>("device-settings") {},
      feedbackUiStateMachine = object : FeedbackUiStateMachine,
        ScreenStateMachineMock<FeedbackUiProps>("feedback") {},
      helpCenterUiStateMachine = object : HelpCenterUiStateMachine,
        ScreenStateMachineMock<HelpCenterUiProps>("help-center") {},
      trustedContactManagementUiStateMachine = object : TrustedContactManagementUiStateMachine,
        ScreenStateMachineMock<TrustedContactManagementProps>("trusted-contacts") {},
      settingsListUiStateMachine = object : SettingsListUiStateMachine,
        BodyStateMachineMock<SettingsListUiProps>("settings-list") {},
      cloudBackupHealthDashboardUiStateMachine = object : CloudBackupHealthDashboardUiStateMachine,
        ScreenStateMachineMock<CloudBackupHealthDashboardProps>("cloud-backup-health") {},
      rotateAuthKeyUIStateMachine = object : RotateAuthKeyUIStateMachine,
        ScreenStateMachineMock<RotateAuthKeyUIStateMachineProps>("rotate-auth-key") {},
      debugMenuStateMachine = object : DebugMenuStateMachine,
        ScreenStateMachineMock<DebugMenuProps>("debug-menu") {},
      biometricSettingUiStateMachine = object : BiometricSettingUiStateMachine,
        ScreenStateMachineMock<BiometricSettingUiProps>("debug-menu") {},
      firmwareDataService = firmwareDataService
    )

  beforeTest {
    firmwareDataService.reset()
  }

  test("onBack calls props onBack") {
    stateMachine().test(props) {
      awaitScreenWithBodyModelMock<SettingsListUiProps> {
        onBack()
      }
      propsOnBackCalls.awaitItem()
    }
  }

  test("settings list") {
    stateMachine().test(props) {
      awaitScreenWithBodyModelMock<SettingsListUiProps> {
        supportedRows
          .map { it::class }.toSet()
          .shouldBe(
            setOf(
              SettingsListUiProps.SettingsListRow.BitkeyDevice::class,
              SettingsListUiProps.SettingsListRow.CustomElectrumServer::class,
              SettingsListUiProps.SettingsListRow.CurrencyPreference::class,
              SettingsListUiProps.SettingsListRow.HelpCenter::class,
              SettingsListUiProps.SettingsListRow.MobilePay::class,
              SettingsListUiProps.SettingsListRow.NotificationPreferences::class,
              SettingsListUiProps.SettingsListRow.RecoveryChannels::class,
              SettingsListUiProps.SettingsListRow.ContactUs::class,
              SettingsListUiProps.SettingsListRow.TrustedContacts::class,
              SettingsListUiProps.SettingsListRow.CloudBackupHealth::class,
              SettingsListUiProps.SettingsListRow.RotateAuthKey::class,
              SettingsListUiProps.SettingsListRow.Biometric::class
            )
          )
      }
    }
  }

  test("open and close mobile pay") {
    stateMachine().test(props) {
      awaitScreenWithBodyModelMock<SettingsListUiProps> {
        supportedRows.first { it is SettingsListUiProps.SettingsListRow.MobilePay }.onClick()
      }
      awaitScreenWithBodyModelMock<MobilePaySettingsUiProps> {
        onBack()
      }
      awaitScreenWithBodyModelMock<SettingsListUiProps>()
    }
  }

  test("open and close notifications") {
    stateMachine().test(props) {
      awaitScreenWithBodyModelMock<SettingsListUiProps> {
        supportedRows.first { it is SettingsListUiProps.SettingsListRow.NotificationPreferences }
          .onClick()
      }

      awaitScreenWithBodyModelMock<NotificationPreferencesProps> {
        onBack()
      }

      awaitScreenWithBodyModelMock<SettingsListUiProps>()
    }
  }

  test("open and close custom electrum server") {
    stateMachine().test(props) {
      awaitScreenWithBodyModelMock<SettingsListUiProps> {
        supportedRows.first { it is SettingsListUiProps.SettingsListRow.CustomElectrumServer }
          .onClick()
      }
      awaitScreenWithBodyModelMock<CustomElectrumServerProps> {
        onBack()
      }
      awaitScreenWithBodyModelMock<SettingsListUiProps>()
    }
  }

  test("open and close currency preference") {
    stateMachine().test(props) {
      awaitScreenWithBodyModelMock<SettingsListUiProps> {
        supportedRows.first { it is SettingsListUiProps.SettingsListRow.CurrencyPreference }
          .onClick()
      }
      awaitScreenWithBodyModelMock<CurrencyPreferenceProps> {
        onBack.shouldNotBeNull().invoke()
      }
      awaitScreenWithBodyModelMock<SettingsListUiProps>()
    }
  }

  test("open and close trusted contacts settings") {
    stateMachine().test(props) {
      awaitScreenWithBodyModelMock<SettingsListUiProps> {
        supportedRows.first { it is SettingsListUiProps.SettingsListRow.TrustedContacts }.onClick()
      }
      awaitScreenWithBodyModelMock<TrustedContactManagementProps> {
        onExit()
      }
      awaitScreenWithBodyModelMock<SettingsListUiProps>()
    }
  }

  test("open and close bitkey device settings") {
    stateMachine().test(props) {
      awaitScreenWithBodyModelMock<SettingsListUiProps> {
        supportedRows.first { it is SettingsListUiProps.SettingsListRow.BitkeyDevice }.onClick()
      }
      awaitScreenWithBodyModelMock<DeviceSettingsProps> {
        onBack()
      }
      awaitScreenWithBodyModelMock<SettingsListUiProps>()
    }
  }

  test("open and close help center") {
    stateMachine().test(props) {
      awaitScreenWithBodyModelMock<SettingsListUiProps> {
        supportedRows.first { it is SettingsListUiProps.SettingsListRow.HelpCenter }.onClick()
      }
      awaitScreenWithBodyModelMock<HelpCenterUiProps> {
        onBack()
      }
      awaitScreenWithBodyModelMock<SettingsListUiProps>()
    }
  }

  test("open and close mobile devices") {
    stateMachine().test(props) {
      awaitScreenWithBodyModelMock<SettingsListUiProps> {
        supportedRows.first { it is SettingsListUiProps.SettingsListRow.RotateAuthKey }.onClick()
      }
      awaitScreenWithBodyModelMock<RotateAuthKeyUIStateMachineProps> {
        origin.shouldBeTypeOf<RotateAuthKeyUIOrigin.Settings>().onBack()
      }
      awaitScreenWithBodyModelMock<SettingsListUiProps>()
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
      awaitScreenWithBodyModelMock<SettingsListUiProps> {
        firmwareDataService.firmwareData.value.shouldBe(firstUpdate)

        // Prepare a new update
        firmwareDataService.pendingUpdate = secondUpdate
        // Navigate somewhere else and back
        supportedRows.first { it is SettingsListUiProps.SettingsListRow.MobilePay }.onClick()
      }

      awaitScreenWithBodyModelMock<MobilePaySettingsUiProps> {
        onBack()
      }
      awaitScreenWithBodyModelMock<SettingsListUiProps>()
      firmwareDataService.firmwareData.value.shouldBe(secondUpdate)
    }
  }

  test("shows bottom sheet from props") {
    stateMachine().test(props.copy(homeBottomSheetModel = SheetModelMock {})) {
      awaitItem().bottomSheetModel.shouldNotBeNull()
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
        awaitScreenWithBodyModelMock<SettingsListUiProps> {
          supportedRows.single { it is DebugMenu }
        }
      }
    }

    test("enabled in AppVariant.Development") {
      stateMachine(AppVariant.Development).test(props) {
        awaitScreenWithBodyModelMock<SettingsListUiProps> {
          supportedRows.single { it is DebugMenu }
        }
      }
    }

    test("disabled in AppVariant.Customer") {
      stateMachine(AppVariant.Customer).test(props) {
        awaitScreenWithBodyModelMock<SettingsListUiProps> {
          supportedRows.none { it is DebugMenu }
        }
      }
    }

    test("disabled in AppVariant.Beta") {
      stateMachine(AppVariant.Beta).test(props) {
        awaitScreenWithBodyModelMock<SettingsListUiProps> {
          supportedRows.none { it is DebugMenu }
        }
      }
    }

    test("disabled in AppVariant.Emergency") {
      stateMachine(AppVariant.Emergency).test(props) {
        awaitScreenWithBodyModelMock<SettingsListUiProps> {
          supportedRows.none { it is DebugMenu }
        }
      }
    }
  }
})
