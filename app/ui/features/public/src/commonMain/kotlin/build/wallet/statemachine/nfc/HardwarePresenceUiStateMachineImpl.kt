package build.wallet.statemachine.nfc

import androidx.compose.runtime.Composable
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
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
          commands.queryAuthentication(session)
        },
        onSuccess = { isAuthenticated ->
          if (isAuthenticated) {
            props.onSuccess()
          } else {
            props.onFailure(Error("Device is locked"))
          }
        },
        onCancel = props.onCancel,
        needsAuthentication = true,
        screenPresentationStyle = props.screenPresentationStyle,
        eventTrackerContext = props.eventTrackerContext,
        showNativeSheetOnIos = props.showNativeSheetOnIos,
        showDeviceConfirmation = true
      )
    )
  }
}
