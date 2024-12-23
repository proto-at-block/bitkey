package build.wallet.statemachine.settings.full.device

import app.cash.turbine.plusAssign
import build.wallet.availability.AppFunctionalityServiceFake
import build.wallet.availability.AppFunctionalityStatus
import build.wallet.availability.F8eUnreachable
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.coachmark.CoachmarkServiceMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.db.DbError
import build.wallet.firmware.FirmwareDeviceInfoDaoMock
import build.wallet.fwup.FirmwareData.FirmwareUpdateState.PendingUpdate
import build.wallet.fwup.FirmwareDataPendingUpdateMock
import build.wallet.fwup.FirmwareDataServiceFake
import build.wallet.fwup.FirmwareDataUpToDateMock
import build.wallet.fwup.FwupDataMock
import build.wallet.nfc.NfcCommandsMock
import build.wallet.nfc.NfcSessionFake
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.core.awaitScreenWithBody
import build.wallet.statemachine.core.awaitScreenWithBodyModelMock
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel.*
import build.wallet.statemachine.core.form.FormMainContentModel.DataList.Data
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryDataMock
import build.wallet.statemachine.fwup.FwupNfcUiProps
import build.wallet.statemachine.fwup.FwupNfcUiStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.recovery.losthardware.LostHardwareRecoveryProps
import build.wallet.statemachine.recovery.losthardware.LostHardwareRecoveryUiStateMachine
import build.wallet.statemachine.settings.full.device.fingerprints.EntryPoint
import build.wallet.statemachine.settings.full.device.fingerprints.ManagingFingerprintsProps
import build.wallet.statemachine.settings.full.device.fingerprints.ManagingFingerprintsUiStateMachine
import build.wallet.statemachine.settings.full.device.wipedevice.WipingDeviceProps
import build.wallet.statemachine.settings.full.device.wipedevice.WipingDeviceUiStateMachine
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
import kotlinx.datetime.Instant

class DeviceSettingsUiStateMachineImplTests : FunSpec({

  val firmwareDeviceInfoDao = FirmwareDeviceInfoDaoMock(turbines::create)
  val appFunctionalityService = AppFunctionalityServiceFake()
  val firmwareDataService = FirmwareDataServiceFake()
  val clock = ClockFake()
  val stateMachine =
    DeviceSettingsUiStateMachineImpl(
      lostHardwareRecoveryUiStateMachine =
        object : LostHardwareRecoveryUiStateMachine,
          ScreenStateMachineMock<LostHardwareRecoveryProps>(
            "initiate hw recovery"
          ) {},
      nfcSessionUIStateMachine =
        object : NfcSessionUIStateMachine, ScreenStateMachineMock<NfcSessionUIStateMachineProps<*>>(
          "firmware metadata"
        ) {},
      fwupNfcUiStateMachine =
        object : FwupNfcUiStateMachine, ScreenStateMachineMock<FwupNfcUiProps>(
          "fwup-nfc"
        ) {},
      dateTimeFormatter = DateTimeFormatterMock(),
      timeZoneProvider = TimeZoneProviderMock(),
      durationFormatter = DurationFormatterFake(),
      firmwareDeviceInfoDao = firmwareDeviceInfoDao,
      appFunctionalityService = appFunctionalityService,
      managingFingerprintsUiStateMachine = object : ManagingFingerprintsUiStateMachine,
        ScreenStateMachineMock<ManagingFingerprintsProps>(
          id = "managing fingerprints"
        ) {},
      wipingDeviceUiStateMachine =
        object : WipingDeviceUiStateMachine, ScreenStateMachineMock<WipingDeviceProps>(
          "wiping device"
        ) {},
      coachmarkService = CoachmarkServiceMock(turbineFactory = turbines::create),
      firmwareDataService = firmwareDataService,
      clock = clock
    )

  val onBackCalls = turbines.create<Unit>("on back calls")

  val props = DeviceSettingsProps(
    account = FullAccountMock,
    lostHardwareRecoveryData = LostHardwareRecoveryDataMock,
    onBack = { onBackCalls += Unit },
    onUnwindToMoneyHome = {}
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

    stateMachine.test(props) {
      awaitScreenWithBody<FormBodyModel> {
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
    stateMachine.test(props) {
      awaitScreenWithBody<FormBodyModel> {
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
    stateMachine.test(props) {
      // Device settings
      awaitScreenWithBody<FormBodyModel> {
        mainContentList[0].apply {
          shouldBeInstanceOf<DataList>()
          buttons.first().onClick()
        }
      }

      // Syncing info via NFC
      awaitScreenWithBodyModelMock<NfcSessionUIStateMachineProps<Result<Unit, DbError>>> {
        session(NfcSessionFake(), nfcCommandsMock)
        onSuccess(Ok(Unit))
        firmwareDeviceInfoDao.getDeviceInfo().get().shouldNotBeNull()
      }

      // Back to device settings
      awaitScreenWithBody<FormBodyModel>()
    }
  }

  test("lost or stolen device") {
    stateMachine.test(props) {
      awaitScreenWithBody<FormBodyModel> {
        mainContentList[2].apply {
          shouldBeInstanceOf<Button>()
          item.text.shouldBe("Replace device")
          item.onClick()
        }
      }

      awaitScreenWithBodyModelMock<LostHardwareRecoveryProps>()
    }
  }

  test("onBack calls") {
    stateMachine.test(props) {
      awaitScreenWithBody<FormBodyModel> {
        val icon =
          toolbar.shouldNotBeNull()
            .leadingAccessory
            .shouldBeInstanceOf<IconAccessory>()

        icon.model.onClick.shouldNotBeNull()
          .invoke()
      }

      onBackCalls.awaitItem().shouldBe(Unit)
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
    stateMachine.test(props) {
      // Device settings
      awaitScreenWithBody<FormBodyModel> {
        mainContentList[0].apply {
          shouldBeInstanceOf<DataList>()
          val updateButton = hero.shouldNotBeNull().button.shouldNotBeNull()
          updateButton.text.shouldBe("Update to $version")
          updateButton.onClick()
        }
      }

      // FWUP-ing
      awaitScreenWithBodyModelMock<FwupNfcUiProps> {
        onDone()
      }

      // Back to device settings
      awaitScreenWithBody<FormBodyModel>()
    }
  }

  test("Replace device button should be disabled given limited functionality") {
    stateMachine.test(props) {
      awaitScreenWithBody<FormBodyModel> {
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

      awaitScreenWithBody<FormBodyModel> {
        mainContentList[2].apply {
          shouldBeInstanceOf<Button>()
          item.text.shouldBe("Replace device")
          item.isEnabled.shouldBeFalse()
        }
      }
    }
  }

  test("tap on manage fingerprints") {
    stateMachine.test(props) {
      // Tap the Fingerprint button
      awaitScreenWithBody<FormBodyModel> {
        mainContentList[1].apply {
          shouldBeInstanceOf<ListGroup>()
          listGroupModel.items[0].onClick!!()
        }
      }

      // Going to manage fingerprints
      awaitScreenWithBodyModelMock<ManagingFingerprintsProps> {
        onBack()
      }

      // Back on the device settings screen
      awaitScreenWithBody<FormBodyModel>()
    }
  }

  test("tap on manage fingerprints but need fwup") {
    firmwareDataService.firmwareData.value = FirmwareDataPendingUpdateMock

    stateMachine.test(props) {
      // Tap the Fingerprint button
      awaitScreenWithBody<FormBodyModel> {
        mainContentList[1].apply {
          shouldBeInstanceOf<ListGroup>()
          listGroupModel.items[0].onClick!!()
        }
      }

      awaitScreenWithBodyModelMock<ManagingFingerprintsProps> {
        entryPoint.shouldBe(EntryPoint.DEVICE_SETTINGS)
        onFwUpRequired()
      }

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

      // FWUP-ing
      awaitScreenWithBodyModelMock<FwupNfcUiProps> {
        onDone()
      }

      // Back to device settings
      awaitScreenWithBody<FormBodyModel>()
    }
  }

  test("tap on reset device") {
    stateMachine.test(props) {
      // Tap the Reset Device button
      awaitScreenWithBody<FormBodyModel> {
        mainContentList[1].apply {
          shouldBeInstanceOf<ListGroup>()
          listGroupModel.items[1].onClick!!()
        }
      }

      // Going to manage reset device
      awaitScreenWithBodyModelMock<WipingDeviceProps> {
        onBack()
      }

      // Back on the device settings screen
      awaitScreenWithBody<FormBodyModel>()
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
      5 -> data.verifyMetadataData("Last sync", "date-time")
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
