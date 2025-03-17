package build.wallet.money.formatter

import build.wallet.money.BitcoinMoney
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

/**
 * Determines the priority of money text to be displayed to the user, returned as an [AmountDisplayText].
 *
 * In practice, we prefer to show the fiat primary text and btc secondary text and will do so if exchange
 * rates are available.
 *
 * Otherwise, the primary text is btc and the secondary text is null.
 *
 * @param withPendingFormat: whether to indicate a fiat amount is tentative, by prefixing it with "~", e.g. "~$2.75".
 */
fun MoneyDisplayFormatter.amountDisplayText(
  bitcoinAmount: BitcoinMoney,
  fiatAmount: FiatMoney?,
  withPendingFormat: Boolean = false,
) = when (fiatAmount) {
  null -> AmountDisplayText(
    primaryAmountText = format(bitcoinAmount),
    secondaryAmountText = null
  )
  else -> {
    val formattedFiatAmount = format(fiatAmount)
    AmountDisplayText(
      primaryAmountText = if (withPendingFormat) {
        "~$formattedFiatAmount"
      } else {
        formattedFiatAmount
      },
      secondaryAmountText = format(bitcoinAmount)
    )
  }
}
