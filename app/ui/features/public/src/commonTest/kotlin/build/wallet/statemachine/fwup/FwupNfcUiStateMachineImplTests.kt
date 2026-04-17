package build.wallet.statemachine.fwup

import bitkey.account.AccountConfigServiceFake
import bitkey.account.HardwareType
import build.wallet.analytics.events.EventTrackerMock
import build.wallet.analytics.events.TrackedAction
import build.wallet.analytics.v1.Action.ACTION_APP_TAP_FWUP_CARD
import build.wallet.coroutines.turbine.turbines
import build.wallet.fwup.FwupFinishResponseStatus
import build.wallet.keybox.KeyboxDaoMock
import build.wallet.nfc.NfcException
import build.wallet.platform.device.DeviceInfoProviderMock
import build.wallet.platform.device.DevicePlatform
import build.wallet.platform.web.InAppBrowserNavigatorMock
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.core.InAppBrowserModel
import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachineTester
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.core.testWithVirtualTime
import build.wallet.statemachine.nfc.FwupInstructionsBodyModel
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitBodyMock
import build.wallet.ui.theme.Theme
import build.wallet.ui.theme.ThemePreference
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class FwupNfcUiStateMachineImplTests : FunSpec({

  val eventTracker = EventTrackerMock(turbines::create)
  val deviceInfoProvider = DeviceInfoProviderMock()
  val inAppBrowserNavigator = InAppBrowserNavigatorMock(turbines::create)
  val accountConfigService = AccountConfigServiceFake()
  val keyboxDao = KeyboxDaoMock(turbines::create)

  val fwupNfcSessionUiStateMachine =
    object : FwupNfcSessionUiStateMachine,
      ScreenStateMachineMock<FwupNfcSessionUiProps>(
        id = "fwup-nfc-session"
      ) {}

  val stateMachine =
    FwupNfcUiStateMachineImpl(
      deviceInfoProvider = deviceInfoProvider,
      fwupNfcSessionUiStateMachine = fwupNfcSessionUiStateMachine,
      inAppBrowserNavigator = inAppBrowserNavigator,
      accountConfigService = accountConfigService,
      keyboxDao = keyboxDao,
      eventTracker = eventTracker
    )

  val onDoneCalls = turbines.create<Unit>("onDone calls")
  val props =
    FwupNfcUiProps(
      onDone = { onDoneCalls.add(Unit) }
    )

  beforeTest {
    accountConfigService.reset()
    deviceInfoProvider.reset()
    keyboxDao.reset()
  }

  test("back button on demo instructions calls props.onDone") {
    stateMachine.test(props) {
      awaitBody<FwupInstructionsBodyModel> {
        onBack()
      }

      onDoneCalls.awaitItem()
    }
  }

  test("update instructions stay dark on iOS for W1") {
    deviceInfoProvider.devicePlatformValue = DevicePlatform.IOS

    stateMachine.test(props) {
      awaitItem().apply {
        themePreference.shouldBe(ThemePreference.Manual(Theme.DARK))
        body.shouldBeInstanceOf<FwupInstructionsBodyModel>()
      }
    }
  }

  test("update instructions inherit system theme on iOS for W3") {
    deviceInfoProvider.devicePlatformValue = DevicePlatform.IOS
    accountConfigService.setHardwareType(HardwareType.W3)

    stateMachine.test(props) {
      awaitItem().apply {
        themePreference.shouldBe(ThemePreference.System)
        body.shouldBeInstanceOf<FwupInstructionsBodyModel>()
      }
    }
  }

  test("update instructions stay dark on Android") {
    stateMachine.test(props) {
      awaitItem().themePreference.shouldBe(ThemePreference.Manual(Theme.DARK))
    }
  }

  test("happy path") {
    stateMachine.test(props) {
      awaitBody<FwupInstructionsBodyModel> {
        buttonModel.onClick()
      }

      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_APP_TAP_FWUP_CARD))

      awaitBodyMock<FwupNfcSessionUiProps> {
        onDone()
      }

      onDoneCalls.awaitItem()
    }
  }

  test("FWUP session forwards native sheet preference") {
    stateMachine.test(
      props.copy(showNativeSheetOnIos = false)
    ) {
      awaitBody<FwupInstructionsBodyModel> {
        buttonModel.onClick()
      }

      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_APP_TAP_FWUP_CARD))

      awaitBodyMock<FwupNfcSessionUiProps> {
        showNativeSheetOnIos.shouldBe(false)
      }
    }
  }

  test("fwup nfc session onBack") {
    stateMachine.test(props) {
      awaitBody<FwupInstructionsBodyModel> {
        buttonModel.onClick()
      }

      eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_APP_TAP_FWUP_CARD))

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

  // Helper to test error bottom sheet content
  suspend fun StateMachineTester<FwupNfcUiProps, ScreenModel>.testBottomSheetContent(
    error: NfcException = NfcException.CommandError(),
    expectedTitle: String,
    expectedSubline: String,
    withButtonGoesToRetry: Boolean = false,
    expectedButtonText: String? = null,
    withUpdateInProgress: Boolean,
    withTransactionType: FwupTransactionType,
  ) {
    awaitBody<FwupInstructionsBodyModel> {
      buttonModel.onClick()
    }

    eventTracker.eventCalls.awaitItem().shouldBe(TrackedAction(ACTION_APP_TAP_FWUP_CARD))

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
          expectedButtonText ?: when (withButtonGoesToRetry) {
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
        withTransactionType = FwupTransactionType.StartFromBeginning()
      )
    }
  }

  test("failure - no update in progress") {
    stateMachine.test(props) {
      testBottomSheetContent(
        expectedTitle = "Unable to update device",
        expectedSubline = "Make sure you hold your device to the back of your phone during the entire update.",
        withUpdateInProgress = false,
        withTransactionType = FwupTransactionType.StartFromBeginning()
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
        withTransactionType = FwupTransactionType.StartFromBeginning()
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

  test("failure - previous MCU update not applied shows specific error and restarts") {
    stateMachine.testWithVirtualTime(props) {
      testBottomSheetContent(
        error = NfcException.PreviousMcuUpdateNotApplied(
          message = "Previous MCU UXC update was not applied"
        ),
        expectedTitle = "Previous update not applied",
        expectedSubline = "The previous update was not applied on your device. " +
          "Please restart the update to try again.",
        withButtonGoesToRetry = true,
        expectedButtonText = "Restart update",
        withUpdateInProgress = false,
        withTransactionType = FwupTransactionType.StartFromBeginning()
      )
    }
  }

  test("failure - timeout shows specific error") {
    stateMachine.test(props) {
      testBottomSheetContent(
        error = NfcException.Timeout(),
        expectedTitle = "Update timed out",
        expectedSubline = "The connection timed out during the update. Hold your device closer to your phone and try again.",
        withUpdateInProgress = false,
        withTransactionType = FwupTransactionType.StartFromBeginning()
      )
    }
  }

  test("failure - tag lost shows connection interrupted error") {
    stateMachine.test(props) {
      testBottomSheetContent(
        error = NfcException.CanBeRetried.TagLost(),
        expectedTitle = "Connection interrupted",
        expectedSubline = "Lost connection to your device during the update. Hold your device steady against the back of your phone and try again.",
        withUpdateInProgress = false,
        withTransactionType = FwupTransactionType.StartFromBeginning()
      )
    }
  }

  test("failure - transceive failure shows connection interrupted error") {
    stateMachine.test(props) {
      testBottomSheetContent(
        error = NfcException.CanBeRetried.TransceiveFailure(),
        expectedTitle = "Connection interrupted",
        expectedSubline = "Lost connection to your device during the update. Hold your device steady against the back of your phone and try again.",
        withUpdateInProgress = false,
        withTransactionType = FwupTransactionType.StartFromBeginning()
      )
    }
  }

  test("failure - fwup finish signature invalid shows specific error") {
    stateMachine.test(props) {
      testBottomSheetContent(
        error = NfcException.FwupFinishError(
          status = FwupFinishResponseStatus.SignatureInvalid,
          message = "fwup_finish failed: SignatureInvalid"
        ),
        expectedTitle = "Signature verification failed",
        expectedSubline = "The firmware update could not be verified. Please try again or contact support if the issue persists.",
        withUpdateInProgress = true,
        withTransactionType = FwupTransactionType.StartFromBeginning()
      )
    }
  }

  test("failure - fwup finish version invalid shows specific error") {
    stateMachine.test(props) {
      testBottomSheetContent(
        error = NfcException.FwupFinishError(
          status = FwupFinishResponseStatus.VersionInvalid,
          message = "fwup_finish failed: VersionInvalid"
        ),
        expectedTitle = "Incompatible firmware version",
        expectedSubline = "This firmware version is not compatible with your device. Please check for a newer update.",
        withUpdateInProgress = true,
        withTransactionType = FwupTransactionType.StartFromBeginning()
      )
    }
  }

  test("failure - fwup finish generic error shows update failed") {
    stateMachine.test(props) {
      testBottomSheetContent(
        error = NfcException.FwupFinishError(
          status = FwupFinishResponseStatus.Error,
          message = "fwup_finish failed: Error"
        ),
        expectedTitle = "Update failed",
        expectedSubline = "The firmware update could not complete. Please try again.",
        withUpdateInProgress = true,
        withTransactionType = FwupTransactionType.StartFromBeginning()
      )
    }
  }

  test("failure - timeout in progress on iOS shows resume and airplane mode") {
    deviceInfoProvider.devicePlatformValue = DevicePlatform.IOS
    deviceInfoProvider.deviceModelValue = "iPhone15,2"
    stateMachine.testWithVirtualTime(props) {
      testBottomSheetContent(
        error = NfcException.Timeout(),
        expectedTitle = "Update timed out",
        expectedSubline = "The connection timed out during the update. Hold your device closer to your phone and try again." +
          "\n\nContinue the update to resume where it left off." +
          "\n\nIf problems persist, turn on Airplane Mode to minimize interruptions.",
        withButtonGoesToRetry = true,
        withUpdateInProgress = true,
        withTransactionType = FwupTransactionType.ResumeFromSequenceId(100U)
      )
    }
  }
})
