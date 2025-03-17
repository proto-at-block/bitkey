package build.wallet.amount

/**
 * Performs basic math operations on decimal numbers.
 */
interface DecimalNumberCalculator {
  /**
   * Keypad operation to "add" [digit] to current [amount].
   *
   * For example, adding `2` to `23.1` results in `23.12`.
   */
  fun append(
    amount: Amount.DecimalNumber,
    digit: Int,
  ): Amount.DecimalNumber

  /**
   * Keypad operation to "delete" the last digit from [amount].
   *
   * For example, deleting from `23.1` results in `23`.
   */
  fun delete(amount: Amount.DecimalNumber): Amount.DecimalNumber

  /**
   * Keypad operation to add a decimal to the [amount].
   *
   * For example, adding a decimal to `23` results in `23.`.
   * This has no effect if the amount already contains a decimal.
   */
  fun decimal(amount: Amount.DecimalNumber): Amount.DecimalNumber
}
