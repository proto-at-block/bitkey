package build.wallet.component.account

import app.cash.turbine.test
import build.wallet.account.AccountStatus.ActiveAccount
import build.wallet.account.AccountStatus.NoAccount
import build.wallet.bitkey.account.SoftwareAccount
import build.wallet.feature.setFlagValue
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.ext.onboardFullAccountWithFakeHardware
import build.wallet.testing.shouldBeOk
import build.wallet.testing.shouldBeOkOfType
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class AccountRepositoryImplComponentTests : FunSpec({
  test("no active account or onboarding") {
    val appTester = launchNewApp()

    appTester.app.appComponent.accountService.accountStatus().test {
      awaitItem().shouldBeOk(NoAccount)
    }

    appTester.app.appComponent.accountService.activeAccount().test {
      awaitItem().shouldBeNull()
    }
  }

  test("active Full account is present") {
    val appTester = launchNewApp()

    val account = appTester.onboardFullAccountWithFakeHardware()

    appTester.app.appComponent.accountService.accountStatus().test {
      awaitItem().shouldBeOk(ActiveAccount(account))
    }

    appTester.app.appComponent.accountService.activeAccount().test {
      awaitItem().shouldBe(account)
    }
  }

  xtest("active Software account is present") {
    val appTester = launchNewApp()
    appTester.app.appComponent.softwareWalletIsEnabledFeatureFlag
      .setFlagValue(true)

    val account = appTester.app.createSoftwareWalletService
      .createAccount()
      .shouldBeOkOfType<SoftwareAccount>()

    appTester.app.appComponent.accountService.accountStatus().test {
      awaitItem().shouldBeOk(ActiveAccount(account))
    }

    appTester.app.appComponent.accountService.activeAccount().test {
      awaitItem().shouldBe(account)
    }
  }
})
