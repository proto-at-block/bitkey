package build.wallet.statemachine.fwup

import build.wallet.coroutines.turbine.turbines
import build.wallet.fwup.FirmwareData.FirmwareUpdateState.PendingUpdate
import build.wallet.fwup.FwupDataMock
import build.wallet.nfc.NfcException
import build.wallet.platform.device.DeviceInfoProviderMock
import build.wallet.platform.device.DevicePlatform
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.core.*
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.nfc.FwupInstructionsBodyModel
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class FwupNfcUiStateMachineImplTests : FunSpec({

  val deviceInfoProvider = DeviceInfoProviderMock()
  val fwupNfcSessionUiStateMachine =
    object : FwupNfcSessionUiStateMachine,
      ScreenStateMachineMock<FwupNfcSessionUiProps>(
        id = "fwup-nfc-session"
      ) {}

  val stateMachine =
    FwupNfcUiStateMachineImpl(
      deviceInfoProvider = deviceInfoProvider,
      fwupNfcSessionUiStateMachine = fwupNfcSessionUiStateMachine
    )

  val onDoneCalls = turbines.create<Unit>("onDone calls")
  val props =
    FwupNfcUiProps(
      firmwareData = PendingUpdate(FwupDataMock),
      isHardwareFake = true,
      onDone = { onDoneCalls.add(Unit) }
    )

  beforeTest {
    deviceInfoProvider.reset()
  }

  test("back button on demo instructions calls props.onDone") {
    stateMachine.test(props) {
      awaitScreenWithBody<FwupInstructionsBodyModel> {
        onBack()
      }

      onDoneCalls.awaitItem()
    }
  }

  test("happy path") {
    stateMachine.test(props) {
      awaitScreenWithBody<FwupInstructionsBodyModel> {
        buttonModel.onClick()
      }

      awaitScreenWithBodyModelMock<FwupNfcSessionUiProps> {
        onDone()
      }

      onDoneCalls.awaitItem()
    }
  }

  test("fwup nfc session onBack") {
    stateMachine.test(props) {
      awaitScreenWithBody<FwupInstructionsBodyModel> {
        buttonModel.onClick()
      }

      awaitScreenWithBodyModelMock<FwupNfcSessionUiProps> {
        onBack()
      }

      // Back to update instructions
      awaitItem()
        .bottomSheetModel.shouldBeNull()
    }
  }

  // Helper to test error bottom sheet content
  suspend fun StateMachineTester<FwupNfcUiProps, ScreenModel>.testBottomSheetContent(
    error: NfcException = NfcException.Timeout(),
    expectedTitle: String,
    expectedSubline: String,
    withButtonGoesToRetry: Boolean = false,
    withUpdateInProgress: Boolean,
    withTransactionType: FwupTransactionType,
  ) {
    awaitScreenWithBody<FwupInstructionsBodyModel> {
      buttonModel.onClick()
    }

    awaitScreenWithBodyModelMock<FwupNfcSessionUiProps> {
      onError(error, withUpdateInProgress, withTransactionType)
    }

    // Back to update instructions, showing error bottom sheet
    with(
      awaitItem().bottomSheetModel.shouldNotBeNull().body.shouldBeInstanceOf<FormBodyModel>()
    ) {
      with(header.shouldNotBeNull()) {
        headline.shouldBe(expectedTitle)
        sublineModel.shouldNotBeNull().string.shouldBe(expectedSubline)
      }

      with(primaryButton.shouldNotBeNull()) {
        text.shouldBe(
          when (withButtonGoesToRetry) {
            true -> "Continue"
            false -> "Got it"
          }
        )
        onClick()
      }
    }

    when (withButtonGoesToRetry) {
      true -> {
        // Error bottom sheet closed
        awaitItem()
          .bottomSheetModel.shouldBeNull()

        // idk why but an extra item is emitted here
        awaitItem()
          .bottomSheetModel.shouldBeNull()

        // Launch NFC again
        awaitScreenWithBodyModelMock<FwupNfcSessionUiProps>()
      }

      false -> {
        // Error bottom sheet closed
        awaitItem()
          .bottomSheetModel.shouldBeNull()
      }
    }
  }

  test("failure - unauthenticated") {
    stateMachine.test(props) {
      testBottomSheetContent(
        error = NfcException.CommandErrorUnauthenticated(),
        expectedTitle = "Device Locked",
        expectedSubline = "Unlock your device with an enrolled fingerprint and try again.",
        withUpdateInProgress = false,
        withTransactionType = FwupTransactionType.StartFromBeginning
      )
    }
  }

  test("failure - no update in progress") {
    stateMachine.test(props) {
      testBottomSheetContent(
        expectedTitle = "Unable to update device",
        expectedSubline = "Make sure you hold your device to the back of your phone during the entire update.",
        withUpdateInProgress = false,
        withTransactionType = FwupTransactionType.StartFromBeginning
      )
    }
  }

  test("failure - no update in progress - iPhone 14 model") {
    deviceInfoProvider.devicePlatformValue = DevicePlatform.IOS
    deviceInfoProvider.deviceModelValue = "iPhone15,2"
    stateMachine.test(props) {
      testBottomSheetContent(
        expectedTitle = "Unable to update device",
        expectedSubline =
          "Make sure you hold your device to the back of your phone during the entire update." +
            "\n\nIf problems persist, turn on Airplane Mode to minimize interruptions.",
        withUpdateInProgress = false,
        withTransactionType = FwupTransactionType.StartFromBeginning
      )
    }
  }

  test("failure - update in progress") {
    stateMachine.test(props) {
      testBottomSheetContent(
        expectedTitle = "Device update not complete",
        expectedSubline = "Make sure you hold your device to the back of your phone during the entire update.",
        withUpdateInProgress = true,
        withTransactionType = FwupTransactionType.ResumeFromSequenceId(100U)
      )
    }
  }

  test("failure - update in progress - iOS non iPhone 14") {
    deviceInfoProvider.devicePlatformValue = DevicePlatform.IOS
    stateMachine.test(props) {
      testBottomSheetContent(
        expectedTitle = "Device update not complete",
        expectedSubline = "Make sure you hold your device to the back of your phone during the entire update. Continue the update to resume where it left off.",
        withButtonGoesToRetry = true,
        withUpdateInProgress = true,
        withTransactionType = FwupTransactionType.ResumeFromSequenceId(100U)
      )
    }
  }

  test("failure - update in progress - iOS iPhone 14") {
    deviceInfoProvider.devicePlatformValue = DevicePlatform.IOS
    deviceInfoProvider.deviceModelValue = "iPhone15,2"
    stateMachine.test(props) {
      testBottomSheetContent(
        expectedTitle = "Device update not complete",
        expectedSubline =
          "Make sure you hold your device to the back of your phone during the entire update. Continue the update to resume where it left off." +
            "\n\nIf problems persist, turn on Airplane Mode to minimize interruptions.",
        withButtonGoesToRetry = true,
        withUpdateInProgress = true,
        withTransactionType = FwupTransactionType.ResumeFromSequenceId(100U)
      )
    }
  }
})
