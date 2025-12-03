package build.wallet.onboarding

import app.cash.turbine.test
import build.wallet.cloud.backup.csek.SealedSsekFake
import build.wallet.coroutines.turbine.awaitUntil
import build.wallet.feature.FeatureFlagValue.BooleanFlag
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.shouldBeOk
import build.wallet.testing.shouldBeOkOfType
import io.kotest.core.spec.style.FunSpec

class OnboardAccountFunctionalTests : FunSpec({

  test("complete all onboarding states") {
    val app = launchNewApp()
    app.encryptedDescriptorBackupsFeatureFlag.setFlagValue(BooleanFlag(true))
    app.onboardingKeyboxSealedSsekDao.set(SealedSsekFake)

    app.run {
      val descriptorBackupStep = onboardAccountService.pendingStep()
        .shouldBeOkOfType<OnboardAccountStep.DescriptorBackup>()

      onboardAccountService.completeStep(descriptorBackupStep).shouldBeOk()

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
    val app = launchNewApp()
    app.run {
      defaultAccountConfigService.setSkipCloudBackupOnboarding(true)
      defaultAccountConfigService.defaultConfig().test {
        awaitUntil { it.skipCloudBackupOnboarding }
      }

      val descriptorBackupStep = onboardAccountService.pendingStep()
        .shouldBeOkOfType<OnboardAccountStep.DescriptorBackup>()

      onboardAccountService.completeStep(descriptorBackupStep).shouldBeOk()

      val notificationsStep = onboardAccountService.pendingStep()
        .shouldBeOkOfType<OnboardAccountStep.NotificationPreferences>()

      onboardAccountService.completeStep(notificationsStep).shouldBeOk()

      onboardAccountService.pendingStep().shouldBeOk(null)
    }
  }

  test("skip notifications step through debug options") {
    val app = launchNewApp()
    app.run {
      defaultAccountConfigService.setSkipNotificationsOnboarding(true)
      defaultAccountConfigService.defaultConfig().test {
        awaitUntil { it.skipNotificationsOnboarding }
      }

      val descriptorBackupStep = onboardAccountService.pendingStep()
        .shouldBeOkOfType<OnboardAccountStep.DescriptorBackup>()

      onboardAccountService.completeStep(descriptorBackupStep).shouldBeOk()

      val cloudBackupStep =
        onboardAccountService.pendingStep().shouldBeOkOfType<OnboardAccountStep.CloudBackup>()

      onboardAccountService.completeStep(cloudBackupStep).shouldBeOk()

      onboardAccountService.pendingStep().shouldBeOk(null)
    }
  }
})
