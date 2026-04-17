package build.wallet.statemachine.nfc

import bitkey.account.AccountConfigServiceFake
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext.SIGN_TRANSACTION
import build.wallet.analytics.events.screen.id.NfcEventTrackerScreenId
import build.wallet.coroutines.turbine.turbines
import build.wallet.nfc.NfcException
import build.wallet.nfc.platform.ConfirmationHandlesFake
import build.wallet.nfc.platform.HardwareInteraction
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.core.test
import build.wallet.statemachine.send.hardwareconfirmation.HardwareConfirmationUiProps
import build.wallet.statemachine.send.hardwareconfirmation.HardwareConfirmationUiStateMachine
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitBodyMock
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class NfcConfirmableSessionUiStateMachineImplTests : FunSpec({

  val nfcSessionUIStateMachine =
    object : NfcSessionUIStateMachine,
      ScreenStateMachineMock<NfcSessionUIStateMachineProps<*>>("nfc-session") {}

  val hardwareConfirmationUiStateMachine =
    object : HardwareConfirmationUiStateMachine,
      ScreenStateMachineMock<HardwareConfirmationUiProps>("hardware-confirmation") {}

  val accountConfigService = AccountConfigServiceFake()

  val stateMachine = NfcConfirmableSessionUiStateMachineImpl(
    nfcSessionUIStateMachine = nfcSessionUIStateMachine,
    hardwareConfirmationUiStateMachine = hardwareConfirmationUiStateMachine,
    accountConfigService = accountConfigService
  )

  val onCancelCalls = turbines.create<Unit>("onCancel calls")
  val onSuccessCalls = turbines.create<Boolean>("onSuccess calls")
  val onErrorCalls = turbines.create<NfcException>("onError calls")

  val mockRequiresConfirmation = HardwareInteraction.RequiresConfirmation<Boolean>(
    handles = ConfirmationHandlesFake,
    mapResult = { HardwareInteraction.Completed(true) }
  )

  fun createProps(
    confirmationResultContent: ConfirmationResultContent = ConfirmationResultContent(),
  ) = NfcConfirmableSessionUIStateMachineProps<Boolean>(
    session = { _, _ -> HardwareInteraction.Completed(true) },
    onSuccess = { onSuccessCalls.add(it) },
    onCancel = { onCancelCalls.add(Unit) },
    onError = { error ->
      onErrorCalls.add(error)
      true
    },
    screenPresentationStyle = ScreenPresentationStyle.Modal,
    eventTrackerContext = SIGN_TRANSACTION,
    confirmationResultContent = confirmationResultContent
  )

  // Initial State Tests

  test("initial state delegates to NfcSessionUIStateMachine") {
    stateMachine.test(createProps()) {
      awaitBodyMock<NfcSessionUIStateMachineProps<*>>(id = "nfc-session")
    }
  }

  // Confirmation Pending Tests

  test("ConfirmationPending with continuation shows pending screen") {
    stateMachine.test(createProps()) {
      // Initial state: in NFC session
      awaitBodyMock<NfcSessionUIStateMachineProps<*>>(id = "nfc-session") {
        // Simulate the wrapped onError being called with ConfirmationPending
        // First we need to get into InNfcSession state with a continuation
        // The onSuccess callback triggers state changes, let's use onSuccess to get a RequiresConfirmation
        @Suppress("UNCHECKED_CAST")
        val onSuccess = onSuccess as suspend (Any?) -> Unit
        onSuccess(mockRequiresConfirmation)
      }

      // Should now be in AwaitingConfirmation state showing hardware confirmation
      awaitBodyMock<HardwareConfirmationUiProps>(id = "hardware-confirmation") {
        // User confirms on device → starts second NFC session with continuation
        onConfirm()
      }

      // Back in NFC session with continuation set
      awaitBodyMock<NfcSessionUIStateMachineProps<*>>(id = "nfc-session") {
        // Simulate ConfirmationPending error during continuation
        onError(NfcException.ConfirmationPending()).shouldBe(true)
      }

      // Should show pending screen
      awaitBody<HardwareConfirmationResultBodyModel> {
        headline.shouldBe("Review action on Bitkey")
        eventTrackerScreenId.shouldBe(NfcEventTrackerScreenId.NFC_CONFIRMATION_PENDING)
      }
    }
  }

  test("UserDenied with continuation shows denied screen") {
    stateMachine.test(createProps()) {
      awaitBodyMock<NfcSessionUIStateMachineProps<*>>(id = "nfc-session") {
        @Suppress("UNCHECKED_CAST")
        val onSuccess = onSuccess as suspend (Any?) -> Unit
        onSuccess(mockRequiresConfirmation)
      }

      awaitBodyMock<HardwareConfirmationUiProps>(id = "hardware-confirmation") {
        onConfirm()
      }

      awaitBodyMock<NfcSessionUIStateMachineProps<*>>(id = "nfc-session") {
        onError(NfcException.UserDenied()).shouldBe(true)
      }

      awaitBody<HardwareConfirmationResultBodyModel> {
        headline.shouldBe("The action was not confirmed on your Bitkey")
        eventTrackerScreenId.shouldBe(NfcEventTrackerScreenId.NFC_CONFIRMATION_DENIED)
      }
    }
  }

  test("pending screen acknowledge returns to awaiting confirmation") {
    stateMachine.test(createProps()) {
      awaitBodyMock<NfcSessionUIStateMachineProps<*>>(id = "nfc-session") {
        @Suppress("UNCHECKED_CAST")
        val onSuccess = onSuccess as suspend (Any?) -> Unit
        onSuccess(mockRequiresConfirmation)
      }

      awaitBodyMock<HardwareConfirmationUiProps>(id = "hardware-confirmation") {
        onConfirm()
      }

      awaitBodyMock<NfcSessionUIStateMachineProps<*>>(id = "nfc-session") {
        onError(NfcException.ConfirmationPending()).shouldBe(true)
      }

      awaitBody<HardwareConfirmationResultBodyModel> {
        onAcknowledge()
      }

      // Should return to hardware confirmation screen
      awaitBodyMock<HardwareConfirmationUiProps>(id = "hardware-confirmation")
    }
  }

  test("denied screen acknowledge returns to beginning of flow") {
    stateMachine.test(createProps()) {
      awaitBodyMock<NfcSessionUIStateMachineProps<*>>(id = "nfc-session") {
        @Suppress("UNCHECKED_CAST")
        val onSuccess = onSuccess as suspend (Any?) -> Unit
        onSuccess(mockRequiresConfirmation)
      }

      awaitBodyMock<HardwareConfirmationUiProps>(id = "hardware-confirmation") {
        onConfirm()
      }

      awaitBodyMock<NfcSessionUIStateMachineProps<*>>(id = "nfc-session") {
        onError(NfcException.UserDenied()).shouldBe(true)
      }

      awaitBody<HardwareConfirmationResultBodyModel> {
        onAcknowledge()
      }

      // Should return to initial NFC session (beginning of flow)
      awaitBodyMock<NfcSessionUIStateMachineProps<*>>(id = "nfc-session")
    }
  }

  // No Continuation Fallback Tests

  test("ConfirmationPending without continuation falls back to onError") {
    stateMachine.test(createProps()) {
      // In initial NFC session (no continuation)
      awaitBodyMock<NfcSessionUIStateMachineProps<*>>(id = "nfc-session") {
        // Should fall back to props.onError since there's no continuation
        onError(NfcException.ConfirmationPending()).shouldBe(true)
      }

      onErrorCalls.awaitItem()
    }
  }

  test("UserDenied without continuation falls back to onError") {
    stateMachine.test(createProps()) {
      awaitBodyMock<NfcSessionUIStateMachineProps<*>>(id = "nfc-session") {
        onError(NfcException.UserDenied()).shouldBe(true)
      }

      onErrorCalls.awaitItem()
    }
  }

  test("ConfirmationNotCompleted with continuation shows denied screen") {
    stateMachine.test(createProps()) {
      awaitBodyMock<NfcSessionUIStateMachineProps<*>>(id = "nfc-session") {
        @Suppress("UNCHECKED_CAST")
        val onSuccess = onSuccess as suspend (Any?) -> Unit
        onSuccess(mockRequiresConfirmation)
      }

      awaitBodyMock<HardwareConfirmationUiProps>(id = "hardware-confirmation") {
        onConfirm()
      }

      awaitBodyMock<NfcSessionUIStateMachineProps<*>>(id = "nfc-session") {
        onError(NfcException.ConfirmationNotCompleted()).shouldBe(true)
      }

      awaitBody<HardwareConfirmationResultBodyModel> {
        headline.shouldBe("The action was not confirmed on your Bitkey")
        eventTrackerScreenId.shouldBe(NfcEventTrackerScreenId.NFC_CONFIRMATION_DENIED)
      }
    }
  }

  test("ConfirmationNotCompleted without continuation falls back to onError") {
    stateMachine.test(createProps()) {
      awaitBodyMock<NfcSessionUIStateMachineProps<*>>(id = "nfc-session") {
        onError(NfcException.ConfirmationNotCompleted()).shouldBe(true)
      }

      onErrorCalls.awaitItem()
    }
  }

  // Custom Content Tests

  test("custom confirmation result content is shown on pending screen") {
    val customContent = ConfirmationResultContent(
      pendingHeadline = "Custom pending headline",
      pendingSubline = "Custom pending subline"
    )

    stateMachine.test(createProps(confirmationResultContent = customContent)) {
      awaitBodyMock<NfcSessionUIStateMachineProps<*>>(id = "nfc-session") {
        @Suppress("UNCHECKED_CAST")
        val onSuccess = onSuccess as suspend (Any?) -> Unit
        onSuccess(mockRequiresConfirmation)
      }

      awaitBodyMock<HardwareConfirmationUiProps>(id = "hardware-confirmation") {
        onConfirm()
      }

      awaitBodyMock<NfcSessionUIStateMachineProps<*>>(id = "nfc-session") {
        onError(NfcException.ConfirmationPending()).shouldBe(true)
      }

      awaitBody<HardwareConfirmationResultBodyModel> {
        headline.shouldBe("Custom pending headline")
        subline.shouldBe("Custom pending subline")
      }
    }
  }

  test("custom confirmation result content is shown on denied screen") {
    val customContent = ConfirmationResultContent(
      deniedHeadline = "Custom denied headline"
    )

    stateMachine.test(createProps(confirmationResultContent = customContent)) {
      awaitBodyMock<NfcSessionUIStateMachineProps<*>>(id = "nfc-session") {
        @Suppress("UNCHECKED_CAST")
        val onSuccess = onSuccess as suspend (Any?) -> Unit
        onSuccess(mockRequiresConfirmation)
      }

      awaitBodyMock<HardwareConfirmationUiProps>(id = "hardware-confirmation") {
        onConfirm()
      }

      awaitBodyMock<NfcSessionUIStateMachineProps<*>>(id = "nfc-session") {
        onError(NfcException.UserDenied()).shouldBe(true)
      }

      awaitBody<HardwareConfirmationResultBodyModel> {
        headline.shouldBe("Custom denied headline")
      }
    }
  }
})
