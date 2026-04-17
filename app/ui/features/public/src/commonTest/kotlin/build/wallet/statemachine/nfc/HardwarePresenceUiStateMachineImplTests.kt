package build.wallet.statemachine.nfc

import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.coroutines.turbine.turbines
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.core.test
import build.wallet.statemachine.ui.awaitUntilBodyMock
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.string.shouldContain

class HardwarePresenceUiStateMachineImplTests : FunSpec({

  val nfcSessionUIStateMachine =
    object : NfcSessionUIStateMachine,
      ScreenStateMachineMock<NfcSessionUIStateMachineProps<*>>("nfc") {}

  val onSuccessCalls = turbines.create<Unit>("onSuccess calls")
  val onFailureCalls = turbines.create<Error>("onFailure calls")
  val onCancelCalls = turbines.create<Unit>("onCancel calls")

  val stateMachine = HardwarePresenceUiStateMachineImpl(
    nfcSessionUIStateMachine = nfcSessionUIStateMachine
  )

  test("successful proof of possession - device is authenticated") {
    stateMachine.test(
      props = HardwarePresenceProps(
        onSuccess = { onSuccessCalls.add(Unit) },
        onFailure = { onFailureCalls.add(it) },
        onCancel = { onCancelCalls.add(Unit) },
        eventTrackerContext = NfcEventTrackerScreenIdContext.METADATA
      )
    ) {
      awaitUntilBodyMock<NfcSessionUIStateMachineProps<Boolean>> {
        onSuccess(true)
      }

      onSuccessCalls.awaitItem()
    }
  }

  test("failure - device not authenticated") {
    stateMachine.test(
      props = HardwarePresenceProps(
        onSuccess = { onSuccessCalls.add(Unit) },
        onFailure = { onFailureCalls.add(it) },
        onCancel = { onCancelCalls.add(Unit) },
        eventTrackerContext = NfcEventTrackerScreenIdContext.METADATA
      )
    ) {
      awaitUntilBodyMock<NfcSessionUIStateMachineProps<Boolean>> {
        onSuccess(false)
      }

      onFailureCalls.awaitItem().message.shouldContain("locked")
    }
  }

  test("cancel propagated to props callback") {
    stateMachine.test(
      props = HardwarePresenceProps(
        onSuccess = { onSuccessCalls.add(Unit) },
        onFailure = { onFailureCalls.add(it) },
        onCancel = { onCancelCalls.add(Unit) },
        eventTrackerContext = NfcEventTrackerScreenIdContext.METADATA
      )
    ) {
      awaitUntilBodyMock<NfcSessionUIStateMachineProps<Boolean>> {
        onCancel()
      }

      onCancelCalls.awaitItem()
    }
  }

  test("NFC session is configured with needsAuthentication = true") {
    stateMachine.test(
      props = HardwarePresenceProps(
        onSuccess = { onSuccessCalls.add(Unit) },
        onFailure = { onFailureCalls.add(it) },
        onCancel = { onCancelCalls.add(Unit) },
        eventTrackerContext = NfcEventTrackerScreenIdContext.METADATA
      )
    ) {
      awaitUntilBodyMock<NfcSessionUIStateMachineProps<Boolean>> {
        needsAuthentication.shouldBeTrue()
      }
    }
  }

  test("NFC session is configured with showDeviceConfirmation = true") {
    stateMachine.test(
      props = HardwarePresenceProps(
        onSuccess = { onSuccessCalls.add(Unit) },
        onFailure = { onFailureCalls.add(it) },
        onCancel = { onCancelCalls.add(Unit) },
        eventTrackerContext = NfcEventTrackerScreenIdContext.METADATA
      )
    ) {
      awaitUntilBodyMock<NfcSessionUIStateMachineProps<Boolean>> {
        showDeviceConfirmation.shouldBeTrue()
      }
    }
  }

  test("NFC session can disable native sheet on iOS") {
    stateMachine.test(
      props = HardwarePresenceProps(
        onSuccess = { onSuccessCalls.add(Unit) },
        onFailure = { onFailureCalls.add(it) },
        onCancel = { onCancelCalls.add(Unit) },
        eventTrackerContext = NfcEventTrackerScreenIdContext.METADATA,
        showNativeSheetOnIos = false
      )
    ) {
      awaitUntilBodyMock<NfcSessionUIStateMachineProps<Boolean>> {
        showNativeSheetOnIos.shouldBeFalse()
      }
    }
  }
})
