package build.wallet.component.account

import app.cash.turbine.test
import build.wallet.account.AccountStatus.ActiveAccount
import build.wallet.account.AccountStatus.NoAccount
import build.wallet.bitkey.account.SoftwareAccount
import build.wallet.feature.setFlagValue
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.ext.onboardFullAccountWithFakeHardware
import build.wallet.testing.ext.testForLegacyAndPrivateWallet
import build.wallet.testing.shouldBeOk
import build.wallet.testing.shouldBeOkOfType
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class AccountRepositoryImplComponentTests : FunSpec({
  test("no active account or onboarding") {
    val app = launchNewApp()

    app.accountService.accountStatus().test {
      awaitItem().shouldBeOk(NoAccount)
    }

    app.accountService.activeAccount().test {
      awaitItem().shouldBeNull()
    }
  }

  testForLegacyAndPrivateWallet("active Full account is present") { app ->
    val account = app.onboardFullAccountWithFakeHardware()

    app.accountService.accountStatus().test {
      awaitItem().shouldBeOk(ActiveAccount(account))
    }

    app.accountService.activeAccount().test {
      awaitItem().shouldBe(account)
    }
  }

  xtest("active Software account is present") {
    val app = launchNewApp()
    app.softwareWalletIsEnabledFeatureFlag
      .setFlagValue(true)

    val account = app.onboardSoftwareAccountService
      .createAccount()
      .shouldBeOkOfType<SoftwareAccount>()

    app.accountService.accountStatus().test {
      // TODO [W-10001] After we persist software account, we should check for total equality
      awaitItem().shouldBeOkOfType<ActiveAccount>()
    }

    app.accountService.activeAccount().test {
      // TODO [W-10001] After we persist software account, we should check for total equality
      awaitItem().shouldNotBeNull().accountId.shouldBe(account.accountId)
    }
  }
})
