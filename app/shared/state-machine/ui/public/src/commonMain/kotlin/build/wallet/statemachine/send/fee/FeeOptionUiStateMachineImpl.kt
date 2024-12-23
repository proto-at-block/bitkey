package build.wallet.statemachine.send.fee

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority.*
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.money.exchange.CurrencyConverter
import build.wallet.money.formatter.MoneyDisplayFormatter
import build.wallet.statemachine.core.form.FormMainContentModel.FeeOptionList.FeeOption
import build.wallet.statemachine.data.money.convertedOrZeroWithRates

@BitkeyInject(AppScope::class)
class FeeOptionUiStateMachineImpl(
  private val currencyConverter: CurrencyConverter,
  private val moneyDisplayFormatter: MoneyDisplayFormatter,
) : FeeOptionUiStateMachine {
  @Composable
  override fun model(props: FeeOptionProps): FeeOption {
    val btcAmountString = moneyDisplayFormatter.format(props.feeAmount)
    val fiatAmountString =
      props.exchangeRates?.let {
        convertedOrZeroWithRates(
          converter = currencyConverter,
          fromAmount = props.feeAmount,
          toCurrency = props.fiatCurrency,
          rates = props.exchangeRates
        ).let { moneyDisplayFormatter.format(it) }
      }

    val optionEnabled =
      !(props.bitcoinBalance.total - props.feeAmount - props.transactionAmount).isNegative

    return FeeOption(
      optionName = getPriorityLeadingText(props.estimatedTransactionPriority),
      transactionTime = getPriorityTrailingText(props.estimatedTransactionPriority),
      transactionFee =
        when (fiatAmountString) {
          null -> btcAmountString
          else -> "$fiatAmountString ($btcAmountString)"
        },
      selected = props.selected && optionEnabled,
      enabled = optionEnabled,
      infoText =
        when {
          props.showAllFeesEqualText -> "All network fees are the same—\nwe selected the fastest for you."
          !optionEnabled -> "Not enough balance"
          else -> null
        },
      onClick =
        remember {
          props.onClick.takeIf { optionEnabled }
        }
    )
  }
}

private fun getPriorityLeadingText(
  estimatedTransactionPriority: EstimatedTransactionPriority,
): String {
  return when (estimatedTransactionPriority) {
    FASTEST -> "Priority"
    THIRTY_MINUTES -> "Standard"
    SIXTY_MINUTES -> "Slow"
  }
}

private fun getPriorityTrailingText(
  estimatedTransactionPriority: EstimatedTransactionPriority,
): String {
  return when (estimatedTransactionPriority) {
    FASTEST -> "~10 mins"
    THIRTY_MINUTES -> "~30 mins"
    SIXTY_MINUTES -> "~1 hour"
  }
}
