package build.wallet.statemachine.data.mobilepay

import build.wallet.limit.MobilePayBalanceMock
import build.wallet.limit.SpendingLimitMock
import build.wallet.money.FiatMoney
import build.wallet.statemachine.data.mobilepay.MobilePayData.MobilePayDisabledData
import build.wallet.statemachine.data.mobilepay.MobilePayData.MobilePayEnabledData

val MobilePayDisabledDataMock =
  MobilePayDisabledData(
    mostRecentSpendingLimit = SpendingLimitMock,
    enableMobilePay = { _, _, _, _ -> }
  )

val MobilePayEnabledDataMock =
  MobilePayEnabledData(
    activeSpendingLimit = SpendingLimitMock,
    balance = MobilePayBalanceMock,
    remainingFiatSpendingAmount = FiatMoney.usd(100),
    disableMobilePay = {},
    changeSpendingLimit = { _, _, _, _ -> },
    refreshBalance = {}
  )
