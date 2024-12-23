package build.wallet.statemachine.money.amount

import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.money.exchange.CurrencyConverter
import build.wallet.money.formatter.MoneyDisplayFormatter
import build.wallet.statemachine.data.money.convertedOrNull

@BitkeyInject(ActivityScope::class)
class MoneyAmountUiStateMachineImpl(
  private val currencyConverter: CurrencyConverter,
  private val moneyDisplayFormatter: MoneyDisplayFormatter,
) : MoneyAmountUiStateMachine {
  @Composable
  override fun model(props: MoneyAmountUiProps): MoneyAmountModel {
    val secondaryAmount =
      convertedOrNull(
        converter = currencyConverter,
        fromAmount = props.primaryMoney,
        toCurrency = props.secondaryAmountCurrency
      )

    val primaryAmountFormatted by remember(props.primaryMoney) {
      derivedStateOf {
        moneyDisplayFormatter.format(props.primaryMoney)
      }
    }

    val secondaryAmountFormatted by remember(secondaryAmount) {
      derivedStateOf {
        secondaryAmount?.let { moneyDisplayFormatter.format(it) }
          ?: "~~"
      }
    }

    return MoneyAmountModel(
      primaryAmount = primaryAmountFormatted,
      // TODO(W-165): display currency symbol.
      secondaryAmount = secondaryAmountFormatted
    )
  }
}
