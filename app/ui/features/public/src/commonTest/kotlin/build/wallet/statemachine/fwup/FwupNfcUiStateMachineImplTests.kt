package build.wallet.statemachine.fwup

import build.wallet.coroutines.turbine.turbines
import build.wallet.nfc.NfcException
import build.wallet.platform.device.DeviceInfoProviderMock
import build.wallet.platform.device.DevicePlatform
import build.wallet.platform.web.InAppBrowserNavigatorMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.core.*
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.nfc.FwupInstructionsBodyModel
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitBodyMock
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class FwupNfcUiStateMachineImplTests : FunSpec({

  val deviceInfoProvider = DeviceInfoProviderMock()
  val inAppBrowserNavigator = InAppBrowserNavigatorMock(turbines::create)

  val fwupNfcSessionUiStateMachine =
    object : FwupNfcSessionUiStateMachine,
      ScreenStateMachineMock<FwupNfcSessionUiProps>(
        id = "fwup-nfc-session"
      ) {}

  val nfcSessionUIStateMachine =
    object : NfcSessionUIStateMachine,
      ScreenStateMachineMock<NfcSessionUIStateMachineProps<*>>(
        id = "nfc-session"
      ) {}

  val stateMachine =
    FwupNfcUiStateMachineImpl(
      deviceInfoProvider = deviceInfoProvider,
      fwupNfcSessionUiStateMachine = fwupNfcSessionUiStateMachine,
      nfcSessionUIStateMachine = nfcSessionUIStateMachine,
      inAppBrowserNavigator = inAppBrowserNavigator
    )

  val onDoneCalls = turbines.create<Unit>("onDone calls")
  val props =
    FwupNfcUiProps(
      onDone = { onDoneCalls.add(Unit) }
    )

  beforeTest {
    deviceInfoProvider.reset()
  }

  test("back button on demo instructions calls props.onDone") {
    stateMachine.test(props) {
      awaitBody<FwupInstructionsBodyModel> {
        onBack()
      }

      onDoneCalls.awaitItem()
    }
  }

  test("happy path") {
    stateMachine.test(props) {
      awaitBody<FwupInstructionsBodyModel> {
        buttonModel.onClick()
      }

      awaitBodyMock<FwupNfcSessionUiProps> {
        onDone("1.2.3")
      }

      awaitBodyMock<NfcSessionUIStateMachineProps<Unit>> {
        onSuccess.invoke(Unit)
      }

      // Success screen
      awaitBody<SuccessBodyModel> {
        title.shouldBe("Firmware updated")
        message.shouldBe("Your Bitkey is now running the latest firmware and ready to use.")
        primaryButtonModel.shouldNotBeNull().onClick()
      }

      onDoneCalls.awaitItem()
    }
  }

  test("fwup nfc session onBack") {
    stateMachine.test(props) {
      awaitBody<FwupInstructionsBodyModel> {
        buttonModel.onClick()
      }

      awaitBodyMock<FwupNfcSessionUiProps> {
        onBack()
      }

      // Back to update instructions
      awaitItem()
        .bottomSheetModel.shouldBeNull()
    }
  }

  test("release notes") {
    stateMachine.test(props) {
      awaitBody<FwupInstructionsBodyModel> {
        (headerModel.sublineModel as LabelModel.LinkSubstringModel).linkedSubstrings[0].onClick()
      }

      awaitBody<InAppBrowserModel> {
        open()
      }

      inAppBrowserNavigator.onOpenCalls.awaitItem()
        .shouldBe("https://bitkey.world/en-US/releases")
    }
  }

  test("verification failure") {
    stateMachine.test(props) {
      awaitBody<FwupInstructionsBodyModel> {
        buttonModel.onClick()
      }

      awaitBodyMock<FwupNfcSessionUiProps> {
        onDone("1.0.0")
      }

      // Verification step fails
      awaitBodyMock<NfcSessionUIStateMachineProps<Unit>> {
        onError(NfcException.CommandError())
      }

      // Error screen
      awaitBody<FormBodyModel> {
        with(header.shouldNotBeNull()) {
          headline.shouldBe("Firmware update failed")
          sublineModel.shouldNotBeNull().string.shouldBe("Your Bitkey was unable to install the firmware update. Please try again.")
        }
        primaryButton.shouldNotBeNull().onClick()
      }

      // Back to update instructions
      awaitItem()
        .bottomSheetModel.shouldBeNull()
    }
  }

  test("verification cancellation") {
    stateMachine.test(props) {
      awaitBody<FwupInstructionsBodyModel> {
        buttonModel.onClick()
      }

      awaitBodyMock<FwupNfcSessionUiProps> {
        onDone("1.0.0")
      }

      // User cancels verification
      awaitBodyMock<NfcSessionUIStateMachineProps<Unit>> {
        onCancel()
      }

      // Error screen
      awaitBody<FormBodyModel> {
        with(header.shouldNotBeNull()) {
          headline.shouldBe("Firmware update failed")
        }
        primaryButton.shouldNotBeNull().onClick()
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
    awaitBody<FwupInstructionsBodyModel> {
      buttonModel.onClick()
    }

    awaitBodyMock<FwupNfcSessionUiProps> {
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
        awaitBodyMock<FwupNfcSessionUiProps>()
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
    stateMachine.testWithVirtualTime(props) {
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
    stateMachine.testWithVirtualTime(props) {
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
