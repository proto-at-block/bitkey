package bitkey.ui.screens.device

import bitkey.privilegedactions.FingerprintResetAvailabilityServiceImpl
import bitkey.ui.framework.test
import build.wallet.availability.AppFunctionalityServiceFake
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.flags.FingerprintResetFeatureFlag
import build.wallet.feature.flags.FingerprintResetMinFirmwareVersionFeatureFlag
import build.wallet.firmware.FirmwareDeviceInfoDaoMock
import build.wallet.fwup.FirmwareDataPendingUpdateMock
import build.wallet.fwup.FirmwareDataServiceFake
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel.DataList
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryDataMock
import build.wallet.statemachine.fwup.FwupScreen
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.settings.full.device.fingerprints.fingerprintreset.FingerprintResetProps
import build.wallet.statemachine.settings.full.device.fingerprints.fingerprintreset.FingerprintResetUiStateMachine
import build.wallet.statemachine.settings.full.device.wipedevice.WipingDeviceProps
import build.wallet.statemachine.settings.full.device.wipedevice.WipingDeviceUiStateMachine
import build.wallet.statemachine.ui.awaitBody
import build.wallet.time.ClockFake
import build.wallet.time.DateTimeFormatterMock
import build.wallet.time.DurationFormatterFake
import build.wallet.time.TimeZoneProviderMock
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeTypeOf

class DeviceSettingsScreenPresenterTests : FunSpec({

  val firmwareDeviceInfoDao = FirmwareDeviceInfoDaoMock(turbines::create)
  val appFunctionalityService = AppFunctionalityServiceFake()
  val firmwareDataService = FirmwareDataServiceFake()
  val clock = ClockFake()

  val featureFlagDao = FeatureFlagDaoFake()
  val fingerprintResetFeatureFlag = FingerprintResetFeatureFlag(featureFlagDao)
  val fingerprintResetMinFirmwareVersionFeatureFlag = FingerprintResetMinFirmwareVersionFeatureFlag(featureFlagDao)

  val fingerprintResetAvailability = FingerprintResetAvailabilityServiceImpl(
    fingerprintResetFeatureFlag = fingerprintResetFeatureFlag,
    fingerprintResetMinFirmwareVersionFeatureFlag = fingerprintResetMinFirmwareVersionFeatureFlag,
    firmwareDataService = firmwareDataService
  )

  val presenter = DeviceSettingsScreenPresenter(
    nfcSessionUIStateMachine = object : NfcSessionUIStateMachine,
      ScreenStateMachineMock<NfcSessionUIStateMachineProps<*>>("nfc-session") {},
    firmwareDeviceInfoDao = firmwareDeviceInfoDao,
    dateTimeFormatter = DateTimeFormatterMock(),
    timeZoneProvider = TimeZoneProviderMock(),
    durationFormatter = DurationFormatterFake(),
    appFunctionalityService = appFunctionalityService,
    wipingDeviceUiStateMachine = object : WipingDeviceUiStateMachine,
      ScreenStateMachineMock<WipingDeviceProps>("wiping-device") {},
    firmwareDataService = firmwareDataService,
    fingerprintResetUiStateMachine = object : FingerprintResetUiStateMachine,
      ScreenStateMachineMock<FingerprintResetProps>("fingerprint-reset") {},
    fingerprintResetAvailabilityService = fingerprintResetAvailability,
    clock = clock
  )

  val deviceSettingsScreen = DeviceSettingsScreen(
    account = FullAccountMock,
    lostHardwareRecoveryData = LostHardwareRecoveryDataMock,
    originScreen = null
  )

  beforeEach {
    appFunctionalityService.reset()
    firmwareDeviceInfoDao.reset()
    firmwareDataService.reset()
    clock.reset()
    firmwareDataService.firmwareData.value = FirmwareDataPendingUpdateMock
  }

  test("firmware update onExit should navigate back to device settings") {
    presenter.test(deviceSettingsScreen) { navigator ->
      awaitBody<FormBodyModel> {
        // Click on the firmware update button
        val updateButton = mainContentList[0]
          .shouldBeInstanceOf<DataList>()
          .hero
          .shouldNotBeNull()
          .button
          .shouldNotBeNull()

        updateButton.onClick()
      }

      // Should navigate to FwupScreen
      val fwupScreen = navigator.goToCalls.awaitItem().shouldBeTypeOf<FwupScreen>()

      // Close the screen
      fwupScreen.onExit()

      // Should navigate back to the DeviceSettingsScreen
      val backToDeviceScreen = navigator.goToCalls.awaitItem().shouldBeTypeOf<DeviceSettingsScreen>()
      backToDeviceScreen.account.shouldBe(deviceSettingsScreen.account)
      backToDeviceScreen.lostHardwareRecoveryData.shouldBe(deviceSettingsScreen.lostHardwareRecoveryData)
    }
  }
})
