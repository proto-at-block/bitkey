package build.wallet.money.input

import build.wallet.amount.Amount
import build.wallet.amount.Amount.DecimalNumber
import build.wallet.amount.Amount.WholeNumber
import build.wallet.amount.DecimalSeparatorProvider
import build.wallet.amount.DoubleFormatter
import build.wallet.money.BitcoinMoney
import build.wallet.money.FiatMoney
import build.wallet.money.Money
import build.wallet.money.currency.BTC
import build.wallet.money.currency.CryptoCurrency
import build.wallet.money.currency.Currency
import build.wallet.money.currency.FiatCurrency
import build.wallet.money.formatter.internal.MoneyFormatterDefinitionsImpl
import build.wallet.money.input.MoneyInputDisplayText.Substring
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import kotlin.ranges.IntRange.Companion.EMPTY

class MoneyInputFormatterImpl(
  private val decimalSeparatorProvider: DecimalSeparatorProvider,
  private val doubleFormatter: DoubleFormatter,
  private val moneyFormatterDefinitions: MoneyFormatterDefinitionsImpl,
) : MoneyInputFormatter {
  override fun displayText(
    inputAmount: Amount,
    inputAmountCurrency: Currency,
  ): MoneyInputDisplayText {
    return when (inputAmount) {
      is WholeNumber -> {
        // Only Bitcoin amounts can be entered in whole number amounts (Satoshis) currently.
        require(inputAmountCurrency == BTC)
        val moneyAmount = BitcoinMoney.sats(inputAmount.number)
        return MoneyInputDisplayText(
          displayText =
            moneyFormatterDefinitions.bitcoinFractionalNameOnly.stringValue(
              moneyAmount
            )
        )
      }

      is DecimalNumber -> {
        val inputAmountNumber = doubleFormatter.parse(inputAmount.numberString)!!.toBigDecimal()
        val moneyAmount =
          when (inputAmountCurrency) {
            is FiatCurrency ->
              FiatMoney(
                currency = inputAmountCurrency,
                value = inputAmountNumber
              )
            is CryptoCurrency -> BitcoinMoney.btc(inputAmountNumber)
          }

        if (moneyAmount.isWholeNumber && !inputAmount.numberString.contains(decimalSeparator)) {
          // If there's no decimal entry yet, use the "compact" format to show the number
          // i.e. $10 instead of $10.00
          MoneyInputDisplayText(
            displayText =
              when (moneyAmount) {
                is FiatMoney -> moneyFormatterDefinitions.fiatCompact
                is BitcoinMoney -> moneyFormatterDefinitions.bitcoinReducedCode
              }.stringValue(moneyAmount)
          )
        } else {
          // Otherwise, figure out if we need to show "ghosted" values (to indicate fractional
          // digits that haven't been entered yet but are placeholders)
          buildGhostedSubstringForDecimalAmount(
            inputDecimalAmount = inputAmount,
            moneyAmount = moneyAmount
          )
        }
      }
    }
  }

  private fun buildGhostedSubstringForDecimalAmount(
    inputDecimalAmount: DecimalNumber,
    moneyAmount: Money,
  ): MoneyInputDisplayText {
    // Get the full text we will display to the customer
    val displayText =
      when (moneyAmount) {
        is FiatMoney -> moneyFormatterDefinitions.fiatStandard
        is BitcoinMoney -> moneyFormatterDefinitions.bitcoinCode
      }.stringValue(moneyAmount)
    // Remove any non digit or decimal characters from the display text (remove currency symbols)
    val displayTextNumbersAndDecimal =
      displayText.filter {
        it.isDigit() || it == decimalSeparator
      }
    // Figure out which text needs to be ghosted by getting any leftover display text that's not included
    // in the input text (`inputDecimalAmount` - this represents what the customer has explicitly typed in)
    val displayTextGhostedText =
      displayTextNumbersAndDecimal.replace(
        inputDecimalAmount.numberString,
        ""
      )
    // If nothing needs to be ghosted, just return the display text
    if (displayTextGhostedText.isEmpty()) {
      return MoneyInputDisplayText(displayText = displayText)
    }

    // Otherwise, figure out the range of the ghosted substring by finding the last occurrence (since the
    // customer is entering in digits / decimal from left to right)
    val rangeOfGhostedText = displayText.lastOccurrenceOf(displayTextGhostedText)
    return if (rangeOfGhostedText.isEmpty()) {
      MoneyInputDisplayText(displayText = displayText)
    } else {
      MoneyInputDisplayText(
        displayText = displayText,
        displayTextGhostedSubstring =
          Substring(
            string = displayTextGhostedText,
            range = rangeOfGhostedText
          )
      )
    }
  }

  private val decimalSeparator
    get() = decimalSeparatorProvider.decimalSeparator()
}

private fun String.lastOccurrenceOf(string: String): IntRange {
  var searchStartIndex = 0
  var range = EMPTY
  while (this.indexOf(string, startIndex = searchStartIndex) > -1) {
    val substringStartIndex = this.indexOf(string, startIndex = searchStartIndex)
    range = substringStartIndex until substringStartIndex + string.length
    searchStartIndex += 1
  }
  return range
}
