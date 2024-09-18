package build.wallet.statemachine.utxo

import androidx.compose.runtime.Composable
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.LoadingSuccessBodyModel.State.Success
import build.wallet.statemachine.core.ScreenModel

class UtxoConsolidationUiStateMachineImpl : UtxoConsolidationUiStateMachine {
  @Composable
  override fun model(props: UtxoConsolidationProps): ScreenModel {
    // TODO(W-9547): implement
    return LoadingSuccessBodyModel(
      onBack = props.onBack,
      state = Success,
      id = null
    ).asRootScreen()
  }
}
