package build.wallet.amount

import build.wallet.amount.Amount.Companion.MAXIMUM
import build.wallet.amount.Amount.DecimalNumber
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.platform.settings.LocaleProvider

@BitkeyInject(AppScope::class)
class DecimalNumberCalculatorImpl(
  private val decimalNumberCreator: DecimalNumberCreator,
  private val localeProvider: LocaleProvider,
  private val doubleFormatter: DoubleFormatter,
) : DecimalNumberCalculator {
  /** A maximum value this calculator will bound all [append] operations to. */
  private val maximumValue: Double = MAXIMUM.toDouble()

  override fun append(
    amount: DecimalNumber,
    digit: Int,
  ): DecimalNumber {
    require(digit in (0..9))
    return if (amount.hasDecimal()) {
      var resultString = amount.numberString
      // Only add another digit if there are less than the maximum fractional digits
      // Otherwise, it is ignored
      val (_, decimals) = amount.numberString.split(
        localeProvider.currentLocale().decimalSeparator
      )
      if (decimals.isNotEmpty()) {
        if (decimals.length < amount.maximumFractionDigits) {
          resultString += digit.toString()
        }
      } else {
        resultString += digit.toString()
      }

      // Don't allow the new result to go above the set maximum
      val result = doubleFormatter.parse(resultString)!!
      if (result > maximumValue) {
        decimalNumberCreator.create(
          number = maximumValue,
          maximumFractionDigits = amount.maximumFractionDigits
        )
      } else {
        decimalNumberCreator.create(
          numberString = resultString,
          maximumFractionDigits = amount.maximumFractionDigits
        )
      }
    } else {
      val result = doubleFormatter.parse(amount.numberString)!! * 10 + digit
      decimalNumberCreator.create(
        // Don't allow the new result to go above the absolute maximum
        numberString = (if (result > maximumValue) maximumValue else result).toLong().toString(),
        maximumFractionDigits = amount.maximumFractionDigits
      )
    }
  }

  override fun delete(amount: DecimalNumber): DecimalNumber {
    return if (amount.hasDecimal()) {
      val resultString = amount.numberString.dropLast(1)
      decimalNumberCreator.create(
        numberString = resultString,
        maximumFractionDigits = amount.maximumFractionDigits
      )
    } else {
      val resultDouble = doubleFormatter.parse(amount.numberString)!! / 10
      val remainder = resultDouble % 1.0
      val result = resultDouble - remainder
      decimalNumberCreator.create(
        numberString = result.toLong().toString(),
        maximumFractionDigits = amount.maximumFractionDigits
      )
    }
  }

  override fun decimal(amount: DecimalNumber): DecimalNumber {
    return if (amount.hasDecimal()) {
      // Already has a decimal
      amount
    } else {
      decimalNumberCreator.create(
        numberString = amount.numberString + localeProvider.currentLocale().decimalSeparator,
        maximumFractionDigits = amount.maximumFractionDigits
      )
    }
  }

  private fun DecimalNumber.hasDecimal() = numberString.contains(decimalSeparator)
}
