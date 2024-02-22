package build.wallet.money.input

import build.wallet.amount.Amount
import build.wallet.money.currency.Currency

interface MoneyInputFormatter {
  /**
   * Returns what text to display for the given number input.
   * The [inputAmountCurrency] is used to convert the [inputAmount] into a monetary value.
   */
  fun displayText(
    inputAmount: Amount,
    inputAmountCurrency: Currency,
  ): MoneyInputDisplayText
}
