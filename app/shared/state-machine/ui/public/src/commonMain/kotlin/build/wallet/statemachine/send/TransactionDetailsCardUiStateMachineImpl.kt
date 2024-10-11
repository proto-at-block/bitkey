package build.wallet.statemachine.send

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import build.wallet.bitcoin.transactions.TransactionDetails
import build.wallet.bitcoin.transactions.toFormattedString
import build.wallet.money.BitcoinMoney
import build.wallet.money.FiatMoney
import build.wallet.money.display.FiatCurrencyPreferenceRepository
import build.wallet.money.exchange.CurrencyConverter
import build.wallet.money.formatter.MoneyDisplayFormatter
import build.wallet.statemachine.data.money.convertedOrZeroWithRates

class TransactionDetailsCardUiStateMachineImpl(
  private val currencyConverter: CurrencyConverter,
  private val moneyDisplayFormatter: MoneyDisplayFormatter,
  private val fiatCurrencyPreferenceRepository: FiatCurrencyPreferenceRepository,
) : TransactionDetailsCardUiStateMachine {
  @Composable
  override fun model(props: TransactionDetailsCardUiProps): TransactionDetailsModel {
    val fiatCurrency by fiatCurrencyPreferenceRepository.fiatCurrencyPreference.collectAsState()
    val transferFiatAmount: FiatMoney? =
      props.exchangeRates?.let {
        convertedOrZeroWithRates(
          converter = currencyConverter,
          fromAmount = props.transactionDetails.transferAmount,
          toCurrency = fiatCurrency,
          rates = it
        )
      } as FiatMoney?

    val feeFiatAmount: FiatMoney? =
      props.exchangeRates?.let {
        convertedOrZeroWithRates(
          converter = currencyConverter,
          fromAmount = props.transactionDetails.feeAmount,
          toCurrency = fiatCurrency,
          rates = it
        )
      } as FiatMoney?

    val totalBitcoinAmount =
      props.transactionDetails.transferAmount + props.transactionDetails.feeAmount

    val formattedTransferAmountText = formattedAmount(
      transferBitcoinAmount = props.transactionDetails.transferAmount,
      transferFiatAmount = transferFiatAmount
    )

    val amountLabel = if (props.variant is TransferConfirmationScreenVariant.Sell) {
      "Amount selling"
    } else {
      "Recipient receives"
    }

    val transactionDetailModelType =
      when (props.transactionDetails) {
        is TransactionDetails.Regular -> {
          val totalFiatAmount: FiatMoney? =
            props.exchangeRates?.let {
              convertedOrZeroWithRates(
                converter = currencyConverter,
                fromAmount = totalBitcoinAmount,
                toCurrency = fiatCurrency,
                rates = it
              )
            } as FiatMoney?

          val totalAmountText = formattedAmount(
            transferBitcoinAmount = totalBitcoinAmount,
            transferFiatAmount = totalFiatAmount
          )
          val feeAmountText = formattedAmount(
            transferBitcoinAmount = props.transactionDetails.feeAmount,
            transferFiatAmount = feeFiatAmount
          )

          TransactionDetailModelType.Regular(
            transferAmountText = formattedTransferAmountText.primaryAmountText,
            transferAmountSecondaryText = formattedTransferAmountText.secondaryAmountText,
            totalAmountPrimaryText = totalAmountText.primaryAmountText,
            totalAmountSecondaryText = totalAmountText.secondaryAmountText,
            feeAmountText = feeAmountText.primaryAmountText,
            feeAmountSecondaryText = feeAmountText.secondaryAmountText
          )
        }
        is TransactionDetails.SpeedUp -> {
          val oldFeeFiatAmount: FiatMoney? =
            props.exchangeRates?.let {
              convertedOrZeroWithRates(
                converter = currencyConverter,
                fromAmount = props.transactionDetails.oldFeeAmount,
                toCurrency = fiatCurrency,
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
                toCurrency = fiatCurrency,
                rates = it
              )
            } as FiatMoney?

          // We calculate totalFiatMoney separately from regular transactions because we may get
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

          val totalAmountText = formattedAmount(
            transferBitcoinAmount = totalBitcoinAmount,
            transferFiatAmount = totalFiatMoney
          )
          val oldFeeAmountText = formattedAmount(
            transferBitcoinAmount = props.transactionDetails.oldFeeAmount,
            transferFiatAmount = oldFeeFiatAmount
          )
          val feeDifferenceText = formattedAmount(
            transferBitcoinAmount = feeDifferenceBitcoinAmount,
            transferFiatAmount = feeDifferenceFiatAmount
          )
          val totalFeeText = formattedAmount(
            transferBitcoinAmount = props.transactionDetails.feeAmount,
            transferFiatAmount = feeFiatAmount
          )

          TransactionDetailModelType.SpeedUp(
            transferAmountText = formattedTransferAmountText.primaryAmountText,
            transferAmountSecondaryText = formattedTransferAmountText.secondaryAmountText,
            totalAmountPrimaryText = totalAmountText.primaryAmountText,
            totalAmountSecondaryText = totalAmountText.secondaryAmountText,
            oldFeeAmountText = oldFeeAmountText.primaryAmountText,
            oldFeeAmountSecondaryText = oldFeeAmountText.secondaryAmountText,
            feeDifferenceText = "+${feeDifferenceText.primaryAmountText}",
            feeDifferenceSecondaryText = "${feeDifferenceText.secondaryAmountText}",
            totalFeeText = totalFeeText.primaryAmountText,
            totalFeeSecondaryText = totalFeeText.secondaryAmountText
          )
        }

        is TransactionDetails.Sell -> {
          val totalFiatAmount: FiatMoney? =
            props.exchangeRates?.let {
              convertedOrZeroWithRates(
                converter = currencyConverter,
                fromAmount = totalBitcoinAmount,
                toCurrency = fiatCurrency,
                rates = it
              )
            } as FiatMoney?

          val totalAmountFormatted = formattedAmount(
            transferBitcoinAmount = totalBitcoinAmount,
            transferFiatAmount = totalFiatAmount
          )

          val feeAmountFormatted = formattedAmount(
            transferBitcoinAmount = props.transactionDetails.feeAmount,
            transferFiatAmount = feeFiatAmount
          )

          TransactionDetailModelType.Sell(
            transferAmountText = "~${formattedTransferAmountText.primaryAmountText}",
            transferAmountSecondaryText = formattedTransferAmountText.secondaryAmountText ?: "",
            totalAmountPrimaryText = totalAmountFormatted.primaryAmountText,
            totalAmountSecondaryText = totalAmountFormatted.secondaryAmountText ?: "",
            feeAmountText = feeAmountFormatted.primaryAmountText,
            feeAmountSecondaryText = feeAmountFormatted.secondaryAmountText ?: ""
          )
        }
      }

    return TransactionDetailsModel(
      transactionSpeedText =
        when (props.transactionDetails) {
          is TransactionDetails.Regular -> props.transactionDetails.estimatedTransactionPriority.toFormattedString()
          is TransactionDetails.SpeedUp -> "~10 minutes"
          is TransactionDetails.Sell -> props.transactionDetails.estimatedTransactionPriority.toFormattedString()
        },
      transactionDetailModelType = transactionDetailModelType,
      amountLabel = amountLabel
    )
  }

  /**
   * Wraps amount text to be displayed to the user. The primary text is fiat and the secondary is btc,
   * if exchange rates are available. Otherwise, the primary text is btc and the secondary is null.
   */
  private data class FormattedAmountText(
    val primaryAmountText: String,
    val secondaryAmountText: String?,
  )

  private fun formattedAmount(
    transferBitcoinAmount: BitcoinMoney,
    transferFiatAmount: FiatMoney?,
  ) = when (transferFiatAmount) {
    null -> FormattedAmountText(
      primaryAmountText = moneyDisplayFormatter.format(transferBitcoinAmount),
      secondaryAmountText = null
    )
    else -> FormattedAmountText(
      primaryAmountText = moneyDisplayFormatter.format(transferFiatAmount),
      secondaryAmountText = moneyDisplayFormatter.format(transferBitcoinAmount)
    )
  }
}
