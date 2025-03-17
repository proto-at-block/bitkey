package build.wallet.partnerships

import build.wallet.money.FiatMoney.Companion.usd

val SuggestedPurchaseAmountsFake = SuggestedPurchaseAmounts(
  default = usd(100.00),
  displayOptions = listOf(
    usd(10.00),
    usd(25.00),
    usd(50.00),
    usd(100.00),
    usd(200.00)
  ),
  min = usd(10.00),
  max = usd(500.00)
)
