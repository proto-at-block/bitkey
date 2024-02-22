package build.wallet.statemachine.send

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority.FASTEST
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority.SIXTY_MINUTES
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority.THIRTY_MINUTES
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
          fromAmount = props.transactionDetail.transferBitcoinAmount,
          toCurrency = props.fiatCurrency,
          rates = it
        )
      } as FiatMoney?

    val feeFiatAmount: FiatMoney? =
      props.exchangeRates?.let {
        convertedOrZeroWithRates(
          converter = currencyConverter,
          fromAmount = props.transactionDetail.feeBitcoinAmount,
          toCurrency = props.fiatCurrency,
          rates = it
        )
      } as FiatMoney?

    val totalBitcoinAmount =
      props.transactionDetail.transferBitcoinAmount + props.transactionDetail.feeBitcoinAmount
    val totalFiatAmount: FiatMoney? =
      props.exchangeRates?.let {
        convertedOrZeroWithRates(
          converter = currencyConverter,
          fromAmount = totalBitcoinAmount,
          toCurrency = props.fiatCurrency,
          rates = it
        )
      } as FiatMoney?

    val formattedTransferAmountText =
      formattedAmountText(props.transactionDetail.transferBitcoinAmount, transferFiatAmount)

    val transactionDetailModelType =
      when (props.transactionDetail) {
        is TransactionDetailType.Regular ->
          TransactionDetailModelType.Regular(
            transferAmountText = formattedTransferAmountText,
            feeAmountText =
              formattedAmountText(
                props.transactionDetail.feeBitcoinAmount,
                feeFiatAmount
              )
          )
        is TransactionDetailType.SpeedUp -> {
          val oldFeeFiatAmount: FiatMoney? =
            props.exchangeRates?.let {
              convertedOrZeroWithRates(
                converter = currencyConverter,
                fromAmount = props.transactionDetail.oldFeeBitcoinAmount,
                toCurrency = props.fiatCurrency,
                rates = it
              )
            } as FiatMoney?

          val feeDifferenceBitcoinAmount =
            props.transactionDetail.feeBitcoinAmount - props.transactionDetail.oldFeeBitcoinAmount
          val feeDifferenceFiatAmount: FiatMoney? =
            props.exchangeRates?.let {
              convertedOrZeroWithRates(
                converter = currencyConverter,
                fromAmount = feeDifferenceBitcoinAmount,
                toCurrency = props.fiatCurrency,
                rates = it
              )
            } as FiatMoney?

          TransactionDetailModelType.SpeedUp(
            transferAmountText = formattedTransferAmountText,
            oldFeeAmountText =
              formattedAmountText(
                props.transactionDetail.oldFeeBitcoinAmount,
                oldFeeFiatAmount
              ),
            feeDifferenceText = "+${formattedAmountText(
              feeDifferenceBitcoinAmount,
              feeDifferenceFiatAmount
            )}"
          )
        }
      }

    return TransactionDetailsModel(
      transactionSpeedText =
        when (props.transactionDetail) {
          is TransactionDetailType.Regular ->
            when (props.transactionDetail.estimatedTransactionPriority) {
              FASTEST -> "~10 minutes"
              THIRTY_MINUTES -> "~30 minutes"
              SIXTY_MINUTES -> "~60 minutes"
            }
          is TransactionDetailType.SpeedUp -> "~10 minutes"
        },
      transactionDetailModelType = transactionDetailModelType,
      totalAmountPrimaryText = formattedAmountText(totalBitcoinAmount, totalFiatAmount),
      totalAmountSecondaryText = formattedSecondaryAmountText(totalBitcoinAmount, totalFiatAmount)
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
