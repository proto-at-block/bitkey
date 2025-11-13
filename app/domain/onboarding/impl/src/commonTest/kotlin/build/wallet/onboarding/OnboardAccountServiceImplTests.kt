package build.wallet.onboarding

import bitkey.account.AccountConfigServiceFake
import build.wallet.cloud.backup.csek.SealedCsekFake
import build.wallet.cloud.backup.csek.SealedSsekFake
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.flags.EncryptedDescriptorBackupsFeatureFlag
import build.wallet.onboarding.OnboardAccountStep.*
import build.wallet.onboarding.OnboardingKeyboxStepState.Complete
import build.wallet.onboarding.OnboardingKeyboxStepState.Incomplete
import build.wallet.testing.shouldBeOk
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first

class OnboardAccountServiceImplTests : FunSpec({
  val accountConfigService = AccountConfigServiceFake()
  val onboardingKeyboxStepStateDao = OnboardingKeyboxStepStateDaoFake()
  val onboardingKeyboxSealedCsekDao = OnboardingKeyboxSealedCsekDaoMock()
  val onboardingKeyboxSealedSsekDao = OnboardingKeyboxSealedSsekDaoFake()
  val onboardingCompletionService = OnboardingCompletionServiceFake()
  val featureFlagDao = FeatureFlagDaoFake()
  val encryptedDescriptorBackupsFeatureFlag = EncryptedDescriptorBackupsFeatureFlag(featureFlagDao)

  val service = OnboardAccountServiceImpl(
    accountConfigService = accountConfigService,
    onboardingKeyboxStepStateDao = onboardingKeyboxStepStateDao,
    onboardingKeyboxSealedCsekDao = onboardingKeyboxSealedCsekDao,
    onboardingKeyboxSealedSsekDao = onboardingKeyboxSealedSsekDao,
    onboardingCompletionService = onboardingCompletionService,
    encryptedDescriptorBackupsFeatureFlag = encryptedDescriptorBackupsFeatureFlag
  )

  beforeTest {
    accountConfigService.reset()
    onboardingKeyboxStepStateDao.clear()
    onboardingKeyboxSealedCsekDao.reset()
    onboardingKeyboxSealedSsekDao.reset()
    onboardingCompletionService.reset()
    featureFlagDao.reset()
  }

  test("marks descriptor backup step as incomplete") {
    // Given a completed descriptor backup step
    onboardingKeyboxStepStateDao
      .setStateForStep(OnboardingKeyboxStep.DescriptorBackup, Complete)
      .shouldBeOk()

    // When marking it incomplete
    val result = service.markStepIncomplete(DescriptorBackup(sealedSsek = SealedSsekFake))

    // Then the result is successful
    result.shouldBeOk()

    // And the step state is now incomplete
    val state = onboardingKeyboxStepStateDao
      .stateForStep(OnboardingKeyboxStep.DescriptorBackup)
      .first()
    state.shouldBe(Incomplete)
  }

  test("marks cloud backup step as incomplete") {
    // Given a completed cloud backup step
    onboardingKeyboxStepStateDao
      .setStateForStep(OnboardingKeyboxStep.CloudBackup, Complete)
      .shouldBeOk()

    // When marking it incomplete
    val result = service.markStepIncomplete(CloudBackup(sealedCsek = SealedCsekFake))

    // Then the result is successful
    result.shouldBeOk()

    // And the step state is now incomplete
    val state = onboardingKeyboxStepStateDao
      .stateForStep(OnboardingKeyboxStep.CloudBackup)
      .first()
    state.shouldBe(Incomplete)
  }

  test("marks notification preferences step as incomplete") {
    // Given a completed notification preferences step
    onboardingKeyboxStepStateDao
      .setStateForStep(OnboardingKeyboxStep.NotificationPreferences, Complete)
      .shouldBeOk()

    // When marking it incomplete
    val result = service.markStepIncomplete(NotificationPreferences)

    // Then the result is successful
    result.shouldBeOk()

    // And the step state is now incomplete
    val state = onboardingKeyboxStepStateDao
      .stateForStep(OnboardingKeyboxStep.NotificationPreferences)
      .first()
    state.shouldBe(Incomplete)
  }
})
