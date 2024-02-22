package build.wallet.limit

import build.wallet.bitcoin.transactions.BitcoinTransaction
import build.wallet.money.BitcoinMoney

class MobilePaySpendingPolicyMock : MobilePaySpendingPolicy {
  override fun getDailySpendingLimitStatus(
    transactionAmount: BitcoinMoney,
    latestTransactions: List<BitcoinTransaction>,
    mobilePayBalance: MobilePayBalance?,
  ): DailySpendingLimitStatus {
    return DailySpendingLimitStatus.RequiresHardware(null)
  }
}
