package build.wallet.limit

import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.mobilepay.MobilePaySpendingLimitServiceMock
import build.wallet.testing.shouldBeOk
import io.kotest.core.spec.style.FunSpec

class MobilePayDisablerImplTests : FunSpec({
  val spendingLimitDao = SpendingLimitDaoMock(turbines::create)
  val spendingLimitService = MobilePaySpendingLimitServiceMock()
  val disabler = MobilePayDisablerImpl(spendingLimitDao, spendingLimitService)

  test("disable limit") {
    disabler.disable(account = FullAccountMock).shouldBeOk()

    spendingLimitDao.clearActiveLimitCalls.awaitItem()
  }
})
