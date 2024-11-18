package build.wallet.onboarding

import build.wallet.testing.AppTester
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.shouldBeOk
import build.wallet.testing.shouldBeOkOfType
import io.kotest.core.spec.style.FunSpec

class OnboardAccountFunctionalTests : FunSpec({
  lateinit var app: AppTester

  beforeTest {
    app = launchNewApp()
  }

  test("complete all onboarding states") {
    app.run {
      val cloudBackupStep =
        onboardAccountService.pendingStep().shouldBeOkOfType<OnboardAccountStep.CloudBackup>()

      onboardAccountService.completeStep(cloudBackupStep).shouldBeOk()

      val notificationsStep = onboardAccountService.pendingStep()
        .shouldBeOkOfType<OnboardAccountStep.NotificationPreferences>()

      onboardAccountService.completeStep(notificationsStep).shouldBeOk()

      onboardAccountService.pendingStep().shouldBeOk(null)
    }
  }

  test("skip cloud backup step through debug options") {
    app.run {
      app.debugOptionsService.setSkipCloudBackupOnboarding(true)

      val notificationsStep = onboardAccountService.pendingStep()
        .shouldBeOkOfType<OnboardAccountStep.NotificationPreferences>()

      onboardAccountService.completeStep(notificationsStep).shouldBeOk()

      onboardAccountService.pendingStep().shouldBeOk(null)
    }
  }

  test("skip notifications step through debug options") {
    app.run {
      app.debugOptionsService.setSkipNotificationsOnboarding(true)

      val cloudBackupStep =
        onboardAccountService.pendingStep().shouldBeOkOfType<OnboardAccountStep.CloudBackup>()

      onboardAccountService.completeStep(cloudBackupStep).shouldBeOk()

      onboardAccountService.pendingStep().shouldBeOk(null)
    }
  }
})
