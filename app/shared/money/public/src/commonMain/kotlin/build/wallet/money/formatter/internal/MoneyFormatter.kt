package build.wallet.money.formatter.internal

import build.wallet.money.Money

interface MoneyFormatter {
  fun stringValue(amount: Money): String
}
