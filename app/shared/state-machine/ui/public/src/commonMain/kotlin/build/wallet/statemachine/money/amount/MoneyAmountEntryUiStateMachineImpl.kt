package build.wallet.statemachine.money.amount

import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.money.formatter.MoneyDisplayFormatter
import build.wallet.money.input.MoneyInputFormatter

@BitkeyInject(ActivityScope::class)
class MoneyAmountEntryUiStateMachineImpl(
  private val moneyInputFormatter: MoneyInputFormatter,
  private val moneyDisplayFormatter: MoneyDisplayFormatter,
) : MoneyAmountEntryUiStateMachine {
  @Composable
  override fun model(props: MoneyAmountEntryProps): MoneyAmountEntryModel {
    val primaryAmountFormatted by remember(props.inputAmount, props.inputAmountMoney) {
      derivedStateOf {
        moneyInputFormatter.displayText(
          inputAmount = props.inputAmount,
          inputAmountCurrency = props.inputAmountMoney.currency
        )
      }
    }

    val secondaryAmountFormatted by remember(props.secondaryAmount) {
      derivedStateOf {
        when (props.secondaryAmount) {
          null -> null
          else -> moneyDisplayFormatter.format(props.secondaryAmount)
        }
      }
    }

    return MoneyAmountEntryModel(
      primaryAmount = primaryAmountFormatted.displayText,
      primaryAmountGhostedSubstringRange = primaryAmountFormatted.displayTextGhostedSubstring?.range,
      secondaryAmount = secondaryAmountFormatted
    )
  }
}
