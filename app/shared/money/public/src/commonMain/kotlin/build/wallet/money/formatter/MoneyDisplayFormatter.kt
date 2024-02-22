package build.wallet.money.formatter

import build.wallet.money.FiatMoney
import build.wallet.money.Money

interface MoneyDisplayFormatter {
  /**
   * Formats the amount for display purposes.
   */
  fun format(amount: Money): String

  /**
   * Formats the fiat amount as a whole number if there are no cents,
   * for use when setting the spending limit or choosing a purchase amount.
   */
  fun formatCompact(amount: FiatMoney): String
}
