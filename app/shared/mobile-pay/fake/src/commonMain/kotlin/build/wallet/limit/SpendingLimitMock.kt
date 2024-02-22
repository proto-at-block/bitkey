package build.wallet.limit

import build.wallet.money.FiatMoney
import kotlinx.datetime.TimeZone

val SpendingLimitMock = SpendingLimitMock(FiatMoney.usd(100))

fun SpendingLimitMock(amount: FiatMoney) =
  SpendingLimit(
    active = true,
    amount = amount,
    timezone = TimeZone.UTC
  )
