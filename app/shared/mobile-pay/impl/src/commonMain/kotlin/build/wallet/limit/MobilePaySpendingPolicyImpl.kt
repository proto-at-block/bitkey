package build.wallet.limit

import build.wallet.bitcoin.transactions.BitcoinTransaction
import build.wallet.limit.DailySpendingLimitStatus.MobilePayAvailable
import build.wallet.limit.DailySpendingLimitStatus.RequiresHardware
import build.wallet.money.BitcoinMoney

class MobilePaySpendingPolicyImpl : MobilePaySpendingPolicy {
  override fun getDailySpendingLimitStatus(
    transactionAmount: BitcoinMoney,
    latestTransactions: List<BitcoinTransaction>,
    mobilePayBalance: MobilePayBalance?,
  ): DailySpendingLimitStatus {
    // If F8e did not return a current balance, we fall back to requiring hardware
    return when (mobilePayBalance) {
      null -> RequiresHardware(spendingLimit = null)
      else -> {
        if (transactionAmount > mobilePayBalance.available) {
          RequiresHardware(spendingLimit = mobilePayBalance.limit)
        } else {
          MobilePayAvailable(spendingLimit = mobilePayBalance.limit)
        }
      }
    }
  }
}
