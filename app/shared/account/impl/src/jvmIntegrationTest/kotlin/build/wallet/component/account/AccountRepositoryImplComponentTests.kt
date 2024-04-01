package build.wallet.component.account

import app.cash.turbine.test
import build.wallet.account.AccountStatus
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.ext.onboardFullAccountWithFakeHardware
import build.wallet.testing.shouldBeOk
import io.kotest.core.spec.style.FunSpec

class AccountRepositoryImplComponentTests : FunSpec({

  test("no active account or onboarding") {
    val appTester = launchNewApp()

    appTester.app.appComponent.accountRepository.accountStatus().test {
      awaitItem().shouldBeOk(AccountStatus.NoAccount)
    }
  }

  test("active Full account is present") {
    val appTester = launchNewApp()

    val account = appTester.onboardFullAccountWithFakeHardware()

    appTester.app.appComponent.accountRepository.accountStatus().test {
      awaitItem().shouldBeOk(AccountStatus.ActiveAccount(account))
    }
  }
})
