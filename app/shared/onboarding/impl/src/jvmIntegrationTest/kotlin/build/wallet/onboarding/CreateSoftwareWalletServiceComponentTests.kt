package build.wallet.onboarding

import build.wallet.bitkey.account.SoftwareAccount
import build.wallet.feature.setFlagValue
import build.wallet.testing.AppTester
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.ext.onboardFullAccountWithFakeHardware
import build.wallet.testing.shouldBeErrOfType
import build.wallet.testing.shouldBeOkOfType
import io.kotest.core.spec.style.FunSpec

class CreateSoftwareWalletServiceComponentTests : FunSpec({

  lateinit var appTester: AppTester
  lateinit var service: CreateSoftwareWalletService

  beforeTest {
    appTester = launchNewApp()
    service = appTester.app.createSoftwareWalletService
  }

  context("happy path") {
    xtest("successfully create software account") {
      appTester.app.appComponent.softwareWalletIsEnabledFeatureFlag.setFlagValue(true)

      service.createAccount().shouldBeOkOfType<SoftwareAccount>()
    }
  }

  context("unhappy path") {

    test("workflow fails when an account already exists") {
      appTester.onboardFullAccountWithFakeHardware()

      service.createAccount().shouldBeErrOfType<Error>()
    }

    test("workflow fails when feature flag is disabled") {
      appTester.app.appComponent.softwareWalletIsEnabledFeatureFlag.setFlagValue(false)

      service.createAccount().shouldBeErrOfType<Error>()
    }
  }
})