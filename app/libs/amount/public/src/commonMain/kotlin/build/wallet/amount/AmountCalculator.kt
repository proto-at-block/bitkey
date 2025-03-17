package build.wallet.amount

/**
 * Allows to perform basic math operations on [Amount]s.
 */
interface AmountCalculator {
  /**
   * Allows to delete the last digit of an amount.
   */
  fun delete(amount: Amount): Amount

  /**
   * Allows to append a digit to an amount.
   */
  fun add(
    amount: Amount,
    digit: Int,
  ): Amount

  /**
   * Allows to append a decimal (either '.' or ',' depending on locale) to an amount.
   */
  fun decimal(amount: Amount): Amount
}
