package build.wallet.component.account

import app.cash.turbine.test
import build.wallet.account.AccountStatus.*
import build.wallet.bitkey.account.OnboardingSoftwareAccount
import build.wallet.feature.setFlagValue
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.ext.onboardFullAccountWithFakeHardware
import build.wallet.testing.shouldBeOk
import build.wallet.testing.shouldBeOkOfType
import io.kotest.core.spec.style.FunSpec

class AccountRepositoryImplComponentTests : FunSpec({
  test("no active account or onboarding") {
    val appTester = launchNewApp()

    appTester.app.appComponent.accountRepository.accountStatus().test {
      awaitItem().shouldBeOk(NoAccount)
    }
  }

  test("active Full account is present") {
    val appTester = launchNewApp()

    val account = appTester.onboardFullAccountWithFakeHardware()

    appTester.app.appComponent.accountRepository.accountStatus().test {
      awaitItem().shouldBeOk(ActiveAccount(account))
    }
  }

  test("onboarding Software account is present") {
    val appTester = launchNewApp()
    appTester.app.appComponent.softwareWalletIsEnabledFeatureFlag
      .setFlagValue(true)

    val account = appTester.app.createSoftwareWalletWorkflow
      .createAccount()
      .shouldBeOkOfType<OnboardingSoftwareAccount>()

    appTester.app.appComponent.accountRepository.accountStatus().test {
      awaitItem().shouldBeOk(OnboardingAccount(account))
    }
  }
})
