package build.wallet.statemachine.settings.full.device

import app.cash.turbine.plusAssign
import build.wallet.availability.AppFunctionalityStatus
import build.wallet.availability.AppFunctionalityStatusProviderMock
import build.wallet.availability.F8eUnreachable
import build.wallet.coroutines.turbine.turbines
import build.wallet.db.DbError
import build.wallet.feature.FeatureFlagDaoMock
import build.wallet.feature.setFlagValue
import build.wallet.fingerprints.MultipleFingerprintsIsEnabledFeatureFlag
import build.wallet.firmware.FirmwareDeviceInfoDaoMock
import build.wallet.fwup.FwupDataMock
import build.wallet.nfc.NfcCommandsMock
import build.wallet.nfc.NfcSessionFake
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.core.awaitScreenWithBody
import build.wallet.statemachine.core.awaitScreenWithBodyModelMock
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel.Button
import build.wallet.statemachine.core.form.FormMainContentModel.DataList
import build.wallet.statemachine.core.form.FormMainContentModel.DataList.Data
import build.wallet.statemachine.core.form.FormMainContentModel.ListGroup
import build.wallet.statemachine.core.test
import build.wallet.statemachine.data.firmware.FirmwareData.FirmwareUpdateState.PendingUpdate
import build.wallet.statemachine.data.firmware.FirmwareData.FirmwareUpdateState.UpToDate
import build.wallet.statemachine.data.firmware.FirmwareDataPendingUpdateMock
import build.wallet.statemachine.data.firmware.FirmwareDataUpToDateMock
import build.wallet.statemachine.data.keybox.ActiveKeyboxLoadedDataMock
import build.wallet.statemachine.fwup.FwupNfcUiProps
import build.wallet.statemachine.fwup.FwupNfcUiStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.recovery.losthardware.LostHardwareRecoveryProps
import build.wallet.statemachine.recovery.losthardware.LostHardwareRecoveryUiStateMachine
import build.wallet.statemachine.settings.full.device.fingerprints.EntryPoint
import build.wallet.statemachine.settings.full.device.fingerprints.ManagingFingerprintsProps
import build.wallet.statemachine.settings.full.device.fingerprints.ManagingFingerprintsUiStateMachine
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
  val appFunctionalityStatusProvider = AppFunctionalityStatusProviderMock()
  val multipleFingerprintsEnabledFeatureFlag = MultipleFingerprintsIsEnabledFeatureFlag(
    featureFlagDao = FeatureFlagDaoMock()
  )
  val resetDeviceIsEnabledFeatureFlag = ResetDeviceIsEnabledFeatureFlag(
    featureFlagDao = FeatureFlagDaoMock()
  )
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
      appFunctionalityStatusProvider = appFunctionalityStatusProvider,
      multipleFingerprintsIsEnabledFeatureFlag = multipleFingerprintsEnabledFeatureFlag,
      resetDeviceIsEnabledFeatureFlag = resetDeviceIsEnabledFeatureFlag,
      managingFingerprintsUiStateMachine = object : ManagingFingerprintsUiStateMachine,
        ScreenStateMachineMock<ManagingFingerprintsProps>(
          id = "managing fingerprints"
        ) {}
    )

  val onBackCalls = turbines.create<Unit>("on back calls")

  val props = DeviceSettingsProps(
    accountData = ActiveKeyboxLoadedDataMock,
    firmwareData = FirmwareDataUpToDateMock.copy(firmwareUpdateState = UpToDate),
    onBack = { onBackCalls += Unit }
  )

  val nfcCommandsMock = NfcCommandsMock(turbines::create)

  beforeTest {
    firmwareDeviceInfoDao.reset()
  }

  test("metadata is appropriately formatted with update") {
    stateMachine.test(
      props.copy(
        firmwareData =
          FirmwareDataUpToDateMock.copy(
            firmwareUpdateState = PendingUpdate(FwupDataMock) {}
          )
      )
    ) {
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
        mainContentList[1].apply {
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
    stateMachine.test(
      props.copy(
        firmwareData =
          FirmwareDataPendingUpdateMock.copy(
            firmwareUpdateState =
              PendingUpdate(
                fwupData = FwupDataMock.copy(version = version),
                onUpdateComplete = {}
              )
          )
      )
    ) {
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
        mainContentList[1].apply {
          shouldBeInstanceOf<Button>()
          item.text.shouldBe("Replace device")
          item.isEnabled.shouldBeTrue()
        }
      }

      appFunctionalityStatusProvider.appFunctionalityStatusFlow.emit(
        AppFunctionalityStatus.LimitedFunctionality(
          cause = F8eUnreachable(Instant.DISTANT_PAST)
        )
      )

      awaitScreenWithBody<FormBodyModel> {
        mainContentList[1].apply {
          shouldBeInstanceOf<Button>()
          item.text.shouldBe("Replace device")
          item.isEnabled.shouldBeFalse()
        }
      }
    }
  }

  test("tap on manage fingerprints") {
    multipleFingerprintsEnabledFeatureFlag.setFlagValue(true)

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
    multipleFingerprintsEnabledFeatureFlag.setFlagValue(true)
    stateMachine.test(
      props.copy(firmwareData = FirmwareDataPendingUpdateMock)
    ) {
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
