package build.wallet.limit

import build.wallet.limit.MobilePayData.MobilePayDisabledData
import build.wallet.limit.MobilePayData.MobilePayEnabledData
import build.wallet.money.FiatMoney

val MobilePayDisabledDataMock =
  MobilePayDisabledData(
    mostRecentSpendingLimit = SpendingLimitMock
  )

val MobilePayEnabledDataMock =
  MobilePayEnabledData(
    activeSpendingLimit = SpendingLimitMock,
    remainingBitcoinSpendingAmount = MobilePayBalanceMock.available,
    remainingFiatSpendingAmount = FiatMoney.usd(100)
  )
