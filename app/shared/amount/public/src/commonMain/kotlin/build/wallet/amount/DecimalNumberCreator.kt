package build.wallet.amount

import build.wallet.amount.Amount.DecimalNumber

interface DecimalNumberCreator {
  /**
   * Creates a new [Amount.DecimalNumber] from the given number with the given expected
   * number of fractional digits.
   *
   * This method should be used when setting initial or explicit values.
   * Amounts being calculated on by the DecimalNumberCalculator should use the number string constructor.
   */
  fun create(
    number: Double,
    maximumFractionDigits: Int,
  ): DecimalNumber

  /**
   * Internal only. Outside consumers should only use the above method.
   * Creates a new [Amount.DecimalNumber] from the given number string with the given expected
   * number of fractional digits.
   *
   * This method should be used when performing calculations in [DecimalNumberCalculator].
   */
  fun create(
    numberString: String,
    maximumFractionDigits: Int,
  ): DecimalNumber
}
