package build.wallet.onboarding

import app.cash.turbine.test
import build.wallet.bitkey.account.OnboardingSoftwareAccount
import build.wallet.feature.setFlagValue
import build.wallet.testing.AppTester
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.ext.onboardFullAccountWithFakeHardware
import build.wallet.testing.shouldBeErrOfType
import build.wallet.testing.shouldBeOkOfType
import io.kotest.core.spec.style.FunSpec

class CreateSoftwareWalletWorkflowComponentTests : FunSpec({

  lateinit var appTester: AppTester
  lateinit var workflow: CreateSoftwareWalletWorkflow

  beforeTest {
    appTester = launchNewApp()
    workflow = appTester.app.createSoftwareWalletWorkflow
  }

  context("happy path") {
    test("successfully create software account") {
      appTester.app.appComponent.softwareWalletIsEnabledFeatureFlag.setFlagValue(true)

      workflow.createAccount().shouldBeOkOfType<OnboardingSoftwareAccount>()
    }
  }

  context("unhappy path") {

    test("workflow fails when an account already exists") {
      appTester.onboardFullAccountWithFakeHardware()

      workflow.createAccount().shouldBeErrOfType<Error>()
    }

    test("workflow fails when feature flag is disabled") {
      appTester.app.appComponent.softwareWalletIsEnabledFeatureFlag.setFlagValue(false)

      workflow.createAccount().shouldBeErrOfType<Error>()
    }
  }
})
