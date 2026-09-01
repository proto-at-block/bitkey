package build.wallet.statemachine.nfc

import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.coroutines.turbine.turbines
import build.wallet.nfc.NfcCommandsMock
import build.wallet.nfc.NfcException
import build.wallet.nfc.NfcSession
import build.wallet.nfc.NfcSessionFake
import build.wallet.statemachine.ScreenStateMachineMock
import build.wallet.statemachine.core.test
import build.wallet.statemachine.ui.awaitUntilBodyMock
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue

class HardwarePresenceUiStateMachineImplTests : FunSpec({

  val nfcSessionUIStateMachine =
    object : NfcSessionUIStateMachine,
      ScreenStateMachineMock<NfcSessionUIStateMachineProps<*>>("nfc") {}

  val onSuccessCalls = turbines.create<Unit>("onSuccess calls")
  val onCancelCalls = turbines.create<Unit>("onCancel calls")

  val stateMachine = HardwarePresenceUiStateMachineImpl(
    nfcSessionUIStateMachine = nfcSessionUIStateMachine
  )

  fun props() =
    HardwarePresenceProps(
      onSuccess = { onSuccessCalls.add(Unit) },
      onCancel = { onCancelCalls.add(Unit) },
      segment = HardwarePresenceTestSegment,
      actionDescription = "Testing hardware presence",
      eventTrackerContext = NfcEventTrackerScreenIdContext.METADATA
    )

  test("successful proof of possession - device is authenticated") {
    stateMachine.test(props = props()) {
      awaitUntilBodyMock<NfcSessionUIStateMachineProps<Unit>> {
        onSuccess(Unit)
      }

      onSuccessCalls.awaitItem()
    }
  }

  val commands = object : NfcCommandsMock(turbine = turbines::create) {
    var isAuthenticated = true

    override suspend fun queryAuthentication(session: NfcSession) = isAuthenticated
  }

  beforeTest {
    commands.isAuthenticated = true
  }

  test("locked device fails the NFC transaction with CommandErrorUnauthenticated") {
    commands.isAuthenticated = false

    stateMachine.test(props = props()) {
      awaitUntilBodyMock<NfcSessionUIStateMachineProps<Unit>> {
        shouldThrow<NfcException.CommandErrorUnauthenticated> {
          session(NfcSessionFake(), commands)
        }
      }
    }
  }

  test("unlocked device completes the session without error") {
    stateMachine.test(props = props()) {
      awaitUntilBodyMock<NfcSessionUIStateMachineProps<Unit>> {
        session(NfcSessionFake(), commands)
      }
    }
  }

  test("cancel propagated to props callback") {
    stateMachine.test(props = props()) {
      awaitUntilBodyMock<NfcSessionUIStateMachineProps<Unit>> {
        onCancel()
      }

      onCancelCalls.awaitItem()
    }
  }

  test("NFC session is configured with needsAuthentication = true") {
    stateMachine.test(props = props()) {
      awaitUntilBodyMock<NfcSessionUIStateMachineProps<Unit>> {
        needsAuthentication.shouldBeTrue()
      }
    }
  }

  test("NFC session is configured with showDeviceConfirmation = true") {
    stateMachine.test(props = props()) {
      awaitUntilBodyMock<NfcSessionUIStateMachineProps<Unit>> {
        showDeviceConfirmation.shouldBeTrue()
      }
    }
  }

  test("NFC session can disable native sheet on iOS") {
    stateMachine.test(
      props = props().copy(showNativeSheetOnIos = false)
    ) {
      awaitUntilBodyMock<NfcSessionUIStateMachineProps<Unit>> {
        showNativeSheetOnIos.shouldBeFalse()
      }
    }
  }
})

private object HardwarePresenceTestSegment : build.wallet.statemachine.core.AppSegment {
  override val id: String = "Test"
}
