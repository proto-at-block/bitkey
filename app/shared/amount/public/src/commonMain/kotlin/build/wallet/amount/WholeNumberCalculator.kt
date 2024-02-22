package build.wallet.amount

/**
 * Performs basic math operations on whole numbers.
 */
interface WholeNumberCalculator {
  /**
   * Keypad operation to "add" [digit] to current [amount].
   *
   * For example, adding `1` to `23` results in `231`.
   */
  fun append(
    amount: Amount.WholeNumber,
    digit: Int,
  ): Amount.WholeNumber

  /**
   * Keypad operation to "delete" the last digit from [amount].
   *
   * For example, deleting from `23` results in `2`.
   */
  fun delete(amount: Amount.WholeNumber): Amount.WholeNumber
}
