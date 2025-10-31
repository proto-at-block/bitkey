package bitkey.ui.screens.device

import bitkey.privilegedactions.FingerprintResetAvailabilityServiceImpl
import bitkey.ui.framework.test
import build.wallet.availability.AppFunctionalityServiceFake
import build.wallet.availability.AppFunctionalityStatus
import build.wallet.availability.F8eUnreachable
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.db.DbError
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.FeatureFlagValue
import build.wallet.feature.flags.FingerprintResetFeatureFlag
import build.wallet.feature.flags.FingerprintResetMinFirmwareVersionFeatureFlag
import build.wallet.firmware.FirmwareDeviceInfoDaoMock
import build.wallet.firmware.FirmwareDeviceInfoMock
import build.wallet.fwup.*
import build.wallet.fwup.FirmwareData.FirmwareUpdateState.PendingUpdate
import build.wallet.nfc.NfcCommandsMock
import build.wallet.nfc.NfcSessionFake
import build.wallet.router.Route
import build.wallet.router.Router
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel.*
import build.wallet.statemachine.core.form.FormMainContentModel.DataList.Data
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryDataMock
import build.wallet.statemachine.fwup.FwupScreen
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.settings.full.device.fingerprints.ManagingFingerprintsScreen
import build.wallet.statemachine.settings.full.device.fingerprints.fingerprintreset.FingerprintResetProps
import build.wallet.statemachine.settings.full.device.fingerprints.fingerprintreset.FingerprintResetUiStateMachine
import build.wallet.statemachine.settings.full.device.wipedevice.WipingDeviceProps
import build.wallet.statemachine.settings.full.device.wipedevice.WipingDeviceUiStateMachine
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitBodyMock
import build.wallet.time.ClockFake
import build.wallet.time.DateTimeFormatterMock
import build.wallet.time.DurationFormatterFake
import build.wallet.time.TimeZoneProviderMock
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.get
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeTypeOf
import kotlinx.datetime.Instant

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

  val onBackCalls = turbines.create<Unit>("on back calls")

  val screen = DeviceSettingsScreen(
    account = FullAccountMock,
    lostHardwareRecoveryData = LostHardwareRecoveryDataMock,
    originScreen = null
  )

  val nfcCommandsMock = NfcCommandsMock(turbines::create)

  beforeTest {
    appFunctionalityService.reset()
    firmwareDeviceInfoDao.reset()
    firmwareDataService.reset()
    clock.reset()
  }

  test("metadata is appropriately formatted with update") {
    firmwareDataService.firmwareData.value =
      FirmwareDataUpToDateMock.copy(
        firmwareUpdateState = PendingUpdate(FwupDataMock)
      )

    presenter.test(screen) { navigator ->
      awaitBody<FormBodyModel> {
        mainContentList[0].apply {
          shouldBeInstanceOf<DataList>()

          hero.shouldNotBeNull().apply {
            title.shouldBe("Update available")
            subtitle.shouldBe("1.2.3")
            button.shouldNotBeNull().text.shouldBe("Update to fake")
          }

          items.verifyMetadataDataList()

          buttons.size.shouldBe(1)
        }
      }
    }
  }

  test("metadata is appropriately formatted with no update") {
    presenter.test(screen) { navigator ->
      awaitBody<FormBodyModel> {
        mainContentList[0].apply {
          shouldBeInstanceOf<DataList>()

          hero.shouldNotBeNull().apply {
            title.shouldBe("Up to date")
            subtitle.shouldBe("1.2.3")
            button.shouldBeNull()
          }

          items.verifyMetadataDataList()

          buttons.size.shouldBe(1)
        }
      }
    }
  }

  test("sync device info") {
    firmwareDeviceInfoDao.getDeviceInfo().get().shouldBeNull()
    presenter.test(screen) { navigator ->
      // Device settings
      awaitBody<FormBodyModel> {
        mainContentList[0].apply {
          shouldBeInstanceOf<DataList>()
          buttons.first().onClick()
        }
      }

      // Syncing info via NFC
      awaitBodyMock<NfcSessionUIStateMachineProps<Result<Unit, DbError>>> {
        session(NfcSessionFake(), nfcCommandsMock)
        onSuccess(Ok(Unit))
        firmwareDeviceInfoDao.getDeviceInfo().get().shouldNotBeNull()
      }

      // Back to device settings
      awaitBody<FormBodyModel>()
    }
  }

  test("lost or stolen device") {
    presenter.test(screen) { navigator ->
      awaitBody<FormBodyModel> {
        mainContentList[2].apply {
          shouldBeInstanceOf<Button>()
          item.text.shouldBe("Replace device")
          item.onClick()
        }
      }

      // Note: In the new pattern, lost hardware recovery would trigger a navigation event
      // For now, this is handled via Router.route rather than Navigator
      Router.route.shouldBe(Route.InitiateHardwareRecovery)
    }
  }

  test("onBack calls") {
    presenter.test(screen) { navigator ->
      awaitBody<FormBodyModel> {
        val icon =
          toolbar.shouldNotBeNull()
            .leadingAccessory
            .shouldBeInstanceOf<IconAccessory>()

        icon.model.onClick.shouldNotBeNull()
          .invoke()
      }

      navigator.exitCalls.awaitItem().shouldBe(Unit)
    }
  }

  test("fwup") {
    val version = "fake-version"
    firmwareDataService.firmwareData.value = FirmwareDataPendingUpdateMock.copy(
      firmwareUpdateState =
        PendingUpdate(
          fwupData = FwupDataMock.copy(version = version)
        )
    )
    presenter.test(screen) { navigator ->
      // Device settings
      awaitBody<FormBodyModel> {
        mainContentList[0].apply {
          shouldBeInstanceOf<DataList>()
          val updateButton = hero.shouldNotBeNull().button.shouldNotBeNull()
          updateButton.text.shouldBe("Update to $version")
          updateButton.onClick()
        }
      }

      // Going to firmware update screen
      val fwupScreen = navigator.goToCalls.awaitItem().shouldBeTypeOf<FwupScreen>()
      fwupScreen.onExit()

      // Back to device settings
      val deviceSettingsScreen = navigator.goToCalls.awaitItem().shouldBeTypeOf<DeviceSettingsScreen>()
      deviceSettingsScreen.account.shouldBe(screen.account)
    }
  }

  test("Replace device button should be disabled given limited functionality") {
    presenter.test(screen) { navigator ->
      awaitBody<FormBodyModel> {
        mainContentList[2].apply {
          shouldBeInstanceOf<Button>()
          item.text.shouldBe("Replace device")
          item.isEnabled.shouldBeTrue()
        }
      }

      appFunctionalityService.status.emit(
        AppFunctionalityStatus.LimitedFunctionality(
          cause = F8eUnreachable(Instant.DISTANT_PAST)
        )
      )

      awaitBody<FormBodyModel> {
        mainContentList[2].apply {
          shouldBeInstanceOf<Button>()
          item.text.shouldBe("Replace device")
          item.isEnabled.shouldBeFalse()
        }
      }
    }
  }

  test("tap on manage fingerprints") {
    presenter.test(screen) { navigator ->
      // Tap the Fingerprint button
      awaitBody<FormBodyModel> {
        mainContentList[1].apply {
          shouldBeInstanceOf<ListGroup>()
          listGroupModel.items[0].onClick!!()
        }
      }

      // Expect the options sheet
      awaitItem().bottomSheetModel.shouldNotBeNull()
        .body.shouldBeInstanceOf<FormBodyModel>().apply {
          primaryButton.shouldNotBeNull().onClick()
        }

      // Going to manage fingerprints
      navigator.goToCalls.awaitItem().shouldBeTypeOf<ManagingFingerprintsScreen>()
    }
  }

  test("tap on manage fingerprints but need fwup") {
    firmwareDataService.firmwareData.value = FirmwareDataPendingUpdateMock

    presenter.test(screen) { navigator ->
      // Tap the Fingerprint button
      awaitBody<FormBodyModel> {
        mainContentList[1].apply {
          shouldBeInstanceOf<ListGroup>()
          listGroupModel.items[0].onClick!!()
        }
      }

      // Expect the options sheet
      awaitItem().bottomSheetModel.shouldNotBeNull()
        .body.shouldBeInstanceOf<FormBodyModel>().apply {
          primaryButton.shouldNotBeNull().onClick()
        }

      // Going to manage fingerprints
      val managingScreen = navigator.goToCalls.awaitItem().shouldBeTypeOf<ManagingFingerprintsScreen>()
      managingScreen.onFwUpRequired()

      // Device settings screen should be showing with a bottom sheet modal
      with(awaitItem()) {
        bottomSheetModel.shouldNotBeNull()
          .body.shouldBeInstanceOf<FormBodyModel>().apply {
            header.shouldNotBeNull()
              .headline.shouldBe("Update your hardware device")

            secondaryButton.shouldNotBeNull().apply {
              text.shouldBe("Update hardware")
              onClick.invoke()
            }
          }
      }

      // Going to firmware update screen
      val fwupScreen = navigator.goToCalls.awaitItem().shouldBeTypeOf<FwupScreen>()
      fwupScreen.onExit()

      // Back to device settings
      val deviceSettingsScreen =
        navigator.goToCalls.awaitItem().shouldBeTypeOf<DeviceSettingsScreen>()
      deviceSettingsScreen.account.shouldBe(screen.account)
    }
  }

  test("tap on reset device") {
    presenter.test(screen) { navigator ->
      // Tap the Reset Device button
      awaitBody<FormBodyModel> {
        mainContentList[1].apply {
          shouldBeInstanceOf<ListGroup>()
          listGroupModel.items[1].onClick!!()
        }
      }

      // Going to manage reset device
      awaitBodyMock<WipingDeviceProps> {
        onBack()
      }

      // Back on the device settings screen
      awaitBody<FormBodyModel>()
    }
  }

  test("fingerprint reset option shows correctly when version requirements met") {
    val featureFlagDao = FeatureFlagDaoFake()
    val fingerprintResetFeatureFlag = FingerprintResetFeatureFlag(featureFlagDao)
    val fingerprintResetMinFirmwareVersionFeatureFlag =
      FingerprintResetMinFirmwareVersionFeatureFlag(featureFlagDao)

    val fingerprintResetAvailability = FingerprintResetAvailabilityServiceImpl(
      fingerprintResetFeatureFlag = fingerprintResetFeatureFlag,
      fingerprintResetMinFirmwareVersionFeatureFlag = fingerprintResetMinFirmwareVersionFeatureFlag,
      firmwareDataService = firmwareDataService
    )

    val presenterWithAvailability = DeviceSettingsScreenPresenter(
      nfcSessionUIStateMachine =
        object : NfcSessionUIStateMachine, ScreenStateMachineMock<NfcSessionUIStateMachineProps<*>>(
          "nfc-session"
        ) {},
      firmwareDeviceInfoDao = firmwareDeviceInfoDao,
      dateTimeFormatter = DateTimeFormatterMock(),
      timeZoneProvider = TimeZoneProviderMock(),
      durationFormatter = DurationFormatterFake(),
      appFunctionalityService = appFunctionalityService,
      wipingDeviceUiStateMachine =
        object : WipingDeviceUiStateMachine, ScreenStateMachineMock<WipingDeviceProps>(
          "wiping-device"
        ) {},
      firmwareDataService = firmwareDataService,
      fingerprintResetUiStateMachine =
        object : FingerprintResetUiStateMachine, ScreenStateMachineMock<FingerprintResetProps>(
          "fingerprint-reset"
        ) {},
      fingerprintResetAvailabilityService = fingerprintResetAvailability,
      clock = clock
    )

    // Enable feature flag and set supported firmware version
    fingerprintResetFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
    fingerprintResetMinFirmwareVersionFeatureFlag.setFlagValue(FeatureFlagValue.StringFlag("1.0.98"))
    firmwareDataService.firmwareData.value = FirmwareData(
      firmwareDeviceInfo = FirmwareDeviceInfoMock.copy(version = "1.0.98"),
      firmwareUpdateState = FirmwareData.FirmwareUpdateState.UpToDate
    )

    presenterWithAvailability.test(screen) { navigator ->
      // Tap the Fingerprint button
      awaitBody<FormBodyModel> {
        mainContentList[1].apply {
          shouldBeInstanceOf<ListGroup>()
          listGroupModel.items[0].onClick!!()
        }
      }

      // Expect the options sheet with fingerprint reset enabled
      awaitItem().bottomSheetModel.shouldNotBeNull()
        .body.shouldBeInstanceOf<FormBodyModel>().apply {
          header.shouldNotBeNull()
            .headline.shouldBe("Manage fingerprints")

          // Primary button should be "Edit fingerprints"
          primaryButton.shouldNotBeNull().apply {
            text.shouldBe("Edit fingerprints")
          }

          // Secondary button should be available for fingerprint reset
          secondaryButton.shouldNotBeNull().apply {
            text.shouldBe("I can't unlock my Bitkey")
          }
        }
    }
  }

  test("fingerprint reset option disabled when version requirements not met") {
    val featureFlagDao = FeatureFlagDaoFake()
    val fingerprintResetFeatureFlag = FingerprintResetFeatureFlag(featureFlagDao)
    val fingerprintResetMinFirmwareVersionFeatureFlag =
      FingerprintResetMinFirmwareVersionFeatureFlag(featureFlagDao)

    val fingerprintResetAvailability = FingerprintResetAvailabilityServiceImpl(
      fingerprintResetFeatureFlag = fingerprintResetFeatureFlag,
      fingerprintResetMinFirmwareVersionFeatureFlag = fingerprintResetMinFirmwareVersionFeatureFlag,
      firmwareDataService = firmwareDataService
    )

    val presenterWithAvailability = DeviceSettingsScreenPresenter(
      nfcSessionUIStateMachine =
        object : NfcSessionUIStateMachine, ScreenStateMachineMock<NfcSessionUIStateMachineProps<*>>(
          "nfc-session"
        ) {},
      firmwareDeviceInfoDao = firmwareDeviceInfoDao,
      dateTimeFormatter = DateTimeFormatterMock(),
      timeZoneProvider = TimeZoneProviderMock(),
      durationFormatter = DurationFormatterFake(),
      appFunctionalityService = appFunctionalityService,
      wipingDeviceUiStateMachine =
        object : WipingDeviceUiStateMachine, ScreenStateMachineMock<WipingDeviceProps>(
          "wiping-device"
        ) {},
      firmwareDataService = firmwareDataService,
      fingerprintResetUiStateMachine =
        object : FingerprintResetUiStateMachine, ScreenStateMachineMock<FingerprintResetProps>(
          "fingerprint-reset"
        ) {},
      fingerprintResetAvailabilityService = fingerprintResetAvailability,
      clock = clock
    )

    // Enable feature flag but set unsupported firmware version
    fingerprintResetFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
    fingerprintResetMinFirmwareVersionFeatureFlag.setFlagValue(FeatureFlagValue.StringFlag("1.0.98"))
    firmwareDataService.firmwareData.value = FirmwareData(
      firmwareDeviceInfo = FirmwareDeviceInfoMock.copy(version = "1.0.95"),
      firmwareUpdateState = FirmwareData.FirmwareUpdateState.UpToDate
    )

    presenterWithAvailability.test(screen) { navigator ->
      // Tap the Fingerprint button
      awaitBody<FormBodyModel> {
        mainContentList[1].apply {
          shouldBeInstanceOf<ListGroup>()
          listGroupModel.items[0].onClick!!()
        }
      }

      // Expect the options sheet with fingerprint reset disabled
      awaitItem().bottomSheetModel.shouldNotBeNull()
        .body.shouldBeInstanceOf<FormBodyModel>().apply {
          header.shouldNotBeNull()
            .headline.shouldBe("Manage fingerprints")

          // Primary button should be "Edit fingerprints"
          primaryButton.shouldNotBeNull().apply {
            text.shouldBe("Edit fingerprints")
          }

          // Secondary button should be null (disabled) when version requirements not met
          secondaryButton.shouldBeNull()
        }
    }
  }
})

private fun List<Data>.verifyMetadataDataList() {
  forEachIndexed { index, data ->
    when (index) {
      0 -> data.verifyMetadataData("Model name", "Bitkey")
      1 -> data.verifyMetadataData("Model number", "evtd")
      2 -> data.verifyMetadataData("Serial number", "serial")
      3 -> data.verifyMetadataData("Firmware version", "1.2.3")
      4 -> data.verifyMetadataData(
        "Last known charge",
        "100%"
      ) // Not 89% due to battery level masking
      5 -> data.verifyMetadataData(
        "Last sync",
        "date-time"
      )
    }
  }
}

private fun Data.verifyMetadataData(
  title: String,
  sideText: String,
) {
  this.title.shouldBe(title)
  this.sideText.shouldBe(sideText)
}
