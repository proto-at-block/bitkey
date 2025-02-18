package build.wallet.account.analytics

import app.cash.turbine.test
import build.wallet.account.AccountServiceImpl
import build.wallet.account.AccountStatus
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.LiteAccountMock
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class AccountServiceImplTests : FunSpec({
  val accountDao = AccountDaoFake()
  val service = AccountServiceImpl(
    accountDao = accountDao
  )

  beforeTest {
    accountDao.clear()
  }

  test("receive LiteAccountUpgradingToFullAccount status") {
    accountDao.setActiveAccount(LiteAccountMock)
    accountDao.saveAccountAndBeginOnboarding(FullAccountMock)

    service.accountStatus().test {
      awaitItem().shouldBe(
        Ok(
          AccountStatus.LiteAccountUpgradingToFullAccount(
            liteAccount = LiteAccountMock,
            onboardingAccount = FullAccountMock
          )
        )
      )
    }
  }
})
