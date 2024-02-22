package build.wallet.amount

sealed interface Amount {
  /** The maximum amount supported. This is to avoid overflow issues */
  companion object {
    const val MAXIMUM: Long = 999_999_999
  }

  /**
   * Represents a keypad amount which is a whole number with no decimals.
   */
  data class WholeNumber(
    val number: Long,
  ) : Amount

  /**
   * Represents a keypad amount which is a decimal number.
   *
   * We use a string to track input to support values like
   * - 12
   * - 12.
   * - 12.0
   * - 12.00
   * which should all be unique
   *
   * @property maximumFractionDigits: The maximum number of fractional digits this decimal amount supports.
   * Used in calculations to ignore additional input beyond the maximum, and in initialization to truncate
   * any extra digits from the number string.
   */
  data class DecimalNumber(
    val numberString: String,
    val maximumFractionDigits: Int,
    val decimalSeparator: Char,
  ) : Amount {
    init {
      require(numberString.isNotBlank())
      require(
        numberString.count { it == decimalSeparator } <= 1
      ) // Should not have more than 1 decimal
      require(
        numberString.all {
          it.isDigit() || it == decimalSeparator
        }
      ) // Only digits and decimals are allowed
      require(
        maximumFractionDigits > 0
      ) // Currencies with no fractional units should not be using this input amount type
    }
  }
}
