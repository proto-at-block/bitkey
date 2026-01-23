package build.wallet.statemachine.nfc

import androidx.compose.runtime.Composable
import build.wallet.statemachine.BodyModelMock
import build.wallet.statemachine.core.ScreenModel

/**
 * Mock implementation for [NfcConfirmableSessionUiStateMachine].
 *
 * Captures props in the returned [BodyModelMock] for test assertions.
 */
class NfcConfirmableSessionUiStateMachineMock(
  val id: String,
) : NfcConfirmableSessionUiStateMachine {
  @Composable
  override fun <T> model(props: NfcConfirmableSessionUIStateMachineProps<T>): ScreenModel {
    return BodyModelMock(
      id = id,
      latestProps = props
    ).asRootScreen()
  }
}
