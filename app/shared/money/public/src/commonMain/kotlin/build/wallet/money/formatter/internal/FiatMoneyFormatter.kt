package build.wallet.money.formatter.internal

import build.wallet.amount.DoubleFormatter
import build.wallet.money.Money
import build.wallet.money.formatter.internal.FiatMoneyFormatter.CurrencyRepresentationOption.CODE
import build.wallet.money.formatter.internal.FiatMoneyFormatter.CurrencyRepresentationOption.SYMBOL
import build.wallet.money.formatter.internal.FiatMoneyFormatter.SignOption.SIGNED

/**
 * A formatter for fiat amounts.
 */
data class FiatMoneyFormatter(
  /** Display option for the currency symbol (see enum cases) */
  private val currencyRepresentationOption: CurrencyRepresentationOption,
  /** Display option for the sign (see enum cases) */
  private val signOption: SignOption,
  /** Display whole amounts without a decimal and fractional unit (i.e. cents). */
  private val omitsFractionalUnitIfPossible: Boolean,
  /** A platform-specific formatter for [Double] values */
  private val doubleFormatter: DoubleFormatter,
) : MoneyFormatter {
  enum class CurrencyRepresentationOption {
    /**
     * The currency's symbol, e.g. $ or ¥. Falls back to [CODE] representation if the
     * currency has no symbol.
     */
    SYMBOL,

    /** The currency's ISO code, e.g. USD or JPY */
    CODE,
  }

  enum class SignOption {
    /** Show a minus sign for negative numbers only */
    STANDARD,

    /** Show a minus sign for negative numbers and a plus sign for positive numbers */
    SIGNED,
  }

  override fun stringValue(amount: Money): String {
    var formattedString = ""

    // First, format the numeric value. //

    val showFractionalAmount =
      if (amount.isWholeNumber) {
        // We can omit the fractional amount.
        // Only omit it if specified
        !omitsFractionalUnitIfPossible
      } else {
        // We can't omit the fractional amount.
        true
      }

    val fractionDigits =
      when {
        showFractionalAmount -> amount.currency.fractionalDigits
        else -> 0
      }

    formattedString +=
      doubleFormatter.format(
        // Use the absolute value and add in signs later.
        double = amount.value.abs().doubleValue(exactRequired = false),
        minimumFractionDigits = fractionDigits,
        maximumFractionDigits = fractionDigits,
        isGroupingUsed = true
      )

    // Next, add the currency symbol or code as appropriate. //

    formattedString =
      when (currencyRepresentationOption) {
        SYMBOL ->
          when (amount.currency.unitSymbol) {
            // Fall back to formatting with the text code if there is no symbol for the currency.
            null -> "$formattedString ${amount.currency.textCode.code}"
            else -> "${amount.currency.unitSymbol}$formattedString"
          }
        CODE -> "$formattedString ${amount.currency.textCode.code}"
      }

    // Finally, show the sign. //

    if (amount.isNegative) {
      formattedString = "- $formattedString"
    } else if (amount.isPositive && signOption == SIGNED) {
      formattedString = "+ $formattedString"
    }

    return formattedString
  }
}
