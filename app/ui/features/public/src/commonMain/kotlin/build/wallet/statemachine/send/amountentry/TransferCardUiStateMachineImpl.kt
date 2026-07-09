package build.wallet.statemachine.send.amountentry

import androidx.compose.runtime.Composable
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.core.LabelModel.Color.ON60
import build.wallet.statemachine.moneyhome.card.CardModel
import build.wallet.statemachine.moneyhome.card.CardModel.CardStyle.Outline
import build.wallet.statemachine.send.TransferAmountUiState.ValidAmountEnteredUiState.AmountEqualOrAboveBalanceUiState

@BitkeyInject(ActivityScope::class)
class TransferCardUiStateMachineImpl : TransferCardUiStateMachine {
  @Composable
  override fun model(props: TransferCardUiProps): CardModel? {
    return when (props.transferAmountState) {
      AmountEqualOrAboveBalanceUiState -> CardModel(
        title =
          LabelModel.StringWithStyledSubstringModel.from(
            string = "Send Max (balance minus fees)",
            substringToColor =
              mapOf(
                "(balance minus fees)" to ON60
              )
          ),
        subtitle = null,
        leadingImage = null,
        content = null,
        style = Outline(),
        onClick = props.onSendMaxClick
      )
      else -> null
    }
  }
}
