package build.wallet.amount

interface DecimalSeparatorProvider {
  /**
   * Provides the decimal separator from locale settings.
   *
   * A decimal separator is a symbol used to separate the integer part from the
   * fractional part of a number written in decimal form (e.g. "." in 12.45)
   * Example decimal separators include "." and ",".
   */
  fun decimalSeparator(): Char
}
