package build.wallet.statemachine.send.fee

import androidx.compose.runtime.Composable
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority.FASTEST
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority.SIXTY_MINUTES
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority.THIRTY_MINUTES
import build.wallet.statemachine.core.form.FormMainContentModel.FeeOptionList.FeeOption
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

@OptIn(ExperimentalObjCRefinement::class)
@HiddenFromObjC
class FeeOptionUiStateMachineMock : FeeOptionUiStateMachine {
  var latestProps: FeeOptionProps? = null

  @Composable
  override fun model(props: FeeOptionProps): FeeOption {
    latestProps = props
    return when (props.estimatedTransactionPriority) {
      FASTEST ->
        FeeOption(
          optionName = "Priority",
          transactionTime = "~10 mins",
          transactionFee = props.feeAmount.toString(),
          selected = props.selected,
          onClick = props.onClick,
          enabled = true
        )

      THIRTY_MINUTES ->
        FeeOption(
          optionName = "Standard",
          transactionTime = "~30 mins",
          transactionFee = props.feeAmount.toString(),
          selected = props.selected,
          onClick = props.onClick,
          enabled = true
        )

      SIXTY_MINUTES ->
        FeeOption(
          optionName = "Slow",
          transactionTime = "~60 mins",
          transactionFee = props.feeAmount.toString(),
          selected = props.selected,
          onClick = props.onClick,
          enabled = true
        )
    }
  }
}
