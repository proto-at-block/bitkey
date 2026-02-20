package build.wallet.statemachine.send.signtransaction

import androidx.compose.runtime.Composable
import build.wallet.statemachine.BodyModelMock
import build.wallet.statemachine.core.ScreenModel

/**
 * Mock implementation for [SignTransactionNfcSessionUiStateMachine].
 *
 * Captures props in the returned [BodyModelMock] for test assertions.
 */
class SignTransactionNfcSessionUiStateMachineMock(
  val id: String,
) : SignTransactionNfcSessionUiStateMachine {
  @Composable
  override fun model(props: SignTransactionNfcSessionUiProps): ScreenModel {
    return BodyModelMock(
      id = id,
      latestProps = props
    ).asRootScreen()
  }
}
