package build.wallet.money.formatter

import build.wallet.amount.DoubleFormatter
import build.wallet.money.BitcoinMoney
import build.wallet.money.Money
import com.ionspin.kotlin.bignum.integer.toBigInteger

/**
 * A formatter for Bitcoin amounts.
 */
data class BitcoinMoneyFormatter(
  /** Whether the value is formatted as Bitcoin or Satoshis */
  private val denominationOption: DenominationOption,
  /** A platform-specific formatter for [Double] values */
  private val doubleFormatter: DoubleFormatter,
) : MoneyFormatter {
  sealed interface DenominationOption {
    /** Format in Bitcoin unit amount */
    data class Bitcoin(val shouldOmitTrailingZeros: Boolean) : DenominationOption

    /** Format in Satoshi unit amount */
    data object Satoshi : DenominationOption
  }

  override fun stringValue(amount: Money): String {
    // This formatter is only used for Bitcoin amounts.
    require(amount is BitcoinMoney)

    // Format the numeric value //
    var formattedString =
      when (denominationOption) {
        is DenominationOption.Bitcoin -> bitcoinStringValue(amount, denominationOption)
        is DenominationOption.Satoshi -> satoshiStringValue(amount)
      }

    // Add the sign for negative numbers //
    if (amount.isNegative) {
      formattedString = "- $formattedString"
    }

    return formattedString
  }

  private fun bitcoinStringValue(
    amount: BitcoinMoney,
    option: DenominationOption.Bitcoin,
  ): String {
    var formattedString = ""

    // Format the numeric value //
    val fractionDigits =
      if (amount.isWholeNumber && option.shouldOmitTrailingZeros) {
        0
      } else {
        amount.currency.fractionalDigits
      }

    // Remove trailing zeros if specified
    val minimumFractionDigits = if (option.shouldOmitTrailingZeros) 0 else fractionDigits

    formattedString +=
      doubleFormatter.format(
        // Use the absolute value and add in signs later.
        double = amount.value.abs().doubleValue(exactRequired = false),
        minimumFractionDigits = minimumFractionDigits,
        maximumFractionDigits = fractionDigits,
        isGroupingUsed = true
      )

    // Add the BTC code //
    formattedString = "$formattedString ${amount.currency.textCode.code}"

    return formattedString
  }

  private fun satoshiStringValue(amount: BitcoinMoney): String {
    var formattedString = ""

    // Format the numeric value //
    formattedString +=
      doubleFormatter.format(
        // Use the absolute value and add in signs later.
        double =
          amount.currency.fractionalUnitValueFromUnitValue(amount.value)
            .abs()
            .doubleValue(exactRequired = false),
        minimumFractionDigits = 0,
        maximumFractionDigits = 0,
        isGroupingUsed = true
      )

    // Add the fractional unit name //
    val config = amount.currency.fractionalUnitConfiguration
    formattedString =
      if (amount.currency.fractionalUnitValueFromUnitValue(amount.value) == 1.toBigInteger()) {
        "$formattedString ${config.name}"
      } else {
        "$formattedString ${config.namePlural}"
      }

    return formattedString
  }
}
