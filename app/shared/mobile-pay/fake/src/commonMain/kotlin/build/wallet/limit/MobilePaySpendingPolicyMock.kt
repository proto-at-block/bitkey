package build.wallet.limit

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign
import build.wallet.money.BitcoinMoney

class MobilePaySpendingPolicyMock(
  turbine: (String) -> Turbine<Any>,
) : MobilePaySpendingPolicy {
  val getDailySpendingLimitStatusCalls = turbine("get daily spending limit calls")

  override fun getDailySpendingLimitStatus(
    transactionAmount: BitcoinMoney,
    mobilePayBalance: MobilePayBalance?,
  ): DailySpendingLimitStatus {
    getDailySpendingLimitStatusCalls += transactionAmount
    return DailySpendingLimitStatus.RequiresHardware(null)
  }
}
