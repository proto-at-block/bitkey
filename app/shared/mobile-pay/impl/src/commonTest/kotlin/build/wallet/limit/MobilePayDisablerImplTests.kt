package build.wallet.limit

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.F8eEnvironment.Production
import build.wallet.f8e.mobilepay.MobilePaySpendingLimitServiceMock
import build.wallet.testing.shouldBeOk
import io.kotest.core.spec.style.FunSpec

class MobilePayDisablerImplTests : FunSpec({
  val spendingLimitDao = SpendingLimitDaoMock(turbines::create)
  val spendingLimitService = MobilePaySpendingLimitServiceMock()
  val disabler = MobilePayDisablerImpl(spendingLimitDao, spendingLimitService)

  test("disable limit") {
    disabler.disable(
      f8eEnvironment = Production,
      fullAccountId = FullAccountId("foo")
    ).shouldBeOk()

    spendingLimitDao.clearActiveLimitCalls.awaitItem()
  }
})
