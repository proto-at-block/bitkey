package build.wallet.statemachine.send

import androidx.compose.runtime.Composable
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority.*
import build.wallet.bitcoin.transactions.TransactionDetails
import build.wallet.money.BitcoinMoney
import build.wallet.money.FiatMoney
import build.wallet.money.exchange.CurrencyConverter
import build.wallet.money.formatter.MoneyDisplayFormatter
import build.wallet.statemachine.data.money.convertedOrZeroWithRates

class TransactionDetailsCardUiStateMachineImpl(
  private val currencyConverter: CurrencyConverter,
  private val moneyDisplayFormatter: MoneyDisplayFormatter,
) : TransactionDetailsCardUiStateMachine {
  @Composable
  override fun model(props: TransactionDetailsCardUiProps): TransactionDetailsModel {
    val transferFiatAmount: FiatMoney? =
      props.exchangeRates?.let {
        convertedOrZeroWithRates(
          converter = currencyConverter,
          fromAmount = props.transactionDetails.transferAmount,
          toCurrency = props.fiatCurrency,
          rates = it
        )
      } as FiatMoney?

    val feeFiatAmount: FiatMoney? =
      props.exchangeRates?.let {
        convertedOrZeroWithRates(
          converter = currencyConverter,
          fromAmount = props.transactionDetails.feeAmount,
          toCurrency = props.fiatCurrency,
          rates = it
        )
      } as FiatMoney?

    val totalBitcoinAmount =
      props.transactionDetails.transferAmount + props.transactionDetails.feeAmount

    val formattedTransferAmountText =
      formattedAmountText(props.transactionDetails.transferAmount, transferFiatAmount)

    val transactionDetailModelType =
      when (props.transactionDetails) {
        is TransactionDetails.Regular -> {
          val totalFiatAmount: FiatMoney? =
            props.exchangeRates?.let {
              convertedOrZeroWithRates(
                converter = currencyConverter,
                fromAmount = totalBitcoinAmount,
                toCurrency = props.fiatCurrency,
                rates = it
              )
            } as FiatMoney?

          TransactionDetailModelType.Regular(
            transferAmountText = formattedTransferAmountText,
            totalAmountPrimaryText = formattedAmountText(totalBitcoinAmount, totalFiatAmount),
            totalAmountSecondaryText = formattedSecondaryAmountText(
              totalBitcoinAmount,
              totalFiatAmount
            ),
            feeAmountText = formattedAmountText(
              props.transactionDetails.feeAmount,
              feeFiatAmount
            )
          )
        }
        is TransactionDetails.SpeedUp -> {
          val oldFeeFiatAmount: FiatMoney? =
            props.exchangeRates?.let {
              convertedOrZeroWithRates(
                converter = currencyConverter,
                fromAmount = props.transactionDetails.oldFeeAmount,
                toCurrency = props.fiatCurrency,
                rates = it
              )
            } as FiatMoney?

          val feeDifferenceBitcoinAmount =
            props.transactionDetails.feeAmount - props.transactionDetails.oldFeeAmount
          val feeDifferenceFiatAmount: FiatMoney? =
            props.exchangeRates?.let {
              convertedOrZeroWithRates(
                converter = currencyConverter,
                fromAmount = feeDifferenceBitcoinAmount,
                toCurrency = props.fiatCurrency,
                rates = it
              )
            } as FiatMoney?

          // We calculate totalFiatMoney separately  from regular transactions because we may get
          // some rounding errors from calculating the fee difference with subtraction and division.
          // It is likely OK for our fiat value to be a little off.
          //
          // If all the conversions above were successful, we compute. Else, we leave the value as
          // null which will fall back to sats in [formattedAmountText].
          val totalFiatMoney: FiatMoney? =
            if (transferFiatAmount != null && oldFeeFiatAmount != null && feeDifferenceFiatAmount != null) {
              transferFiatAmount + oldFeeFiatAmount + feeDifferenceFiatAmount
            } else {
              null
            }

          TransactionDetailModelType.SpeedUp(
            transferAmountText = formattedTransferAmountText,
            totalAmountPrimaryText = formattedAmountText(totalBitcoinAmount, totalFiatMoney),
            totalAmountSecondaryText = formattedSecondaryAmountText(
              totalBitcoinAmount,
              totalFiatMoney
            ),
            oldFeeAmountText =
              formattedAmountText(
                props.transactionDetails.oldFeeAmount,
                oldFeeFiatAmount
              ),
            feeDifferenceText = "+${
              formattedAmountText(
                feeDifferenceBitcoinAmount,
                feeDifferenceFiatAmount
              )
            }"
          )
        }
      }

    return TransactionDetailsModel(
      transactionSpeedText =
        when (props.transactionDetails) {
          is TransactionDetails.Regular ->
            when (props.transactionDetails.estimatedTransactionPriority) {
              FASTEST -> "~10 minutes"
              THIRTY_MINUTES -> "~30 minutes"
              SIXTY_MINUTES -> "~60 minutes"
            }
          is TransactionDetails.SpeedUp -> "~10 minutes"
        },
      transactionDetailModelType = transactionDetailModelType
    )
  }

  private fun formattedAmountText(
    transferBitcoinAmount: BitcoinMoney,
    transferFiatAmount: FiatMoney?,
  ): String {
    return when (transferFiatAmount) {
      null -> moneyDisplayFormatter.format(transferBitcoinAmount)
      else -> moneyDisplayFormatter.format(transferFiatAmount)
    }
  }

  private fun formattedSecondaryAmountText(
    transferBitcoinAmount: BitcoinMoney,
    transferFiatAmount: FiatMoney?,
  ): String? {
    return transferFiatAmount?.let { "(${moneyDisplayFormatter.format(transferBitcoinAmount)})" }
  }
}
