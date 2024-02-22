package build.wallet.statemachine.send.fee

import androidx.compose.runtime.Composable
import build.wallet.statemachine.core.form.FormMainContentModel.FeeOptionList
import build.wallet.statemachine.core.form.FormMainContentModel.FeeOptionList.FeeOption
import kotlinx.collections.immutable.toImmutableList

class FeeOptionListUiStateMachineFake : FeeOptionListUiStateMachine {
  var latestProps: FeeOptionListProps? = null

  @Composable
  override fun model(props: FeeOptionListProps): FeeOptionList {
    latestProps = props
    return FeeOptionList(
      options =
        props.fees.map { entry ->
          FeeOption(
            optionName = "",
            transactionTime = "",
            transactionFee = entry.value.amount.toString(),
            selected = props.defaultPriority == entry.key,
            onClick = { props.onOptionSelected(entry.key) },
            enabled = true
          )
        }.toImmutableList()
    )
  }
}
