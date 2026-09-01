package build.wallet.partnerships

import build.wallet.money.BitcoinMoney

data class SellLimits(
  val minAmount: BitcoinMoney,
  val maxAmount: BitcoinMoney,
)
