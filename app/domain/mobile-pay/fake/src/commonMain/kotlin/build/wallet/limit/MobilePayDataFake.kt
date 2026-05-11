package build.wallet.limit

import build.wallet.money.BitcoinMoney
import build.wallet.money.FiatMoney

val MobilePayDisabledDataMock =
  MobilePayData.MobilePayDisabledData(
    mostRecentSpendingLimit = SpendingLimitMock
  )

val MobilePayEnabledDataMock =
  MobilePayData.MobilePayEnabledData(
    activeSpendingLimit = SpendingLimitMock,
    remainingBitcoinSpendingAmount = MobilePayBalanceMock.available,
    remainingFiatSpendingAmount = FiatMoney.usd(100),
    spentBitcoinAmount = BitcoinMoney.zero(),
    spentFiatAmount = FiatMoney.usd(0)
  )
