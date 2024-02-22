package build.wallet.amount

import build.wallet.amount.Amount.Companion.MAXIMUM

class WholeNumberCalculatorImpl(
  /** A maximum value this calculator will bound all [append] operations to. */
  private val maximumValue: Long = MAXIMUM,
) : WholeNumberCalculator {
  override fun append(
    amount: Amount.WholeNumber,
    digit: Int,
  ): Amount.WholeNumber {
    require(digit in (0..9))
    val result = amount.number * 10 + digit
    // Don't allow the new result to go above the set maximum
    return Amount.WholeNumber(if (result > maximumValue) maximumValue else result)
  }

  override fun delete(amount: Amount.WholeNumber): Amount.WholeNumber {
    val result = amount.number / 10
    return Amount.WholeNumber(result)
  }
}
