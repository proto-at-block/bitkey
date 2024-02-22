package build.wallet.money.exchange

import build.wallet.money.Money

/**
 * Represents a converted money amount in a given currency.
 *
 * We use this data structure to return call-sites with not just the target value, but also the
 * exchange rates used to convert the value.
 */
data class ConvertedMoney(
  val money: Money,
  val rates: List<ExchangeRate>,
)
