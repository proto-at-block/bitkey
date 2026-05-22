package build.wallet.statemachine.settings.full.device.wipedevice

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign
import bitkey.account.AccountConfigServiceFake
import bitkey.account.HardwareType
import build.wallet.device.wipe.DeviceWipeEligibilityServiceFake
import build.wallet.device.wipe.InactiveHardwareDevice
import build.wallet.device.wipe.InactiveDeviceWipeValidationError
import build.wallet.bitkey.keybox.FullAccountConfigMock
import build.wallet.bitkey.keybox.FullAccountW3Mock
import build.wallet.coroutines.turbine.turbines
import build.wallet.firmware.FirmwareDeviceInfoDaoMock
import build.wallet.firmware.FirmwareDeviceInfoMock
import build.wallet.firmware.HardwareUnlockInfoServiceFake
import build.wallet.firmware.UnlockInfo
import build.wallet.firmware.UnlockMethod
import build.wallet.nfc.NfcCommandsMock
import build.wallet.nfc.NfcException
import build.wallet.nfc.NfcSession
import build.wallet.nfc.NfcSessionFake
import build.wallet.nfc.platform.HardwareInteraction
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.nfc.NfcConfirmableSessionUIStateMachineProps
import build.wallet.statemachine.nfc.NfcConfirmableSessionUiStateMachineMock
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.settings.full.device.wipedevice.confirmation.WipingDeviceConfirmationProps
import build.wallet.statemachine.settings.full.device.wipedevice.confirmation.WipingDeviceConfirmationUiStateMachineImpl
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitBodyMock
import build.wallet.statemachine.ui.awaitSheet
import build.wallet.statemachine.ui.matchers.shouldHaveId
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.get
import io.kotest.core.spec.style.FunSpec
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.flow.first

private val confirmationMessages = listOf(
  "Wiping disconnects this device from your Bitkey wallet.",
  "This device will no longer access the funds in your wallet.",
  "This device will no longer help recover your wallet.",
  "After the wipe is complete, you can safely give away or dispose of this device."
)

class WipingDeviceConfirmationUiStateMachineImplTests : FunSpec({
  val firmwareDeviceInfoDao = FirmwareDeviceInfoDaoMock(turbines::create)
  val hardwareUnlockInfoService = HardwareUnlockInfoServiceFake()
  val accountConfigService = AccountConfigServiceFake()
  val deviceWipeEligibilityService = DeviceWipeEligibilityServiceFake()

  val stateMachine = WipingDeviceConfirmationUiStateMachineImpl(
    nfcConfirmableSessionUiStateMachine = NfcConfirmableSessionUiStateMachineMock("wiping device nfc"),
    firmwareDeviceInfoDao = firmwareDeviceInfoDao,
    hardwareUnlockInfoService = hardwareUnlockInfoService,
    accountConfigService = accountConfigService,
    deviceWipeEligibilityService = deviceWipeEligibilityService
  )

  val onBackCalls = turbines.create<Unit>("on back calls")
  val onConfirmWipeDeviceCalls = turbines.create<Unit>("on confirm wipe device calls")

  val props = WipingDeviceConfirmationProps(
    onBack = { onBackCalls += Unit },
    onWipeDevice = { onConfirmWipeDeviceCalls += Unit },
    isDevicePaired = true,
    fullAccount = null
  )

  fun nfcCommandsMock(id: String) = NfcCommandsMock { name ->
    Turbine(name = "$id $name")
  }

  beforeTest {
    firmwareDeviceInfoDao.reset()
    accountConfigService.reset()
    deviceWipeEligibilityService.reset()
    hardwareUnlockInfoService.replaceAllUnlockInfo(emptyList())
  }

  test("onBack calls") {
    stateMachine.test(props) {
      awaitBody<FormBodyModel> {
        val icon = toolbar.shouldNotBeNull()
          .leadingAccessory
          .shouldBeInstanceOf<ToolbarAccessoryModel.IconAccessory>()

        icon.model.onClick.shouldNotBeNull()
          .invoke()
      }

      onBackCalls.awaitItem().shouldBe(Unit)
    }
  }

  test("test content and unchecked checkboxes") {
    stateMachine.test(props) {
      awaitBody<FormBodyModel> {
        mainContentList[0].apply {
          shouldBeInstanceOf<FormMainContentModel.ListGroup>()
          header.shouldNotBeNull().apply {
            headline.shouldBe("Before you continue...")
            sublineModel.shouldNotBeNull().string.shouldBe("Please read and confirm the following:")
          }

          val listGroup = mainContentList[0].shouldBeInstanceOf<FormMainContentModel.ListGroup>()
          listGroup.listGroupModel.items.size.shouldBe(confirmationMessages.size)
          confirmationMessages.forEachIndexed { index, message ->
            listGroup.listGroupModel.items[index].leadingAccessory
              .shouldBeInstanceOf<ListItemAccessory.CheckboxAccessory>().apply {
                isChecked.shouldBe(false)
              }
            listGroup.listGroupModel.items[index].title.shouldBe(message)
          }
        }

        primaryButton.shouldNotBeNull().apply {
          shouldBeInstanceOf<ButtonModel>()
          text.shouldBe("Wipe device")
          isEnabled.shouldBe(false)
        }
      }
    }
  }

  test("CTA is disabled until all messages are checked") {
    stateMachine.test(props) {
      awaitBody<FormBodyModel> {
        primaryButton.shouldNotBeNull().isEnabled.shouldBe(false)
        checkBoxAtIndex(0)
      }

      awaitBody<FormBodyModel> {
        primaryButton.shouldNotBeNull().isEnabled.shouldBe(false)
        checkBoxAtIndex(1)
      }

      awaitBody<FormBodyModel> {
        primaryButton.shouldNotBeNull().isEnabled.shouldBe(false)
        checkBoxAtIndex(2)
      }

      awaitBody<FormBodyModel> {
        primaryButton.shouldNotBeNull().isEnabled.shouldBe(false)
        checkBoxAtIndex(3)
      }

      awaitBody<FormBodyModel> {
        mainContentList.size.shouldBe(1)
        primaryButton.shouldNotBeNull().isEnabled.shouldBe(true)
        mainContentList[0].apply {
          shouldBeInstanceOf<FormMainContentModel.ListGroup>()
          repeat(confirmationMessages.size) { index ->
            listGroupModel.items[index].leadingAccessory
              .shouldBeInstanceOf<ListItemAccessory.CheckboxAccessory>().apply {
                isChecked.shouldBe(true)
              }
          }
        }
      }
    }
  }

  test("full flow for wiping device") {
    stateMachine.test(props) {
      checkAllConfirmationMessages()

      awaitBody<FormBodyModel> {
        primaryButton.shouldNotBeNull().apply {
          onClick()
        }
      }

      awaitSheet<FormBodyModel> {
        primaryButton.shouldNotBeNull().apply {
          onClick()
        }
      }

      awaitBodyMock<NfcConfirmableSessionUIStateMachineProps<Boolean>> {
        onSuccess(true)
      }

      onConfirmWipeDeviceCalls.awaitItem()
      firmwareDeviceInfoDao.clearCalls.awaitItem()

      hardwareUnlockInfoService.countUnlockInfo(UnlockMethod.BIOMETRICS).first().shouldBe(0)
      deviceWipeEligibilityService.recordW3UpgradeOldW1WipedIfApplicableCalls
        .shouldBe(emptyList())
    }
  }

  test("show and dismiss ScanAndWipeConfirmationSheet") {
    stateMachine.test(props) {
      checkAllConfirmationMessages()

      awaitBody<FormBodyModel> {
        primaryButton.shouldNotBeNull().apply {
          // Simulate clicking the wipe button to show the sheet
          onClick.invoke()
        }
      }

      awaitItem().bottomSheetModel.shouldNotBeNull().apply {
        body.shouldHaveId(WipingDeviceEventTrackerScreenId.SCAN_AND_RESET_SHEET)
        body.shouldBeInstanceOf<FormBodyModel>().apply {
          secondaryButton.shouldNotBeNull().apply {
            // Simulate clicking the confirm button to dismiss the sheet
            onClick.invoke()
          }
        }
      }

      // Verify the bottom sheet is dismissed
      awaitItem().bottomSheetModel.shouldBeNull()
    }
  }

  test("W1 config skips two-tap confirmation (onRequiresConfirmation is non-null)") {
    accountConfigService.setActiveConfig(
      FullAccountConfigMock.copy(hardwareType = HardwareType.W1)
    )
    stateMachine.test(props) {
      checkAllConfirmationMessages()
      awaitBody<FormBodyModel> {
        primaryButton.shouldNotBeNull().onClick()
      }
      awaitSheet<FormBodyModel> {
        primaryButton.shouldNotBeNull().onClick()
      }
      awaitBodyMock<NfcConfirmableSessionUIStateMachineProps<Boolean>> {
        // W1 should have onRequiresConfirmation set (skip second tap)
        onRequiresConfirmation.shouldNotBeNull()
        config.showNativeSheetOnIos.shouldBe(true)
      }
    }
  }

  test("W3 config uses two-tap confirmation (onRequiresConfirmation is null)") {
    accountConfigService.setActiveConfig(
      FullAccountConfigMock.copy(hardwareType = HardwareType.W3)
    )
    stateMachine.test(props) {
      checkAllConfirmationMessages()
      awaitBody<FormBodyModel> {
        primaryButton.shouldNotBeNull().onClick()
      }
      awaitSheet<FormBodyModel> {
        primaryButton.shouldNotBeNull().onClick()
      }
      awaitBodyMock<NfcConfirmableSessionUIStateMachineProps<Boolean>> {
        // W3 should have onRequiresConfirmation null (use default two-tap)
        onRequiresConfirmation.shouldBeNull()
        config.showNativeSheetOnIos.shouldBe(false)
      }
    }
  }

  test("unknown hardware type defaults to two-tap confirmation") {
    accountConfigService.setActiveConfig(null)
    stateMachine.test(props) {
      checkAllConfirmationMessages()
      awaitBody<FormBodyModel> {
        primaryButton.shouldNotBeNull().onClick()
      }
      awaitSheet<FormBodyModel> {
        primaryButton.shouldNotBeNull().onClick()
      }
      awaitBodyMock<NfcConfirmableSessionUIStateMachineProps<Boolean>> {
        onRequiresConfirmation.shouldBeNull()
        config.showNativeSheetOnIos.shouldBe(false)
      }
    }
  }

  test("full account hardware type is used when provided by parent state machine") {
    accountConfigService.setActiveConfig(
      FullAccountConfigMock.copy(hardwareType = HardwareType.W1)
    )
    stateMachine.test(props.copy(fullAccount = FullAccountW3Mock)) {
      checkAllConfirmationMessages()
      awaitBody<FormBodyModel> {
        primaryButton.shouldNotBeNull().onClick()
      }
      awaitSheet<FormBodyModel> {
        primaryButton.shouldNotBeNull().onClick()
      }
      awaitBodyMock<NfcConfirmableSessionUIStateMachineProps<Boolean>> {
        onRequiresConfirmation.shouldBeNull()
        config.showNativeSheetOnIos.shouldBe(false)
      }
    }
  }

  test("old-W1 context shows confirmation and configures W1 NFC") {
    stateMachine.test(
      props.copy(
        fullAccount = FullAccountW3Mock,
        isDevicePaired = false,
        wipeContext = WipeContext.InactiveDevice(inactiveW1Device("e5ff120e"))
      )
    ) {
      awaitBody<FormBodyModel> {
        val listGroup = mainContentList[0].shouldBeInstanceOf<FormMainContentModel.ListGroup>()
        listGroup.listGroupModel.items.map { it.title }.shouldBe(confirmationMessages)
        listGroup.listGroupModel.items[0].leadingAccessory
          .shouldBeInstanceOf<ListItemAccessory.CheckboxAccessory>().apply {
            isChecked.shouldBe(false)
          }
        checkBoxAtIndex(0)
      }
      checkRemainingConfirmationMessages(startIndex = 1)
      awaitBody<FormBodyModel> {
        primaryButton.shouldNotBeNull().onClick()
      }
      awaitSheet<FormBodyModel> {
        primaryButton.shouldNotBeNull().onClick()
      }
      awaitBodyMock<NfcConfirmableSessionUIStateMachineProps<Boolean>> {
        needsAuthentication.shouldBe(true)
        hardwareVerification.shouldBe(NfcSessionUIStateMachineProps.HardwareVerification.NotRequired)
        config.hardwareTypeOverride.shouldBe(HardwareType.W1)
        config.skipFirmwareTelemetry.shouldBe(true)
        onRequiresConfirmation.shouldNotBeNull()
        config.showNativeSheetOnIos.shouldBe(true)
      }
    }
  }

  test("inactive W3 context configures W3 NFC behavior") {
    stateMachine.test(
      props.copy(
        fullAccount = FullAccountW3Mock,
        isDevicePaired = false,
        wipeContext = WipeContext.InactiveDevice(
          InactiveHardwareDevice(
            hardwareType = HardwareType.W3,
            hardwareFingerprint = "old-w3-fingerprint"
          )
        )
      )
    ) {
      confirmWipeDevice()

      awaitBodyMock<NfcConfirmableSessionUIStateMachineProps<Boolean>> {
        needsAuthentication.shouldBe(true)
        hardwareVerification.shouldBe(NfcSessionUIStateMachineProps.HardwareVerification.NotRequired)
        config.hardwareTypeOverride.shouldBe(HardwareType.W3)
        config.skipFirmwareTelemetry.shouldBe(true)
        onRequiresConfirmation.shouldBeNull()
        config.showNativeSheetOnIos.shouldBe(false)
      }
    }
  }

  test("old-W1 final wipe validation delegates to device wipe service before wipeDevice") {
    stateMachine.test(
      props.copy(
        fullAccount = FullAccountW3Mock,
        isDevicePaired = false,
        wipeContext = WipeContext.InactiveDevice(inactiveW1Device("e5ff120e"))
      )
    ) {
      confirmWipeDevice()

      awaitBodyMock<NfcConfirmableSessionUIStateMachineProps<Boolean>> {
        val commands = nfcCommandsMock("serial-mismatch").apply {
          deviceInfoResult = FirmwareDeviceInfoMock.copy(serial = "expected-serial")
        }

        session(NfcSessionFake(), commands)

        deviceWipeEligibilityService.validateInactiveDeviceForWipeCalls.single()
          .expectedDevice.shouldBe(
            inactiveW1Device("e5ff120e")
          )
        deviceWipeEligibilityService.validateInactiveDeviceForWipeCalls.single()
          .bitcoinNetworkType.shouldBe(FullAccountW3Mock.config.bitcoinNetworkType)
      }
    }
  }

  test("old-W1 final wipe validation failure prevents wipeDevice") {
    deviceWipeEligibilityService.validateInactiveDeviceForWipeResult =
      Err(InactiveDeviceWipeValidationError.WrongDevice)

    stateMachine.test(
      props.copy(
        fullAccount = FullAccountW3Mock,
        isDevicePaired = false,
        wipeContext = WipeContext.InactiveDevice(inactiveW1Device("different"))
      )
    ) {
      confirmWipeDevice()

      awaitBodyMock<NfcConfirmableSessionUIStateMachineProps<Boolean>> {
        val commands = object : NfcCommandsMock({ name ->
          Turbine(name = "validation-failure $name")
        }) {
          var wipeDeviceCalls = 0

          override suspend fun wipeDevice(session: NfcSession): HardwareInteraction<Boolean> {
            wipeDeviceCalls += 1
            return HardwareInteraction.Completed(true)
          }
        }

        shouldThrow<NfcException.CommandError> {
          session(NfcSessionFake(), commands)
        }
        commands.wipeDeviceCalls.shouldBe(0)
      }
    }
  }

  test("old-W1 device locked validation maps to unauthenticated NFC error") {
    deviceWipeEligibilityService.validateInactiveDeviceForWipeResult =
      Err(InactiveDeviceWipeValidationError.DeviceLocked)

    stateMachine.test(
      props.copy(
        fullAccount = FullAccountW3Mock,
        isDevicePaired = false,
        wipeContext = WipeContext.InactiveDevice(inactiveW1Device("e5ff120e"))
      )
    ) {
      confirmWipeDevice()

      awaitBodyMock<NfcConfirmableSessionUIStateMachineProps<Boolean>> {
        val error = shouldThrow<NfcException.CommandErrorUnauthenticated> {
          session(NfcSessionFake(), nfcCommandsMock("device-locked"))
        }

        onError(error).shouldBe(false)
      }

      deviceWipeEligibilityService.recordW3UpgradeOldW1WipedIfApplicableCalls
        .shouldBe(emptyList())
    }
  }

  test("old-W1 successful wipe records W3 old W1 handled") {
    stateMachine.test(
      props.copy(
        fullAccount = FullAccountW3Mock,
        isDevicePaired = false,
        wipeContext = WipeContext.InactiveDevice(inactiveW1Device("e5ff120e"))
      )
    ) {
      confirmWipeDevice()

      awaitBodyMock<NfcConfirmableSessionUIStateMachineProps<Boolean>> {
        onSuccess(true)
      }

      onConfirmWipeDeviceCalls.awaitItem()
      deviceWipeEligibilityService.recordW3UpgradeOldW1WipedIfApplicableCalls
        .single()
        .apply {
          account.shouldBe(FullAccountW3Mock)
          device.shouldBe(inactiveW1Device("e5ff120e"))
        }
    }
  }

  test("old-W1 record failure still completes wipe flow") {
    deviceWipeEligibilityService.recordW3UpgradeOldW1WipedIfApplicableResult =
      Err(Error("failed"))

    stateMachine.test(
      props.copy(
        fullAccount = FullAccountW3Mock,
        isDevicePaired = false,
        wipeContext = WipeContext.InactiveDevice(inactiveW1Device("e5ff120e"))
      )
    ) {
      confirmWipeDevice()

      awaitBodyMock<NfcConfirmableSessionUIStateMachineProps<Boolean>> {
        onSuccess(true)
      }

      onConfirmWipeDeviceCalls.awaitItem()
      deviceWipeEligibilityService.recordW3UpgradeOldW1WipedIfApplicableCalls
        .single()
        .device
        .shouldBe(inactiveW1Device("e5ff120e"))
    }
  }

  test("old-W1 wipe does not clear current paired W3 firmware or unlock metadata") {
    firmwareDeviceInfoDao.setDeviceInfo(
      FirmwareDeviceInfoMock.copy(serial = "current-w3", hwRevision = "w3a-core-evt")
    )
    hardwareUnlockInfoService.replaceAllUnlockInfo(UnlockInfo.ONBOARDING_DEFAULT)

    stateMachine.test(
      props.copy(
        fullAccount = FullAccountW3Mock,
        isDevicePaired = false,
        wipeContext = WipeContext.InactiveDevice(inactiveW1Device("e5ff120e"))
      )
    ) {
      confirmWipeDevice()

      awaitBodyMock<NfcConfirmableSessionUIStateMachineProps<Boolean>> {
        onSuccess(true)
      }

      onConfirmWipeDeviceCalls.awaitItem()
      firmwareDeviceInfoDao.getDeviceInfo().get().shouldNotBeNull()
      hardwareUnlockInfoService.countUnlockInfo(UnlockMethod.BIOMETRICS).first().shouldBe(1)
    }
  }
})

private suspend fun ReceiveTurbine<ScreenModel>.confirmWipeDevice() {
  checkAllConfirmationMessages()
  awaitBody<FormBodyModel> {
    primaryButton.shouldNotBeNull().onClick()
  }
  awaitSheet<FormBodyModel> {
    primaryButton.shouldNotBeNull().onClick()
  }
}

private suspend fun ReceiveTurbine<ScreenModel>.checkAllConfirmationMessages() {
  checkRemainingConfirmationMessages()
}

private suspend fun ReceiveTurbine<ScreenModel>.checkRemainingConfirmationMessages(startIndex: Int = 0) {
  for (index in startIndex until confirmationMessages.size) {
    awaitBody<FormBodyModel> {
      checkBoxAtIndex(index)
    }
  }
}

fun FormBodyModel.checkBoxAtIndex(index: Int) {
  mainContentList[0].apply {
    shouldBeInstanceOf<FormMainContentModel.ListGroup>()

    listGroupModel.items[index].leadingAccessory.shouldBeInstanceOf<ListItemAccessory.CheckboxAccessory>()
      .apply {
        onClick.invoke()
      }
  }
}

private fun inactiveW1Device(
  hardwareFingerprint: String,
) = InactiveHardwareDevice(
  hardwareType = HardwareType.W1,
  hardwareFingerprint = hardwareFingerprint
)
