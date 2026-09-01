package build.wallet.statemachine.nfc

import androidx.compose.runtime.Composable
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.nfc.NfcException
import build.wallet.statemachine.core.ScreenModel

@BitkeyInject(ActivityScope::class)
class HardwarePresenceUiStateMachineImpl(
  private val nfcSessionUIStateMachine: NfcSessionUIStateMachine,
) : HardwarePresenceUiStateMachine {
  @Composable
  override fun model(props: HardwarePresenceProps): ScreenModel {
    return nfcSessionUIStateMachine.model(
      props = NfcSessionUIStateMachineProps(
        session = { session, commands ->
          // The validateHardwareIsPaired interceptor already verifies the
          // tapped device is the paired one (challenge signing on W1, serial
          // match on W3). We only need to confirm the device is unlocked.
          //
          // Throw instead of returning the boolean so a locked device fails the
          // NFC transaction itself. Otherwise the transaction "succeeds",
          // showing a false "Success" state (and a W3 "APPROVED" confirmation
          // screen attempt) before the caller ever learns the device was locked
          // (W-17492).
          if (!commands.queryAuthentication(session)) {
            throw NfcException.CommandErrorUnauthenticated()
          }
        },
        onSuccess = { props.onSuccess() },
        onCancel = props.onCancel,
        needsAuthentication = true,
        screenPresentationStyle = props.screenPresentationStyle,
        segment = props.segment,
        actionDescription = props.actionDescription,
        eventTrackerContext = props.eventTrackerContext,
        showNativeSheetOnIos = props.showNativeSheetOnIos,
        showDeviceConfirmation = true
      )
    )
  }
}
