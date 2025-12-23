package build.wallet.money.formatter

import build.wallet.amount.DoubleFormatter
import build.wallet.money.BitcoinMoney
import build.wallet.money.Money
import com.ionspin.kotlin.bignum.integer.BigInteger

/**
 * A formatter for Bitcoin amounts.
 */
data class BitcoinMoneyFormatter(
  /** Whether the value is formatted as Bitcoin or satoshis */
  private val denominationOption: DenominationOption,
  /** A platform-specific formatter for [Double] values */
  private val doubleFormatter: DoubleFormatter,
) : MoneyFormatter {
  sealed interface DenominationOption {
    /** Format in Bitcoin unit amount */
    data class Bitcoin(val shouldOmitTrailingZeros: Boolean) : DenominationOption

    /**
     * Format in satoshi unit amount.
     * @param useBip177Format If true, uses "₿" prefix (BIP 177), otherwise uses "sats" suffix.
     */
    data class Satoshi(val useBip177Format: Boolean) : DenominationOption
  }

  override fun stringValue(amount: Money): String {
    // This formatter is only used for Bitcoin amounts.
    require(amount is BitcoinMoney)

    // Format the numeric value //
    var formattedString =
      when (denominationOption) {
        is DenominationOption.Bitcoin -> bitcoinStringValue(amount, denominationOption)
        is DenominationOption.Satoshi -> satoshiStringValue(amount, denominationOption)
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

  private fun satoshiStringValue(
    amount: BitcoinMoney,
    option: DenominationOption.Satoshi,
  ): String {
    // Format the numeric value //
    val formattedNumber =
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

    // Add currency indicator based on format option
    return if (option.useBip177Format) {
      // BIP 177 format: "₿100,000"
      "₿$formattedNumber"
    } else {
      // Legacy format: "100,000 sats"
      val config = amount.currency.fractionalUnitConfiguration
      val fractionalAmount =
        amount.currency.fractionalUnitValueFromUnitValue(amount.value).abs()
      val unitLabel =
        if (fractionalAmount == BigInteger.ONE) {
          config.name
        } else {
          config.namePlural ?: config.name
        }
      "$formattedNumber $unitLabel"
    }
  }
}
