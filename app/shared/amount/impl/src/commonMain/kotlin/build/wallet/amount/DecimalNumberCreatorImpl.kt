package build.wallet.amount

import build.wallet.amount.Amount.DecimalNumber
import build.wallet.platform.settings.LocaleProvider

class DecimalNumberCreatorImpl(
  private val localeProvider: LocaleProvider,
  private val doubleFormatter: DoubleFormatter,
) : DecimalNumberCreator {
  override fun create(
    number: Double,
    maximumFractionDigits: Int,
  ): DecimalNumber =
    DecimalNumber(
      /**
       * This allows our input to start with whole numbers from zero, instead of like $0.0
       * so the first number a customer enters is unexpectedly a fractional value.
       *
       * Similarly, for something like $10.00, we'll start at the decimal for input instead of at the end
       */
      numberString =
        when (number.isWholeNumber()) {
          true -> number.toLong().toString()
          false ->
            doubleFormatter.format(
              double = number,
              minimumFractionDigits = 0,
              maximumFractionDigits = maximumFractionDigits,
              isGroupingUsed = false
            )
        },
      maximumFractionDigits = maximumFractionDigits,
      decimalSeparator = localeProvider.currentLocale().decimalSeparator
    )

  override fun create(
    numberString: String,
    maximumFractionDigits: Int,
  ): DecimalNumber {
    val decimalSeparator = localeProvider.currentLocale().decimalSeparator
    return DecimalNumber(
      numberString = numberString.truncatedToMaximumFractionDigits(
        decimalSeparator = decimalSeparator,
        maximumFractionDigits = maximumFractionDigits
      ),
      maximumFractionDigits = maximumFractionDigits,
      decimalSeparator = decimalSeparator
    )
  }

  private fun String.truncatedToMaximumFractionDigits(
    decimalSeparator: Char,
    maximumFractionDigits: Int,
  ): String {
    val components = this.split(decimalSeparator)
    return if (components.count() > 1) {
      val wholeDigits = components[0]
      val fractionalDigits = components[1]
      wholeDigits + decimalSeparator + fractionalDigits.take(maximumFractionDigits)
    } else {
      this
    }
  }
}

private fun Double.isWholeNumber(): Boolean = this.toInt().toDouble() == this
