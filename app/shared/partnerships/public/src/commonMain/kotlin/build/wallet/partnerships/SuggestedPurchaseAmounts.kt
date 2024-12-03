package build.wallet.partnerships

import build.wallet.money.FiatMoney

/**
 * Suggested fiat amounts when customer purchases bitcoin through a partner.
 *
 * @param [default] default amount that should be picked/highlighted in UI initially when displaying
 * suggested amounts to customer. [default] is part of all available [displayOptions].
 * @param [displayOptions] all amount options that should be display to customer.
 */
data class SuggestedPurchaseAmounts(
  val default: FiatMoney,
  val displayOptions: List<FiatMoney>,
  val min: FiatMoney,
  val max: FiatMoney,
)
