package build.wallet.money.formatter

import build.wallet.money.Money

interface MoneyFormatter {
  fun stringValue(amount: Money): String
}
