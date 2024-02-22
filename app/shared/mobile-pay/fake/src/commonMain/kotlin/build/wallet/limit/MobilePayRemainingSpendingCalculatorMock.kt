package build.wallet.limit

import build.wallet.bitcoin.transactions.BitcoinTransaction
import build.wallet.money.BitcoinMoney
import build.wallet.money.Money
import kotlinx.datetime.TimeZone

class MobilePayRemainingSpendingCalculatorMock : MobilePayRemainingSpendingCalculator {
  lateinit var remainingSpendingAmountResult: Money

  override fun remainingSpendingAmount(
    allTransactions: List<BitcoinTransaction>,
    limitAmountInBtc: BitcoinMoney,
    limitTimeZone: TimeZone,
  ): Money = remainingSpendingAmountResult
}
