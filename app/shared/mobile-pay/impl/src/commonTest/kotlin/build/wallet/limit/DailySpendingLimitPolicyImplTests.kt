package build.wallet.limit

import build.wallet.coroutines.turbine.turbines
import build.wallet.money.BitcoinMoney
import build.wallet.money.exchange.CurrencyConverterFake
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class DailySpendingLimitPolicyImplTests : FunSpec({
  val spendingLimitDao = SpendingLimitDaoMock(turbines::create)
  val currencyConverterFake = CurrencyConverterFake(conversionRate = 3.0)
  val policy =
    MobilePaySpendingPolicyImpl()

  afterTest {
    spendingLimitDao.reset()
    currencyConverterFake.conversionRate = 3.0
  }

  test(
    "Given previous transactions and new transaction are below limit, mobile pay is available"
  ) {
    policy.getDailySpendingLimitStatus(
      transactionAmount = BitcoinMoney.sats(140_000),
      latestTransactions = listOf(),
      mobilePayBalance = MobilePayBalanceMock
    ).shouldBe(
      DailySpendingLimitStatus.MobilePayAvailable(MobilePayBalanceMock.limit)
    )
  }

  test("Given that transaction amount is above the limit, hardware is required") {
    policy.getDailySpendingLimitStatus(
      transactionAmount = BitcoinMoney.sats(140_001),
      latestTransactions = listOf(),
      mobilePayBalance = MobilePayBalanceMock
    ).shouldBe(
      DailySpendingLimitStatus.RequiresHardware(MobilePayBalanceMock.limit)
    )
  }
})
