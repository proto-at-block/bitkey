package build.wallet.statemachine.send.amountentry

import androidx.compose.runtime.Composable
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.core.LabelModel.Color.ON60
import build.wallet.statemachine.send.TransferAmountUiState.ValidAmountEnteredUiState.AmountEqualOrAboveBalanceUiState

@BitkeyInject(ActivityScope::class)
class SmartBarUiStateMachineImpl : SmartBarUiStateMachine {
  @Composable
  override fun model(props: SmartBarUiProps): SmartBarModel? {
    return when (props.transferAmountState) {
      AmountEqualOrAboveBalanceUiState -> SmartBarModel(
        title = LabelModel.StringWithStyledSubstringModel.from(
          string = "Send Max (balance minus fees)",
          substringToColor = mapOf("(balance minus fees)" to ON60)
        ),
        onClick = props.onSendMaxClick
      )
      else -> null
    }
  }
}
